#import <AppKit/AppKit.h>
#import <AVFoundation/AVFoundation.h>
#import <Foundation/Foundation.h>
#import <MediaPlayer/MediaPlayer.h>

#include "ffmpeg_decoder.h"

// Forward declarations of Go callback functions
extern void goHandleTrackEnd();
extern void goHandleRemotePlay();
extern void goHandleRemotePause();
extern void goHandleRemoteNext();
extern void goHandleRemotePrevious();

// Returns YES for formats AVFoundation can decode natively.
static BOOL isAVFoundationNative(NSString *ext) {
    static NSSet *s;
    static dispatch_once_t t;
    dispatch_once(&t, ^{
        s = [NSSet setWithObjects:@"mp3", @"m4a", @"aac", @"wav", @"wave",
                                  @"aiff", @"aif", @"flac", @"caf", nil];
    });
    return [s containsObject:ext.lowercaseString];
}

@interface AirmedyPlayer : NSObject
// AVAudioEngine pipeline
@property (strong, nonatomic) AVAudioEngine       *engine;
@property (strong, nonatomic) AVAudioPlayerNode   *playerNode;
@property (strong, nonatomic) AVAudioUnitEQ       *equalizer;
@property (strong, nonatomic) AVAudioMixerNode    *mixerNode;
// Native AVFoundation path
@property (strong, nonatomic) AVAudioFile         *audioFile;
// FFmpeg decode path
@property (nonatomic) float       *ffmpegPCMBuffer;
@property (nonatomic) ma_uint64    ffmpegFrameCount;
@property (nonatomic) ma_uint32    ffmpegChannels;
@property (nonatomic) ma_uint32    ffmpegSampleRate;
@property (nonatomic) BOOL         usingFFmpegDecoder;
// Playback state
@property (assign, nonatomic) BOOL  isPlaying;
@property (assign, nonatomic) BOOL  eqEnabled;
@property (assign, nonatomic) float volume;
// Position tracking
@property (assign, nonatomic) AVAudioFramePosition scheduledStartFrame;
@property (assign, nonatomic) NSTimeInterval       pausePosition;
// Generation counter: incremented on each load/seek to invalidate stale completion handlers
@property (assign, nonatomic) NSUInteger           scheduleGeneration;
@end

@implementation AirmedyPlayer

- (instancetype)init {
    self = [super init];
    if (self) {
        _volume = 1.0f;
        _eqEnabled = YES;
        _isPlaying = NO;
        _pausePosition = 0.0;
        _scheduledStartFrame = 0;
        _ffmpegPCMBuffer = NULL;
        _ffmpegFrameCount = 0;
        _ffmpegChannels = 0;
        _ffmpegSampleRate = 0;
        _usingFFmpegDecoder = NO;
        [self setupEngine];
    }
    return self;
}

- (void)dealloc {
    if (_ffmpegPCMBuffer) {
        free(_ffmpegPCMBuffer);
        _ffmpegPCMBuffer = NULL;
    }
}

