#import <AppKit/AppKit.h>
#import <AVFoundation/AVFoundation.h>
#import <Foundation/Foundation.h>
#import <MediaPlayer/MediaPlayer.h>

// Forward declarations of Go callback functions
extern void goHandleTrackEnd();
extern void goHandleRemotePlay();
extern void goHandleRemotePause();
extern void goHandleRemoteNext();
extern void goHandleRemotePrevious();

@interface AirmedyPlayer : NSObject
// AVAudioEngine pipeline
@property (strong, nonatomic) AVAudioEngine       *engine;
@property (strong, nonatomic) AVAudioPlayerNode   *playerNode;
@property (strong, nonatomic) AVAudioUnitEQ       *equalizer;
@property (strong, nonatomic) AVAudioMixerNode    *mixerNode;
// Loaded audio file
@property (strong, nonatomic) AVAudioFile         *audioFile;
// Playback state
@property (assign, nonatomic) BOOL  isPlaying;
@property (assign, nonatomic) BOOL  eqEnabled;
@property (assign, nonatomic) float volume;
// Position tracking
@property (assign, nonatomic) AVAudioFramePosition scheduledStartFrame; // file-relative start
@property (assign, nonatomic) NSTimeInterval       pausePosition;       // seconds at last pause/seek
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
        [self setupEngine];
    }
    return self;
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
    if (!self.audioFile) return;
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

- (void)load:(NSString *)path {
    BOOL wasPlaying = self.isPlaying;
    // Invalidate any in-flight completion handlers BEFORE stopping nodes, so stale
    // callbacks that fire during stop() see a different generation and skip.
    self.scheduleGeneration++;
    [self.playerNode stop];
    self.isPlaying = NO;
    self.pausePosition = 0.0;
    self.scheduledStartFrame = 0;

    NSURL *url = [NSURL fileURLWithPath:path];
    NSError *err = nil;
    AVAudioFile *file = [[AVAudioFile alloc] initForReading:url error:&err];
    if (err || !file) {
        NSLog(@"[AirmedyPlayer] Failed to open audio file %@: %@", path, err);
        return;
    }
    self.audioFile = file;

    // Must stop the engine before modifying the graph — connecting nodes on a
    // running engine throws 'com.apple.coreaudio.avfaudio' error -10868.
    [self.engine stop];

    AVAudioFormat *format = file.processingFormat;
    [self.engine disconnectNodeOutput:self.playerNode];
    [self.engine disconnectNodeOutput:self.equalizer];
    [self.engine connect:self.playerNode to:self.equalizer  format:format];
    [self.engine connect:self.equalizer  to:self.engine.mainMixerNode format:format];

    NSError *startErr = nil;
    [self.engine startAndReturnError:&startErr];
    if (startErr) {
        NSLog(@"[AirmedyPlayer] Failed to restart engine after load: %@", startErr);
    }

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

    if (wasPlaying) {
        [self.playerNode play];
        self.isPlaying = YES;
    }
}

- (double)currentPosition {
    if (!self.audioFile || !self.isPlaying) {
        return self.pausePosition;
    }
    AVAudioTime *nodeTime = self.playerNode.lastRenderTime;
    if (!nodeTime) return self.pausePosition;

    AVAudioTime *playerTime = [self.playerNode playerTimeForNodeTime:nodeTime];
    if (!playerTime || playerTime.sampleTime < 0) return self.pausePosition;

    double sampleRate = self.audioFile.processingFormat.sampleRate;
    double elapsed = (double)(self.scheduledStartFrame + playerTime.sampleTime) / sampleRate;
    return elapsed;
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
