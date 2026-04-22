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
@property (strong, nonatomic) AVPlayer *player;
@property (strong, nonatomic) id timeObserver;
@end

@implementation AirmedyPlayer

- (instancetype)init {
    self = [super init];
    if (self) {
        self.player = [[AVPlayer alloc] init];
    }
    return self;
}

- (void)play {
    [self.player play];
}

- (void)pause {
    [self.player pause];
}

- (void)stop {
    [self.player pause];
    [self.player seekToTime:kCMTimeZero];
}

- (void)seek:(double)seconds {
    CMTime time = CMTimeMakeWithSeconds(seconds, 1000);
    [self.player seekToTime:time toleranceBefore:kCMTimeZero toleranceAfter:kCMTimeZero];
}

- (void)setVolume:(float)volume {
    self.player.volume = volume;
}

- (void)load:(NSString *)urlStr {
    NSURL *url = [NSURL fileURLWithPath:urlStr];
    AVPlayerItem *item = [AVPlayerItem playerItemWithURL:url];

    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(playerItemDidReachEnd:)
                                                 name:AVPlayerItemDidPlayToEndTimeNotification
                                               object:item];

    [self.player replaceCurrentItemWithPlayerItem:item];
}

- (void)playerItemDidReachEnd:(NSNotification *)notification {
    goHandleTrackEnd();
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
        // Determine current state and toggle
        if (self.player.rate > 0) {
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

    info[MPMediaItemPropertyTitle] = title ?: @"";
    info[MPMediaItemPropertyArtist] = artist ?: @"";
    info[MPMediaItemPropertyAlbumTitle] = album ?: @"";
    info[MPMediaItemPropertyPlaybackDuration] = @(duration);
    info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = @(position);
    info[MPNowPlayingInfoPropertyPlaybackRate] = @(self.player.rate);

    if (artworkPath && artworkPath.length > 0) {
        NSImage *image = [[NSImage alloc] initWithContentsOfFile:artworkPath];
        if (image) {
            MPMediaItemArtwork *artwork = [[MPMediaItemArtwork alloc]
                initWithBoundsSize:image.size
                    requestHandler:^NSImage *(CGSize size) {
                        return image;
                    }];
            info[MPMediaItemPropertyArtwork] = artwork;
        }
    }

    [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = info;
}

- (void)clearNowPlaying {
    [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nil;
}

@end

// C-bridge functions

void* InitPlayer() {
    AirmedyPlayer* player = [[AirmedyPlayer alloc] init];
    return (__bridge_retained void*)player;
}

void PlayPlayer(void* playerPtr) {
    AirmedyPlayer* player = (__bridge AirmedyPlayer*)playerPtr;
    [player play];
}

void PausePlayer(void* playerPtr) {
    AirmedyPlayer* player = (__bridge AirmedyPlayer*)playerPtr;
    [player pause];
}

void StopPlayer(void* playerPtr) {
    AirmedyPlayer* player = (__bridge AirmedyPlayer*)playerPtr;
    [player stop];
}

void SeekPlayer(void* playerPtr, double seconds) {
    AirmedyPlayer* player = (__bridge AirmedyPlayer*)playerPtr;
    [player seek:seconds];
}

void SetVolumePlayer(void* playerPtr, float volume) {
    AirmedyPlayer* player = (__bridge AirmedyPlayer*)playerPtr;
    [player setVolume:volume];
}

void LoadPlayer(void* playerPtr, const char* url) {
    AirmedyPlayer* player = (__bridge AirmedyPlayer*)playerPtr;
    NSString* urlStr = [NSString stringWithUTF8String:url];
    [player load:urlStr];
}

double GetCurrentTimePlayer(void* playerPtr) {
    AirmedyPlayer* player = (__bridge AirmedyPlayer*)playerPtr;
    return CMTimeGetSeconds(player.player.currentTime);
}

void SetupRemoteCommandCenter(void* playerPtr) {
    AirmedyPlayer* player = (__bridge AirmedyPlayer*)playerPtr;
    [player setupRemoteCommandCenter];
}

void UpdateNowPlayingInfo(void* playerPtr,
                          const char* title,
                          const char* artist,
                          const char* album,
                          double duration,
                          double position,
                          const char* artworkPath) {
    AirmedyPlayer* player = (__bridge AirmedyPlayer*)playerPtr;
    [player updateNowPlayingTitle:[NSString stringWithUTF8String:title ?: ""]
                           artist:[NSString stringWithUTF8String:artist ?: ""]
                            album:[NSString stringWithUTF8String:album ?: ""]
                         duration:duration
                         position:position
                      artworkPath:artworkPath ? [NSString stringWithUTF8String:artworkPath] : nil];
}

void ClearNowPlayingInfo(void* playerPtr) {
    AirmedyPlayer* player = (__bridge AirmedyPlayer*)playerPtr;
    [player clearNowPlaying];
}
