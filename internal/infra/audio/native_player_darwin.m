#import <AppKit/AppKit.h>
#import <AVFoundation/AVFoundation.h>
#import <Foundation/Foundation.h>
#import <MediaPlayer/MediaPlayer.h>
@import SFBAudioEngine;

// Forward declarations of Go callback functions
extern void goHandleTrackEnd();
extern void goHandleRemotePlay();
extern void goHandleRemotePause();
extern void goHandleRemoteNext();
extern void goHandleRemotePrevious();
extern void goHandleRemoteSeek(double position);

@interface AirmedyPlayer : NSObject <SFBAudioPlayerDelegate>
@property (strong, nonatomic) SFBAudioPlayer  *sfbPlayer;
@property (strong, nonatomic) AVAudioUnitEQ   *equalizer;
@property (assign, nonatomic) BOOL             eqEnabled;
@property (assign, nonatomic) float            volume;
@property (assign, nonatomic) BOOL             isPlaying;
@property (assign, nonatomic) NSTimeInterval   pausePosition;
@end

@implementation AirmedyPlayer

- (instancetype)init {
    self = [super init];
    if (!self) return nil;

    _volume        = 1.0f;
    _eqEnabled     = YES;
    _isPlaying     = NO;
    _pausePosition = 0.0;

    // 10-band parametric EQ at ISO standard frequencies
    _equalizer = [[AVAudioUnitEQ alloc] initWithNumberOfBands:10];
    double freqs[] = {32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    for (int i = 0; i < 10; i++) {
        AVAudioUnitEQFilterParameters *b = _equalizer.bands[i];
        b.filterType = AVAudioUnitEQFilterTypeParametric;
        b.frequency  = (float)freqs[i];
        b.gain       = 0.0f;
        b.bandwidth  = 1.0f;
        b.bypass     = NO;
    }
    _equalizer.bypass = NO;

    _sfbPlayer = [[SFBAudioPlayer alloc] init];
    _sfbPlayer.delegate = self;

    // Insert EQ between sourceNode and mainMixerNode.
    // On init, SFBAudioEngine connects: sourceNode → mainMixerNode.
    // We insert EQ so: sourceNode → EQ → mainMixerNode.
    // Subsequent format changes are handled by reconfigureProcessingGraph:withFormat:.
    __weak AirmedyPlayer *weakSelf = self;
    [_sfbPlayer modifyProcessingGraph:^(AVAudioEngine *engine) {
        AirmedyPlayer *s = weakSelf;
        if (!s) return;
        AVAudioNode *src = s->_sfbPlayer.sourceNode;
        AVAudioMixerNode *mixer = engine.mainMixerNode;
        [engine disconnectNodeOutput:src bus:0];
        [engine attachNode:s->_equalizer];
        [engine connect:src to:s->_equalizer format:nil];
        [engine connect:s->_equalizer to:mixer format:nil];
    }];

    return self;
}

// --- SFBAudioPlayerDelegate ---

// Called when audio format changes; reconnect EQ with the new format.
// SFBAudioEngine connects sourceNode → returned node with format.
// We connect returned node → mainMixerNode with format.
- (AVAudioNode *)audioPlayer:(SFBAudioPlayer *)audioPlayer
    reconfigureProcessingGraph:(AVAudioEngine *)engine
                    withFormat:(AVAudioFormat *)format
{
    if ([engine.attachedNodes containsObject:self.equalizer]) {
        [engine disconnectNodeOutput:self.equalizer bus:0];
    } else {
        [engine attachNode:self.equalizer];
    }
    [engine connect:self.equalizer to:engine.mainMixerNode format:format];
    return self.equalizer;
}

// Fires when last sample is rendered — the true end of playback.
// If a next track was pre-queued (gapless), SFBAudioEngine is already playing it,
// so isPlaying stays YES. Only mark stopped if the engine actually stopped.
- (void)audioPlayer:(SFBAudioPlayer *)audioPlayer
   renderingComplete:(id<SFBPCMDecoding>)decoder
{
    if (!audioPlayer.isPlaying) {
        self.isPlaying = NO;
        self.pausePosition = 0.0;
    }
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_DEFAULT, 0), ^{
        goHandleTrackEnd();
    });
}

- (void)audioPlayer:(SFBAudioPlayer *)audioPlayer
    playbackStateChanged:(SFBAudioPlayerPlaybackState)playbackState
{
    // Keep isPlaying in sync if SFBAudioEngine transitions externally (e.g. interruption).
    self.isPlaying = (playbackState == SFBAudioPlayerPlaybackStatePlaying);
}