- (void)setupEngine {
    self.engine     = [[AVAudioEngine alloc] init];
    self.playerNode = [[AVAudioPlayerNode alloc] init];
    // 10-band EQ matching ISO standard frequencies
    self.equalizer  = [[AVAudioUnitEQ alloc] initWithNumberOfBands:10];
    double frequencies[] = {32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    for (int i = 0; i < 10; i++) {
        AVAudioUnitEQFilterParameters *band = self.equalizer.bands[i];
        band.filterType  = AVAudioUnitEQFilterTypeParametric;
        band.frequency   = (float)frequencies[i];
        band.gain        = 0.0f;
        band.bandwidth   = 1.0f;
        band.bypass      = NO;
    }
    self.equalizer.bypass = NO;

    [self.engine attachNode:self.playerNode];
    [self.engine attachNode:self.equalizer];

    AVAudioFormat *format = [[AVAudioFormat alloc]
        initStandardFormatWithSampleRate:44100 channels:2];
    [self.engine connect:self.playerNode  to:self.equalizer  format:format];
    [self.engine connect:self.equalizer   to:self.engine.mainMixerNode format:format];

    NSError *err = nil;
    [self.engine startAndReturnError:&err];
    if (err) {
        NSLog(@"[AirmedyPlayer] Failed to start AVAudioEngine: %@", err);
    }
}

- (void)play {
    if (!self.audioFile && !self.ffmpegPCMBuffer) return;
    if (self.isPlaying) return;

    if (!self.engine.isRunning) {
        NSError *err = nil;
        [self.engine startAndReturnError:&err];
    }
    [self.playerNode play];
    self.isPlaying = YES;
}

- (void)pause {
    if (!self.isPlaying) return;
    self.pausePosition = [self currentPosition];
    [self.playerNode pause];
    self.isPlaying = NO;
}

- (void)stop {
    self.pausePosition = 0.0;
    self.scheduledStartFrame = 0;
    [self.playerNode stop];
    self.isPlaying = NO;
}

- (void)seek:(double)seconds {
    if (self.usingFFmpegDecoder) {
        BOOL wasPlaying = self.isPlaying;
        self.scheduleGeneration++;
        [self.playerNode stop];

        AVAudioFramePosition startFrame = (AVAudioFramePosition)(seconds * self.ffmpegSampleRate);
        if (startFrame < 0) startFrame = 0;
        AVAudioFramePosition maxFrame = (AVAudioFramePosition)self.ffmpegFrameCount - 1;
        if (maxFrame < 0) maxFrame = 0;
        if (startFrame > maxFrame) startFrame = maxFrame;

        self.pausePosition = seconds;
        [self scheduleFFmpegFrom:startFrame generation:self.scheduleGeneration];

        if (wasPlaying) {
            [self.playerNode play];
            self.isPlaying = YES;
        }
        return;
    }

    // Native AVAudioFile path
    if (!self.audioFile) return;
    BOOL wasPlaying = self.isPlaying;

    self.scheduleGeneration++;
    [self.playerNode stop];

    double sampleRate = self.audioFile.processingFormat.sampleRate;
    AVAudioFramePosition totalFrames = self.audioFile.length;
    AVAudioFramePosition startFrame  = (AVAudioFramePosition)(seconds * sampleRate);
    if (startFrame < 0) startFrame = 0;
    if (startFrame >= totalFrames) startFrame = totalFrames > 0 ? totalFrames - 1 : 0;

    AVAudioFrameCount remainingFrames = (AVAudioFrameCount)(totalFrames - startFrame);
    self.scheduledStartFrame = startFrame;
    self.pausePosition       = seconds;
    NSUInteger generation = self.scheduleGeneration;

    __weak AirmedyPlayer *weakSelf = self;
    [self.playerNode scheduleSegment:self.audioFile
                   startingFrame:startFrame
                      frameCount:remainingFrames
                           atTime:nil
               completionHandler:^{
                   dispatch_async(dispatch_get_global_queue(QOS_CLASS_DEFAULT, 0), ^{
                       AirmedyPlayer *s = weakSelf;
                       if (s && s.isPlaying && s.scheduleGeneration == generation) {
                           goHandleTrackEnd();
                       }
                   });
               }];

    if (wasPlaying) {
        [self.playerNode play];
        self.isPlaying = YES;
    }
}

- (void)setVolume:(float)volume {
    _volume = volume;
    self.playerNode.volume = volume;
}

// Reconnects the AVAudioEngine graph with the given format. Stops and restarts engine.
- (void)reconnectEngineWithFormat:(AVAudioFormat *)format {
    [self.engine stop];
    [self.engine disconnectNodeOutput:self.playerNode];
    [self.engine disconnectNodeOutput:self.equalizer];
    [self.engine connect:self.playerNode to:self.equalizer  format:format];
    [self.engine connect:self.equalizer  to:self.engine.mainMixerNode format:format];
    NSError *err = nil;
    [self.engine startAndReturnError:&err];
    if (err) {
        NSLog(@"[AirmedyPlayer] Failed to restart engine: %@", err);
    }
}

// Schedules decoded FFmpeg PCM (stored in ffmpegPCMBuffer) from the given frame offset.
- (void)scheduleFFmpegFrom:(AVAudioFramePosition)startFrame generation:(NSUInteger)gen {
    if (!self.ffmpegPCMBuffer || startFrame >= (AVAudioFramePosition)self.ffmpegFrameCount) return;

    AVAudioFrameCount remaining = (AVAudioFrameCount)(self.ffmpegFrameCount - startFrame);

    AVAudioFormat *fmt = [[AVAudioFormat alloc]
        initWithCommonFormat:AVAudioPCMFormatFloat32
                  sampleRate:self.ffmpegSampleRate
                    channels:self.ffmpegChannels
                 interleaved:NO];

    AVAudioPCMBuffer *buffer = [[AVAudioPCMBuffer alloc] initWithPCMFormat:fmt
                                                             frameCapacity:remaining];
    if (!buffer) {
        NSLog(@"[AirmedyPlayer] Failed to allocate AVAudioPCMBuffer (%u frames)", remaining);
        return;
    }
    buffer.frameLength = remaining;

    // De-interleave: ffmpegPCMBuffer is [ch0,ch1,ch0,ch1,...] -> buffer.floatChannelData[ch][frame]
    float **dst = buffer.floatChannelData;
    float  *src = self.ffmpegPCMBuffer + (NSUInteger)startFrame * self.ffmpegChannels;
    for (AVAudioFrameCount f = 0; f < remaining; f++) {
        for (ma_uint32 ch = 0; ch < self.ffmpegChannels; ch++) {
            dst[ch][f] = src[f * self.ffmpegChannels + ch];
        }
    }

    self.scheduledStartFrame = startFrame;

    __weak AirmedyPlayer *weakSelf = self;
    [self.playerNode scheduleBuffer:buffer completionHandler:^{
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_DEFAULT, 0), ^{
            AirmedyPlayer *s = weakSelf;
            if (s && s.isPlaying && s.scheduleGeneration == gen) {
                goHandleTrackEnd();
            }
        });
    }];
}

