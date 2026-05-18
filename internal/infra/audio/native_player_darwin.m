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
extern void goHandleRemoteSeek(double position);

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
// FFmpeg streaming path
@property (nonatomic) FFmpegHandle *ffmpegStream;
@property (nonatomic) ma_uint64    ffmpegFrameCount;
@property (nonatomic) ma_uint32    ffmpegChannels;
@property (nonatomic) ma_uint32    ffmpegSampleRate;
@property (nonatomic) BOOL         usingFFmpegDecoder;
@property (nonatomic) BOOL         isLoading;
@property (assign, nonatomic) BOOL         shouldPlayAfterLoad;
@property (assign, nonatomic) NSUInteger           loadingGeneration;
// Playback state
@property (assign, nonatomic) BOOL  isPlaying;
@property (assign, nonatomic) BOOL  eqEnabled;
@property (assign, nonatomic) float volume;
// Position tracking
@property (assign, nonatomic) AVAudioFramePosition scheduledStartFrame;
@property (assign, nonatomic) NSTimeInterval       pausePosition;
// Generation counter: incremented on each load/seek to invalidate stale completion handlers
@property (assign, nonatomic) NSUInteger           scheduleGeneration;
// Tracks which generation already fired goHandleTrackEnd to prevent duplicate callbacks
@property (assign, nonatomic) NSUInteger           trackEndFiredGeneration;
@end

#define FFMPEG_CHUNK_FRAMES (44100 * 2) // 2 seconds of audio at 44.1kHz

@implementation AirmedyPlayer

- (instancetype)init {
    self = [super init];
    if (self) {
        _volume = 1.0f;
        _eqEnabled = YES;
        _isPlaying = NO;
        _isLoading = NO;
        _shouldPlayAfterLoad = NO;
        _pausePosition = 0.0;
        _scheduledStartFrame = 0;
        _ffmpegStream = NULL;
        _ffmpegFrameCount = 0;
        _ffmpegChannels = 0;
        _ffmpegSampleRate = 0;
        _usingFFmpegDecoder = NO;
        _loadingGeneration = 0;
        _trackEndFiredGeneration = NSUIntegerMax;
        [self setupEngine];
    }
    return self;
}

