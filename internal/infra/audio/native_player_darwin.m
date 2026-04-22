#import <AVFoundation/AVFoundation.h>
#import <Foundation/Foundation.h>

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
    
    // Listen for end of track
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(playerItemDidReachEnd:)
                                                 name:AVPlayerItemDidPlayToEndTimeNotification
                                               object:item];
                                               
    [self.player replaceCurrentItemWithPlayerItem:item];
}

- (void)playerItemDidReachEnd:(NSNotification *)notification {
    // This will be called when the track finishes.
    // We need to notify Go here.
    extern void goHandleTrackEnd();
    goHandleTrackEnd();
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