- (void)loadNative:(NSString *)path {
    self.usingFFmpegDecoder = NO;

    NSURL *url = [NSURL fileURLWithPath:path];
    NSError *err = nil;
    AVAudioFile *file = [[AVAudioFile alloc] initForReading:url error:&err];
    if (err || !file) {
        NSLog(@"[AirmedyPlayer] Failed to open audio file %@: %@", path, err);
        return;
    }
    self.audioFile = file;

    AVAudioFormat *format = file.processingFormat;
    [self reconnectEngineWithFormat:format];

    NSUInteger generation = self.scheduleGeneration;
    __weak AirmedyPlayer *weakSelf = self;
    [self.playerNode scheduleFile:file
                           atTime:nil
               completionHandler:^{
                   dispatch_async(dispatch_get_global_queue(QOS_CLASS_DEFAULT, 0), ^{
                       AirmedyPlayer *s = weakSelf;
                       if (s && s.isPlaying && s.scheduleGeneration == generation) {
                           goHandleTrackEnd();
                       }
                   });
               }];

    self.playerNode.volume = _volume;
}

- (void)loadFFmpeg:(NSString *)path {
    self.usingFFmpegDecoder = YES;
    self.audioFile = nil;

    if (self.ffmpegPCMBuffer) {
        free(self.ffmpegPCMBuffer);
        self.ffmpegPCMBuffer = NULL;
    }

    // Use the engine's output sample rate; fall back to 44100 if unavailable.
    ma_uint32 targetRate = (ma_uint32)[self.engine.outputNode outputFormatForBus:0].sampleRate;
    if (targetRate == 0) targetRate = 44100;

    float    *rawPCM   = NULL;
    ma_uint64 frames   = 0;
    ma_uint32 channels = 0;

    int result = ffmpeg_decode_file(path.UTF8String, targetRate, &rawPCM, &frames, &channels);
    if (result != 0 || !rawPCM) {
        NSLog(@"[AirmedyPlayer] FFmpeg decode failed for %@ (code %d)", path, result);
        return;
    }

    self.ffmpegPCMBuffer  = rawPCM;
    self.ffmpegFrameCount = frames;
    self.ffmpegChannels   = channels > 0 ? channels : 2;
    self.ffmpegSampleRate = targetRate;

    AVAudioFormat *fmt = [[AVAudioFormat alloc]
        initWithCommonFormat:AVAudioPCMFormatFloat32
                  sampleRate:targetRate
                    channels:self.ffmpegChannels
                 interleaved:NO];
    [self reconnectEngineWithFormat:fmt];

    [self scheduleFFmpegFrom:0 generation:self.scheduleGeneration];
    self.playerNode.volume = _volume;
}