- (void)dealloc {
    @synchronized(self) {
        if (_ffmpegStream) {
            ffmpeg_close(_ffmpegStream);
            _ffmpegStream = NULL;
        }
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
    if (self.isLoading) {
        self.shouldPlayAfterLoad = YES;
        self.isPlaying = YES;
        return;
    }
    
    if (!self.audioFile && !self.ffmpegStream) return;
    
    // Always ensure engine is running
    if (!self.engine.isRunning) {
        NSError *err = nil;
        [self.engine startAndReturnError:&err];
    }
    
    // Only call play on the node if it's not already playing.
    // Note: We don't return early if self.isPlaying is true because that might be an optimistic state.
    if (!self.playerNode.isPlaying) {
        [self.playerNode play];
    }
    self.isPlaying = YES;
    [self updatePlaybackRate];
}

- (void)pause {
    if (self.isLoading) {
        self.shouldPlayAfterLoad = NO;
        self.isPlaying = NO;
        return;
    }
    if (!self.isPlaying) return;
    self.pausePosition = [self currentPosition];
    [self.playerNode pause];
    self.isPlaying = NO;
    [self updatePlaybackRate];
}

- (void)stop {
    self.pausePosition = 0.0;
    self.scheduledStartFrame = 0;
    self.isLoading = NO;
    self.shouldPlayAfterLoad = NO;
    self.loadingGeneration++;
    [self.playerNode stop];
    self.isPlaying = NO;
    // Clear Now Playing is handled by Go side
    @synchronized(self) {
        if (self.ffmpegStream) {
            ffmpeg_close(self.ffmpegStream);
            self.ffmpegStream = NULL;
        }
    }
}

- (void)seek:(double)seconds {
    if (self.isLoading) return;

    if (self.usingFFmpegDecoder) {
        @synchronized(self) {
            if (self.ffmpegStream) {
                BOOL wasPlaying = self.isPlaying;
                self.scheduleGeneration++;
                [self.playerNode stop];

                if (ffmpeg_seek(self.ffmpegStream, seconds) == 0) {
                    self.pausePosition = seconds;
                    self.scheduledStartFrame = (AVAudioFramePosition)(seconds * self.ffmpegSampleRate);
                    [self scheduleNextFFmpegChunkWithGeneration:self.scheduleGeneration];
                    [self scheduleNextFFmpegChunkWithGeneration:self.scheduleGeneration];
                    [self scheduleNextFFmpegChunkWithGeneration:self.scheduleGeneration];
                }
                if (wasPlaying) {
                    [self.playerNode play];
                    self.isPlaying = YES;
                }
            }
        }
        [self updatePlaybackRate];
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
    if (remainingFrames == 0) {
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_DEFAULT, 0), ^{
            goHandleTrackEnd();
        });
        return;
    }
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
    [self updatePlaybackRate];
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

// Asynchronously decodes and schedules the next chunk of audio from FFmpeg.
- (void)scheduleNextFFmpegChunkWithGeneration:(NSUInteger)gen {
    __weak AirmedyPlayer *weakSelf = self;
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        AirmedyPlayer *s = weakSelf;
        if (!s) return;
        
        float *buf = NULL;
        int read = 0;
        ma_uint32 channels = 0;
        ma_uint32 sampleRate = 0;

        @synchronized(s) {
            if (s.scheduleGeneration != gen || !s.ffmpegStream) return;
            
            channels = s.ffmpegChannels;
            sampleRate = s.ffmpegSampleRate;
            int framesToRead = FFMPEG_CHUNK_FRAMES;
            buf = malloc(framesToRead * channels * sizeof(float));
            read = ffmpeg_read(s.ffmpegStream, buf, framesToRead);
        }
        
        dispatch_async(dispatch_get_main_queue(), ^{
            AirmedyPlayer *ms = weakSelf;
            if (!ms || ms.scheduleGeneration != gen) {
                if (buf) free(buf);
                return;
            }
            if (read <= 0) {
                if (buf) free(buf);
                if (ms.isPlaying && ms.trackEndFiredGeneration != gen) {
                    ms.trackEndFiredGeneration = gen;
                    goHandleTrackEnd();
                }
                return;
            }
            
            AVAudioFormat *fmt = [[AVAudioFormat alloc]
                initWithCommonFormat:AVAudioPCMFormatFloat32
                          sampleRate:sampleRate
                            channels:channels
                         interleaved:NO];
            AVAudioPCMBuffer *buffer = [[AVAudioPCMBuffer alloc] initWithPCMFormat:fmt frameCapacity:read];
            buffer.frameLength = read;
            
            float **dst = buffer.floatChannelData;
            for (int f = 0; f < read; f++) {
                for (int ch = 0; ch < (int)channels; ch++) {
                    dst[ch][f] = buf[f * channels + ch];
                }
            }
            free(buf);
            
            [ms.playerNode scheduleBuffer:buffer completionHandler:^{
                dispatch_async(dispatch_get_main_queue(), ^{
                    [weakSelf scheduleNextFFmpegChunkWithGeneration:gen];
                });
            }];
        });
    });
}