- (void)audioPlayer:(SFBAudioPlayer *)audioPlayer
    encounteredError:(NSError *)error
{
    NSLog(@"[AirmedyPlayer] Error: %@", error);
    self.isPlaying = NO;
}

// --- Playback ---

- (void)play {
    NSError *err = nil;
    [self.sfbPlayer playReturningError:&err];
    if (err) {
        NSLog(@"[AirmedyPlayer] play error: %@", err);
        return;
    }
    self.isPlaying = YES;
    [self updatePlaybackRate];
}

- (void)pause {
    self.pausePosition = [self currentPosition];
    [self.sfbPlayer pause];
    self.isPlaying = NO;
    [self updatePlaybackRate];
}

- (void)stop {
    [self.sfbPlayer stop];
    self.isPlaying = NO;
    self.pausePosition = 0.0;
}

- (void)load:(NSString *)path {
    BOOL wasPlaying = self.isPlaying;
    self.isPlaying = NO;
    self.pausePosition = 0.0;

    NSURL *url = [NSURL fileURLWithPath:path];
    NSError *err = nil;
    [self.sfbPlayer enqueueURL:url forImmediatePlayback:YES error:&err];
    if (err) {
        NSLog(@"[AirmedyPlayer] Failed to load %@: %@", path, err);
        return;
    }
    [self.sfbPlayer setVolume:_volume error:nil];

    if (wasPlaying) {
        [self play];
    }
}

- (void)enqueueNext:(NSString *)path {
    NSURL *url = [NSURL fileURLWithPath:path];
    NSError *err = nil;
    [self.sfbPlayer enqueueURL:url forImmediatePlayback:NO error:&err];
    if (err) {
        NSLog(@"[AirmedyPlayer] Failed to enqueue next %@: %@", path, err);
    }
}

- (void)clearEnqueued {
    [self.sfbPlayer clearQueue];
}

- (void)seek:(double)seconds {
    if ([self.sfbPlayer seekToTime:seconds]) {
        self.pausePosition = seconds;
    }
}

- (void)setVolume:(float)volume {
    _volume = volume;
    [self.sfbPlayer setVolume:volume error:nil];
}

- (double)currentPosition {
    if (!self.isPlaying) return self.pausePosition;
    NSTimeInterval t = self.sfbPlayer.currentTime;
    return (t > 0) ? t : self.pausePosition;
}

// --- EQ ---

- (void)setEQBandIndex:(int)index frequency:(double)freq gain:(double)gain bandwidth:(double)bw {
    if (index < 0 || index >= (int)self.equalizer.bands.count) return;
    AVAudioUnitEQFilterParameters *b = self.equalizer.bands[index];
    b.frequency = (float)freq;
    b.gain      = (float)gain;
    b.bandwidth = (float)bw;
}

- (void)setEQEnabled:(BOOL)enabled {
    self.eqEnabled        = enabled;
    self.equalizer.bypass = !enabled;
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
    info[MPNowPlayingInfoPropertyPlaybackRate] = @(self.isPlaying ? 1.0 : 0.0);
    center.nowPlayingInfo = info;
}

- (void)updateNowPlayingTitle:(NSString *)title
                       artist:(NSString *)artist
                        album:(NSString *)album
                     duration:(double)duration
                     position:(double)position
                  artworkPath:(NSString *)artworkPath {
    NSMutableDictionary *info = [NSMutableDictionary dictionary];
    info[MPMediaItemPropertyTitle]                       = title ?: @"";
    info[MPMediaItemPropertyArtist]                      = artist ?: @"";
    info[MPMediaItemPropertyAlbumTitle]                  = album ?: @"";
    info[MPMediaItemPropertyPlaybackDuration]            = @(duration);
    info[MPNowPlayingInfoPropertyElapsedPlaybackTime]    = @(position);
    info[MPNowPlayingInfoPropertyDefaultPlaybackRate]    = @(1.0);
    info[MPNowPlayingInfoPropertyPlaybackRate]           = @(self.isPlaying ? 1.0 : 0.0);

    if (artworkPath.length > 0) {
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
// C-bridge functions (signatures unchanged from previous impl)
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

void EnqueueNextPlayer(void *playerPtr, const char *path) {
    NSString *p = [NSString stringWithUTF8String:path];
    [(__bridge AirmedyPlayer *)playerPtr enqueueNext:p];
}

void ClearEnqueuedPlayer(void *playerPtr) {
    [(__bridge AirmedyPlayer *)playerPtr clearEnqueued];
}