- (void)load:(NSString *)path {
    BOOL wasPlaying = self.isPlaying;
    self.scheduleGeneration++;
    [self.playerNode stop];
    self.isPlaying = NO;
    self.pausePosition = 0.0;
    self.scheduledStartFrame = 0;

    if (isAVFoundationNative(path.pathExtension)) {
        [self loadNative:path];
    } else {
        [self loadFFmpeg:path];
    }

    if (wasPlaying) {
        [self.playerNode play];
        self.isPlaying = YES;
    }
}

- (double)currentPosition {
    if (!self.isPlaying) {
        return self.pausePosition;
    }
    AVAudioTime *nodeTime = self.playerNode.lastRenderTime;
    if (!nodeTime) return self.pausePosition;

    AVAudioTime *playerTime = [self.playerNode playerTimeForNodeTime:nodeTime];
    if (!playerTime || playerTime.sampleTime < 0) return self.pausePosition;

    double sampleRate;
    if (self.usingFFmpegDecoder) {
        sampleRate = self.ffmpegSampleRate > 0 ? self.ffmpegSampleRate : 44100.0;
    } else {
        sampleRate = self.audioFile ? self.audioFile.processingFormat.sampleRate : 44100.0;
    }
    return (double)(self.scheduledStartFrame + playerTime.sampleTime) / sampleRate;
}

// --- EQ ---

- (void)setEQBandIndex:(int)index frequency:(double)freq gain:(double)gain bandwidth:(double)bw {
    if (index < 0 || index >= (int)self.equalizer.bands.count) return;
    AVAudioUnitEQFilterParameters *band = self.equalizer.bands[index];
    band.frequency = (float)freq;
    band.gain      = (float)gain;
    band.bandwidth = (float)bw;
}

- (void)setEQEnabled:(BOOL)enabled {
    self.eqEnabled           = enabled;
    self.equalizer.bypass    = !enabled;
}

// --- Now Playing ---

- (void)setupRemoteCommandCenter {
    MPRemoteCommandCenter *center = [MPRemoteCommandCenter sharedCommandCenter];

    [center.playCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
        goHandleRemotePlay();
        return MPRemoteCommandHandlerStatusSuccess;
    }];

    [center.pauseCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
        goHandleRemotePause();
        return MPRemoteCommandHandlerStatusSuccess;
    }];

    [center.nextTrackCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
        goHandleRemoteNext();
        return MPRemoteCommandHandlerStatusSuccess;
    }];

    [center.previousTrackCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
        goHandleRemotePrevious();
        return MPRemoteCommandHandlerStatusSuccess;
    }];

    [center.togglePlayPauseCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
        if (self.isPlaying) {
            goHandleRemotePause();
        } else {
            goHandleRemotePlay();
        }
        return MPRemoteCommandHandlerStatusSuccess;
    }];
}