- (void)loadNative:(NSString *)path {
    self.usingFFmpegDecoder = NO;
    self.isLoading = NO;

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

- (void)loadFFmpeg:(NSString *)path wasPlaying:(BOOL)wasPlaying {
    @synchronized(self) {
        if (self.ffmpegStream) {
            ffmpeg_close(self.ffmpegStream);
            self.ffmpegStream = NULL;
        }
    }
    
    self.usingFFmpegDecoder = YES;
    self.audioFile = nil;
    self.isLoading = YES;
    self.shouldPlayAfterLoad = wasPlaying;

    NSUInteger loadingGen = ++self.loadingGeneration;
    ma_uint32 targetRate = (ma_uint32)[self.engine.outputNode outputFormatForBus:0].sampleRate;
    if (targetRate == 0) targetRate = 44100;

    __weak AirmedyPlayer *weakSelf = self;
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        FFmpegHandle *h = ffmpeg_open(path.UTF8String, targetRate);

        dispatch_async(dispatch_get_main_queue(), ^{
            AirmedyPlayer *s = weakSelf;
            if (!s || s.loadingGeneration != loadingGen) {
                if (h) ffmpeg_close(h);
                return;
            }

            s.isLoading = NO;
            if (!h) {
                NSLog(@"[AirmedyPlayer] FFmpeg open failed for %@", path);
                return;
            }

            @synchronized(s) {
                s.ffmpegStream = h;
                s.ffmpegFrameCount = h->total_frames;
                s.ffmpegChannels   = h->n_ch;
                s.ffmpegSampleRate = targetRate;
            }

            AVAudioFormat *fmt = [[AVAudioFormat alloc]
                initWithCommonFormat:AVAudioPCMFormatFloat32
                        sampleRate:targetRate
                            channels:h->n_ch
                        interleaved:NO];
            [s reconnectEngineWithFormat:fmt];

            s.scheduleGeneration++;
            s.scheduledStartFrame = 0;
            [s scheduleNextFFmpegChunkWithGeneration:s.scheduleGeneration];
            [s scheduleNextFFmpegChunkWithGeneration:s.scheduleGeneration];
            [s scheduleNextFFmpegChunkWithGeneration:s.scheduleGeneration];
            
            s.playerNode.volume = s.volume;
            if (s.shouldPlayAfterLoad) {
                [s play];
            }
        });
    });
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
        if (wasPlaying) {
            [self play];
        }
    } else {
        [self loadFFmpeg:path wasPlaying:wasPlaying];
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

    __weak AirmedyPlayer *weakSelf = self;
    [center.togglePlayPauseCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
        AirmedyPlayer *s = weakSelf;
        if (s && s.isPlaying) {
            goHandleRemotePause();
        } else {
            goHandleRemotePlay();
        }
        return MPRemoteCommandHandlerStatusSuccess;
    }];

    [center.changePlaybackPositionCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
        MPChangePlaybackPositionCommandEvent *posEvent = (MPChangePlaybackPositionCommandEvent *)event;
        goHandleRemoteSeek(posEvent.positionTime);
        return MPRemoteCommandHandlerStatusSuccess;
    }];
}

- (void)updatePlaybackRate {
    MPNowPlayingInfoCenter *center = [MPNowPlayingInfoCenter defaultCenter];
    NSDictionary *currentInfo = center.nowPlayingInfo;
    if (!currentInfo) return;

    NSMutableDictionary *info = [currentInfo mutableCopy];
    info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = @([self currentPosition]);
    info[MPNowPlayingInfoPropertyPlaybackRate]  = @(self.isPlaying ? 1.0 : 0.0);
    center.nowPlayingInfo = info;
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
    info[MPMediaItemPropertyPlaybackDuration]            = @(duration);
    info[MPNowPlayingInfoPropertyElapsedPlaybackTime]    = @(position);
    info[MPNowPlayingInfoPropertyDefaultPlaybackRate]    = @(1.0);
    info[MPNowPlayingInfoPropertyPlaybackRate]           = @(self.isPlaying ? 1.0 : 0.0);

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

- (void)updateNowPlayingPosition:(double)position {
    NSDictionary *cur = [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo;
    if (!cur) return;
    NSMutableDictionary *info = [cur mutableCopy];
    info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = @(position);
    info[MPNowPlayingInfoPropertyPlaybackRate] = @(self.isPlaying ? 1.0 : 0.0);
    [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = info;
}

@end

// ============================================================
// C-bridge functions
// ============================================================

void* InitPlayer() {
    AirmedyPlayer *player = [[AirmedyPlayer alloc] init];
    return (__bridge_retained void *)player;
}

void DestroyPlayer(void *playerPtr) {
    if (playerPtr) CFRelease(playerPtr);
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

void UpdateNowPlayingPosition(void *playerPtr, double position) {
    [(__bridge AirmedyPlayer *)playerPtr updateNowPlayingPosition:position];
}

void SetEQBand(void *playerPtr, int index, double freq, double gain, double bandwidth) {
    [(__bridge AirmedyPlayer *)playerPtr setEQBandIndex:index frequency:freq gain:gain bandwidth:bandwidth];
}

void SetEQEnabled(void *playerPtr, int enabled) {
    [(__bridge AirmedyPlayer *)playerPtr setEQEnabled:(BOOL)enabled];
}