- (void)updateNowPlayingTitle:(NSString *)title
                       artist:(NSString *)artist
                        album:(NSString *)album
                     duration:(double)duration
                     position:(double)position
                  artworkPath:(NSString *)artworkPath {
    NSMutableDictionary *info = [NSMutableDictionary dictionary];
    info[MPMediaItemPropertyTitle]              = title ?: @"";
    info[MPMediaItemPropertyArtist]             = artist ?: @"";
    info[MPMediaItemPropertyAlbumTitle]         = album ?: @"";
    info[MPMediaItemPropertyPlaybackDuration]   = @(duration);
    info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = @(position);
    info[MPNowPlayingInfoPropertyPlaybackRate]  = @(self.isPlaying ? 1.0 : 0.0);

    if (artworkPath && artworkPath.length > 0) {
        NSImage *image = [[NSImage alloc] initWithContentsOfFile:artworkPath];
        if (image) {
            MPMediaItemArtwork *artwork = [[MPMediaItemArtwork alloc]
                initWithBoundsSize:image.size
                    requestHandler:^NSImage *(CGSize size) { return image; }];
            info[MPMediaItemPropertyArtwork] = artwork;
        }
    }
    [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = info;
}

- (void)clearNowPlaying {
    [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nil;
}

@end

// ============================================================
// C-bridge functions
// ============================================================

void* InitPlayer() {
    AirmedyPlayer *player = [[AirmedyPlayer alloc] init];
    return (__bridge_retained void *)player;
}

void PlayPlayer(void *playerPtr) {
    [(__bridge AirmedyPlayer *)playerPtr play];
}

void PausePlayer(void *playerPtr) {
    [(__bridge AirmedyPlayer *)playerPtr pause];
}

void StopPlayer(void *playerPtr) {
    [(__bridge AirmedyPlayer *)playerPtr stop];
}

void SeekPlayer(void *playerPtr, double seconds) {
    [(__bridge AirmedyPlayer *)playerPtr seek:seconds];
}

void SetVolumePlayer(void *playerPtr, float volume) {
    [(__bridge AirmedyPlayer *)playerPtr setVolume:volume];
}

void LoadPlayer(void *playerPtr, const char *path) {
    NSString *p = [NSString stringWithUTF8String:path];
    [(__bridge AirmedyPlayer *)playerPtr load:p];
}

double GetCurrentTimePlayer(void *playerPtr) {
    return [(__bridge AirmedyPlayer *)playerPtr currentPosition];
}

void SetupRemoteCommandCenter(void *playerPtr) {
    [(__bridge AirmedyPlayer *)playerPtr setupRemoteCommandCenter];
}

void UpdateNowPlayingInfo(void *playerPtr,
                          const char *title,
                          const char *artist,
                          const char *album,
                          double duration,
                          double position,
                          const char *artworkPath) {
    AirmedyPlayer *p = (__bridge AirmedyPlayer *)playerPtr;
    [p updateNowPlayingTitle:[NSString stringWithUTF8String:title ?: ""]
                      artist:[NSString stringWithUTF8String:artist ?: ""]
                       album:[NSString stringWithUTF8String:album ?: ""]
                    duration:duration
                    position:position
                 artworkPath:artworkPath ? [NSString stringWithUTF8String:artworkPath] : nil];
}

void ClearNowPlayingInfo(void *playerPtr) {
    [(__bridge AirmedyPlayer *)playerPtr clearNowPlaying];
}

void SetEQBand(void *playerPtr, int index, double freq, double gain, double bandwidth) {
    [(__bridge AirmedyPlayer *)playerPtr setEQBandIndex:index frequency:freq gain:gain bandwidth:bandwidth];
}

void SetEQEnabled(void *playerPtr, int enabled) {
    [(__bridge AirmedyPlayer *)playerPtr setEQEnabled:(BOOL)enabled];
}
