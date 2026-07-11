#import <AppKit/AppKit.h>
#import <AudioToolbox/AudioToolbox.h>
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

// ============================================================
// AirmedyStereoWidener — custom in-process AUAudioUnit performing a mid/side
// stereo-width adjustment (M=(L+R)/2, S=(L-R)/2*width, L'=M+S, R'=M-S).
// width=1.0 is mathematically the identity transform, so the node can stay
// permanently in the graph at the default. Registered once per process and
// instantiated synchronously (no XPC hop needed for in-process components).
// ============================================================

@interface AirmedyStereoWidener : AUAudioUnit
@property (atomic, assign) float width; // 1.0 = neutral, 0.0 = mono, up to 2.0 = wider
@end

@implementation AirmedyStereoWidener {
    AUAudioUnitBusArray *_inputBusArray;
    AUAudioUnitBusArray *_outputBusArray;
}

- (instancetype)initWithComponentDescription:(AudioComponentDescription)componentDescription
                                      options:(AudioComponentInstantiationOptions)options
                                        error:(NSError **)outError {
    self = [super initWithComponentDescription:componentDescription options:options error:outError];
    if (!self) return nil;

    _width = 1.0f;
    AVAudioFormat *format = [[AVAudioFormat alloc] initStandardFormatWithSampleRate:44100 channels:2];
    NSError *busErr = nil;
    AUAudioUnitBus *inputBus  = [[AUAudioUnitBus alloc] initWithFormat:format error:&busErr];
    AUAudioUnitBus *outputBus = [[AUAudioUnitBus alloc] initWithFormat:format error:&busErr];
    _inputBusArray  = [[AUAudioUnitBusArray alloc] initWithAudioUnit:self busType:AUAudioUnitBusTypeInput  busses:@[inputBus]];
    _outputBusArray = [[AUAudioUnitBusArray alloc] initWithAudioUnit:self busType:AUAudioUnitBusTypeOutput busses:@[outputBus]];
    return self;
}

- (AUAudioUnitBusArray *)inputBusses  { return _inputBusArray; }
- (AUAudioUnitBusArray *)outputBusses { return _outputBusArray; }

- (AUInternalRenderBlock)internalRenderBlock {
    AirmedyStereoWidener * __weak weakSelf = self;
    return ^AUAudioUnitStatus(AudioUnitRenderActionFlags *actionFlags,
                               const AudioTimeStamp     *timestamp,
                               AVAudioFrameCount         frameCount,
                               NSInteger                 outputBusNumber,
                               AudioBufferList          *outputData,
                               const AURenderEvent      *realtimeEventListHead,
                               AURenderPullInputBlock    pullInputBlock) {
        AirmedyStereoWidener *strongSelf = weakSelf;
        if (!strongSelf || !pullInputBlock) return kAudioUnitErr_NoConnection;

        AudioUnitRenderActionFlags pullFlags = 0;
        AUAudioUnitStatus err = pullInputBlock(&pullFlags, timestamp, frameCount, 0, outputData);
        if (err != noErr) return err;
        if (outputData->mNumberBuffers < 2) return noErr; // mono: nothing to widen

        float w = strongSelf.width;
        float *L = (float *)outputData->mBuffers[0].mData;
        float *R = (float *)outputData->mBuffers[1].mData;
        if (!L || !R) return noErr;
        for (AVAudioFrameCount i = 0; i < frameCount; i++) {
            float l = L[i], r = R[i];
            float m = 0.5f * (l + r);
            float s = 0.5f * (l - r) * w;
            L[i] = m + s;
            R[i] = m - s;
        }
        return noErr;
    };
}

@end

// Registers AirmedyStereoWidener as an in-process AudioComponent exactly once.
static void EnsureStereoWidenerRegistered(void) {
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        AudioComponentDescription desc = {
            .componentType         = kAudioUnitType_Effect,
            .componentSubType      = 'awdn',
            .componentManufacturer = 'Amdy',
            .componentFlags        = 0,
            .componentFlagsMask    = 0,
        };
        [AUAudioUnit registerSubclass:[AirmedyStereoWidener class]
               asComponentDescription:desc
                                  name:@"Airmedy: Stereo Widener"
                               version:1];
    });
}

// Instantiates a fresh widener node. In-process registered components support
// synchronous instantiation (no XPC round-trip), so the throwing/async APIs
// are unnecessary here.
static AVAudioUnitEffect *MakeStereoWidenerNode(void) {
    EnsureStereoWidenerRegistered();
    AudioComponentDescription desc = {
        .componentType         = kAudioUnitType_Effect,
        .componentSubType      = 'awdn',
        .componentManufacturer = 'Amdy',
        .componentFlags        = 0,
        .componentFlagsMask    = 0,
    };
    return [[AVAudioUnitEffect alloc] initWithAudioComponentDescription:desc];
}

// ============================================================
// AirmedyDeck — one SFBAudioPlayer with its own persistent EQ.
//
// Two decks exist so tracks can overlap for crossfading:
// SFBAudioPlayer exposes a single serial decoder queue and cannot
// host two simultaneous sources, so each deck owns a full player
// (and AVAudioEngine); the decks mix at the CoreAudio device level.
// mainMixerNode.outputVolume is the per-deck crossfade gain stage,
// independent of user volume (output AU) and normalization
// (EQ globalGain).
// ============================================================

@interface AirmedyDeck : NSObject
@property (strong, nonatomic) SFBAudioPlayer     *sfbPlayer;
@property (strong, nonatomic) AVAudioUnitEQ      *equalizer;
@property (strong, nonatomic) AVAudioUnitEffect  *widener;
@property (assign, nonatomic) float               normPreampDB; // per-source normalization gain (dB)
@end

@implementation AirmedyDeck

- (instancetype)initWithDelegate:(id<SFBAudioPlayerDelegate>)delegate {
    self = [super init];
    if (!self) return nil;

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
    _widener = MakeStereoWidenerNode();

    _sfbPlayer = [[SFBAudioPlayer alloc] init];
    _sfbPlayer.delegate = delegate;

    // Insert EQ + widener between sourceNode and mainMixerNode.
    // On init, SFBAudioEngine connects: sourceNode → mainMixerNode.
    // We insert: sourceNode → EQ → widener → mainMixerNode.
    // Subsequent format changes are handled by reconfigureProcessingGraph:withFormat:.
    __weak AirmedyDeck *weakSelf = self;
    [_sfbPlayer modifyProcessingGraph:^(AVAudioEngine *engine) {
        AirmedyDeck *s = weakSelf;
        if (!s) return;
        AVAudioNode *src = s->_sfbPlayer.sourceNode;
        AVAudioMixerNode *mixer = engine.mainMixerNode;
        [engine disconnectNodeOutput:src bus:0];
        [engine attachNode:s->_equalizer];
        [engine attachNode:s->_widener];
        [engine connect:src to:s->_equalizer format:nil];
        [engine connect:s->_equalizer to:s->_widener format:nil];
        [engine connect:s->_widener to:mixer format:nil];
    }];

    return self;
}

// Build a PCM decoder for a file. SFBAudioPlayer's processing graph only
// accepts SFBPCMDecoding decoders, so DSD files (.dsf/.dff) — whose native
// decoder conforms to SFBDSDDecoding — must be wrapped in SFBDSDPCMDecoder
// (DSD->PCM conversion, compatible with any DAC). Non-DSD types fall through
// to nil so callers enqueue the URL and let the player auto-detect.
- (id<SFBPCMDecoding>)pcmDecoderForPath:(NSString *)path error:(NSError **)error {
    NSString *ext = path.pathExtension.lowercaseString;
    if (![[SFBDSDDecoder supportedPathExtensions] containsObject:ext]) {
        return nil;
    }
    return [[SFBDSDPCMDecoder alloc] initWithURL:[NSURL fileURLWithPath:path] error:error];
}

- (BOOL)enqueuePath:(NSString *)path forImmediatePlayback:(BOOL)immediate {
    NSError *err = nil;
    id<SFBPCMDecoding> dsd = [self pcmDecoderForPath:path error:&err];
    if (dsd) {
        [self.sfbPlayer enqueueDecoder:dsd forImmediatePlayback:immediate error:&err];
    } else if (!err) {
        [self.sfbPlayer enqueueURL:[NSURL fileURLWithPath:path] forImmediatePlayback:immediate error:&err];
    }
    if (err) {
        NSLog(@"[AirmedyDeck] Failed to enqueue %@: %@", path, err);
        return NO;
    }
    return YES;
}

// Stop playback and drop any queued decoders, leaving the deck idle.
- (void)reset {
    [self.sfbPlayer clearQueue];
    [self.sfbPlayer stop];
    self.sfbPlayer.mainMixerNode.outputVolume = 1.0f;
}

@end

// ============================================================
// AirmedyPlayer — controller over two decks. Routes transport,
// EQ, normalization and Now Playing; drives crossfades on a
// private serial queue (fadeQueue).
// ============================================================

@interface AirmedyPlayer : NSObject <SFBAudioPlayerDelegate>
@property (strong, nonatomic) NSArray<AirmedyDeck *> *decks;
@property (assign, nonatomic) NSUInteger       activeIndex;
@property (assign, nonatomic) float            volume;
@property (assign, nonatomic) BOOL             isPlaying;
@property (assign, nonatomic) NSTimeInterval   pausePosition;
@property (assign, nonatomic) double           crossfadeDuration; // seconds; 0 = off (gapless)
@property (assign, nonatomic) float            eqPreampDB; // user EQ preamp, global, composes with normPreampDB
@property (assign, nonatomic) BOOL             fading;
@property (strong, nonatomic) NSString        *preloadedPath;     // pending track in the idle deck
@property (strong, nonatomic) AirmedyDeck     *outgoingDeck;      // fading-out deck while fading
@property (strong, nonatomic) dispatch_queue_t fadeQueue;
@property (strong, nonatomic) dispatch_source_t fadeTimer;
@end

@implementation AirmedyPlayer

- (instancetype)init {
    self = [super init];
    if (!self) return nil;

    _volume        = 1.0f;
    _isPlaying     = NO;
    _pausePosition = 0.0;
    _activeIndex   = 0;
    _crossfadeDuration = 0.0;
    _fadeQueue = dispatch_queue_create("com.airmedy.player.fade", DISPATCH_QUEUE_SERIAL);

    _decks = @[
        [[AirmedyDeck alloc] initWithDelegate:self],
        [[AirmedyDeck alloc] initWithDelegate:self],
    ];

    return self;
}

- (AirmedyDeck *)activeDeck { return self.decks[self.activeIndex]; }
- (AirmedyDeck *)idleDeck   { return self.decks[self.activeIndex ^ 1]; }

// --- SFBAudioPlayerDelegate ---

// Called when audio format changes; reconnect the owning deck's EQ with the
// new format. SFBAudioEngine connects sourceNode → returned node with format.
// We connect returned node → mainMixerNode with format.
- (AVAudioNode *)audioPlayer:(SFBAudioPlayer *)audioPlayer
    reconfigureProcessingGraph:(AVAudioEngine *)engine
                    withFormat:(AVAudioFormat *)format
{
    AVAudioUnitEQ *eq = nil;
    AVAudioUnitEffect *widener = nil;
    for (AirmedyDeck *d in self.decks) {
        if (d.sfbPlayer == audioPlayer) { eq = d.equalizer; widener = d.widener; break; }
    }
    if (!eq) return engine.mainMixerNode;

    if ([engine.attachedNodes containsObject:eq]) {
        [engine disconnectNodeOutput:eq bus:0];
    } else {
        [engine attachNode:eq];
    }
    if (widener) {
        if ([engine.attachedNodes containsObject:widener]) {
            [engine disconnectNodeOutput:widener bus:0];
        } else {
            [engine attachNode:widener];
        }
        [engine connect:eq to:widener format:format];
        [engine connect:widener to:engine.mainMixerNode format:format];
    } else {
        [engine connect:eq to:engine.mainMixerNode format:format];
    }
    return eq;
}

// Fires when last sample is rendered — the true end of playback. Only the
// active deck's end is a track end for the app: during a crossfade the
// outgoing deck (no longer active) drains and must not advance the queue.
// If a next track was pre-queued (gapless), SFBAudioEngine is already playing
// it, so isPlaying stays YES. Only mark stopped if the engine actually stopped.
- (void)audioPlayer:(SFBAudioPlayer *)audioPlayer
   renderingComplete:(id<SFBPCMDecoding>)decoder
{
    if (audioPlayer != self.activeDeck.sfbPlayer || self.fading) {
        return;
    }
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
    // Keep isPlaying in sync if SFBAudioEngine transitions externally (e.g.
    // interruption). Only the active deck's state is the app's state.
    if (audioPlayer != self.activeDeck.sfbPlayer) return;
    self.isPlaying = (playbackState == SFBAudioPlayerPlaybackStatePlaying);
}

- (void)audioPlayer:(SFBAudioPlayer *)audioPlayer
    encounteredError:(NSError *)error
{
    NSLog(@"[AirmedyPlayer] Error: %@", error);
    if (audioPlayer == self.activeDeck.sfbPlayer) {
        self.isPlaying = NO;
    }
}

// --- Playback ---

- (void)play {
    SFBAudioPlayer *player = self.activeDeck.sfbPlayer;
    // Resume from the paused position. playReturningError: restarts the
    // AVAudioEngine and can reset the decoder to frame 0, so a paused track
    // must be resumed via -resume (per SFBAudioPlayer semantics).
    if (player.playbackState == SFBAudioPlayerPlaybackStatePaused) {
        [player resume];
        self.isPlaying = YES;
        [self updatePlaybackRate];
        return;
    }

    NSError *err = nil;
    [player playReturningError:&err];
    if (err) {
        NSLog(@"[AirmedyPlayer] play error: %@", err);
        return;
    }
    self.isPlaying = YES;
    [self updatePlaybackRate];
}

- (void)pause {
    self.pausePosition = [self currentPosition];
    [self.activeDeck.sfbPlayer pause];
    self.isPlaying = NO;
    [self updatePlaybackRate];
}

- (void)stop {
    dispatch_sync(self.fadeQueue, ^{
        [self completeFadeNow];
    });
    [self.activeDeck.sfbPlayer stop];
    self.isPlaying = NO;
    self.pausePosition = 0.0;
}

- (void)load:(NSString *)path {
    BOOL wasPlaying = self.isPlaying;
    self.isPlaying = NO;
    self.pausePosition = 0.0;

    // A hard load supersedes any fade and any pre-loaded next track.
    dispatch_sync(self.fadeQueue, ^{
        [self completeFadeNow];
        [self.idleDeck reset];
        self.preloadedPath = nil;
    });

    if (![self.activeDeck enqueuePath:path forImmediatePlayback:YES]) {
        return;
    }
    [self.activeDeck.sfbPlayer setVolume:_volume error:nil];

    if (wasPlaying) {
        [self play];
    }
}

- (void)enqueueNext:(NSString *)path {
    if (self.crossfadeDuration <= 0) {
        // Gapless: SFBAudioEngine auto-transitions within the active deck's
        // queue when formats match.
        [self.activeDeck enqueuePath:path forImmediatePlayback:NO];
        return;
    }

    // Crossfade: pre-load into the idle deck (stopped — the decoder is queued
    // for immediate playback and starts on playReturningError: at fade time).
    dispatch_sync(self.fadeQueue, ^{
        [self completeFadeNow];
        AirmedyDeck *deck = self.idleDeck;
        [deck reset];
        self.preloadedPath = nil;
        if ([deck enqueuePath:path forImmediatePlayback:YES]) {
            self.preloadedPath = path;
        }
    });
}

- (void)clearEnqueued {
    dispatch_sync(self.fadeQueue, ^{
        [self.activeDeck.sfbPlayer clearQueue];
        // While fading the "idle" deck is the outgoing one — leave it alone;
        // there is no pre-loaded track then anyway.
        if (!self.fading) {
            [self.idleDeck reset];
        }
        self.preloadedPath = nil;
    });
}

- (void)seek:(double)seconds {
    if ([self.activeDeck.sfbPlayer seekToTime:seconds]) {
        self.pausePosition = seconds;
    }
}

- (void)setVolume:(float)volume {
    _volume = volume;
    // User volume applies to both decks (output AU stage) so a mid-fade
    // change affects outgoing and incoming alike; the crossfade ramp rides
    // mainMixerNode.outputVolume independently.
    for (AirmedyDeck *d in self.decks) {
        [d.sfbPlayer setVolume:volume error:nil];
    }
}

- (double)currentPosition {
    if (!self.isPlaying) return self.pausePosition;
    NSTimeInterval t = self.activeDeck.sfbPlayer.currentTime;
    return (t > 0) ? t : self.pausePosition;
}

// --- Crossfade (all state mutated on fadeQueue) ---

- (void)setCrossfadeDurationSec:(double)seconds {
    dispatch_sync(self.fadeQueue, ^{
        self.crossfadeDuration = seconds;
    });
}

- (BOOL)beginCrossfade:(double)durationSec nextPreampDB:(double)db {
    __block BOOL ok = NO;
    dispatch_sync(self.fadeQueue, ^{
        [self completeFadeNow];
        if (self.preloadedPath == nil) return;

        AirmedyDeck *from = self.activeDeck;
        AirmedyDeck *to   = self.idleDeck;

        // Per-source normalization: the incoming deck gets the incoming
        // track's gain; the outgoing deck keeps its own until it is stopped.
        to.normPreampDB = (float)db;
        [self applyGlobalGainForDeck:to];
        [to.sfbPlayer setVolume:self.volume error:nil];
        to.sfbPlayer.mainMixerNode.outputVolume = 0.0f;

        NSError *err = nil;
        if (![to.sfbPlayer playReturningError:&err]) {
            NSLog(@"[AirmedyPlayer] crossfade play error: %@", err);
            return;
        }

        // Swap at fade start so position/status/preamp immediately target
        // the incoming deck.
        self.activeIndex ^= 1;
        self.preloadedPath = nil;
        self.outgoingDeck = from;
        self.fading = YES;
        self.isPlaying = YES;
        self.pausePosition = 0.0;

        [self startFadeTimer:durationSec from:from to:to];
        ok = YES;
    });
    return ok;
}

- (void)finishCrossfade {
    dispatch_sync(self.fadeQueue, ^{
        [self completeFadeNow];
    });
}

// Crossfade-mode fallback for a natural track end that raced past the fade
// window: start the pre-loaded deck at full level and swap. No-op (success)
// when crossfade is off — SFBAudioEngine already auto-transitioned.
- (BOOL)startPreloadedHard {
    __block BOOL ok = NO;
    dispatch_sync(self.fadeQueue, ^{
        if (self.crossfadeDuration <= 0) { ok = YES; return; }
        [self completeFadeNow];
        if (self.preloadedPath == nil) return;

        AirmedyDeck *from = self.activeDeck;
        AirmedyDeck *to   = self.idleDeck;
        [to.sfbPlayer setVolume:self.volume error:nil];
        to.sfbPlayer.mainMixerNode.outputVolume = 1.0f;

        NSError *err = nil;
        if (![to.sfbPlayer playReturningError:&err]) {
            NSLog(@"[AirmedyPlayer] start preloaded error: %@", err);
            return;
        }
        [from reset];

        self.activeIndex ^= 1;
        self.preloadedPath = nil;
        self.isPlaying = YES;
        self.pausePosition = 0.0;
        ok = YES;
    });
    return ok;
}

// Equal-power ramp on each deck's mainMixerNode, 20ms steps. Runs on fadeQueue.
- (void)startFadeTimer:(double)durationSec from:(AirmedyDeck *)from to:(AirmedyDeck *)to {
    const double stepSec = 0.02;
    __block double t = 0.0;
    double step = stepSec / MAX(durationSec, stepSec);

    dispatch_source_t timer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0, 0, self.fadeQueue);
    dispatch_source_set_timer(timer,
                              dispatch_time(DISPATCH_TIME_NOW, (int64_t)(stepSec * NSEC_PER_SEC)),
                              (uint64_t)(stepSec * NSEC_PER_SEC),
                              (uint64_t)(0.005 * NSEC_PER_SEC));
    __weak AirmedyPlayer *weakSelf = self;
    dispatch_source_set_event_handler(timer, ^{
        AirmedyPlayer *s = weakSelf;
        if (!s) return;
        t += step;
        if (t >= 1.0) {
            [s completeFadeNow];
            return;
        }
        float x = (float)(t * M_PI_2);
        from.sfbPlayer.mainMixerNode.outputVolume = cosf(x);
        to.sfbPlayer.mainMixerNode.outputVolume   = sinf(x);
    });
    self.fadeTimer = timer;
    dispatch_resume(timer);
}

// Force-completes the running fade: outgoing deck stopped and reset, incoming
// snaps to full level. Must run on fadeQueue. No-op when not fading.
- (void)completeFadeNow {
    if (self.fadeTimer) {
        dispatch_source_cancel(self.fadeTimer);
        self.fadeTimer = nil;
    }
    if (!self.fading) return;

    if (self.outgoingDeck) {
        [self.outgoingDeck reset];
        self.outgoingDeck = nil;
    }
    self.activeDeck.sfbPlayer.mainMixerNode.outputVolume = 1.0f;
    self.fading = NO;
}

// --- EQ ---

// Band parameters and bypass apply to both decks so the pre-loaded (incoming)
// deck always carries the current EQ when a fade starts.
- (void)setEQBandIndex:(int)index frequency:(double)freq gain:(double)gain bandwidth:(double)bw {
    for (AirmedyDeck *d in self.decks) {
        if (index < 0 || index >= (int)d.equalizer.bands.count) continue;
        AVAudioUnitEQFilterParameters *b = d.equalizer.bands[index];
        b.frequency = (float)freq;
        b.gain      = (float)gain;
        b.bandwidth = (float)bw;
    }
}

- (void)setEQEnabled:(BOOL)enabled {
    // Bypass each band individually rather than the whole AVAudioUnitEQ unit:
    // unit-level bypass also silences globalGain, which would break normalization
    // (setPreampGainDB) whenever the user's EQ is toggled off.
    for (AirmedyDeck *d in self.decks) {
        for (AVAudioUnitEQFilterParameters *b in d.equalizer.bands) {
            b.bypass = !enabled;
        }
    }
}

// --- Normalization + user EQ preamp ---

// globalGain is applied by AVAudioUnitEQ after all bands, on the same
// persistent node used for EQ — independent of per-band bypass/eqEnabled.
// It carries the sum of two independently-tracked gains: the automatic
// normalization gain (per-deck/source, normPreampDB) and the user's global
// EQ preamp (eqPreampDB). Combining in dB (== multiplying linear gains) lets
// either be changed without disturbing the other.
- (void)applyGlobalGainForDeck:(AirmedyDeck *)deck {
    deck.equalizer.globalGain = deck.normPreampDB + self.eqPreampDB;
}

// Active deck only: normalization gain is per-source (the incoming deck
// receives its own track's gain in beginCrossfade:).
- (void)setPreampGainDB:(double)db {
    self.activeDeck.normPreampDB = (float)db;
    [self applyGlobalGainForDeck:self.activeDeck];
}

// User EQ preamp is global (same for both decks) and persists across track
// changes and crossfades, unlike normalization gain.
- (void)setEQPreampDB:(double)db {
    self.eqPreampDB = (float)db;
    for (AirmedyDeck *d in self.decks) {
        [self applyGlobalGainForDeck:d];
    }
}

// --- Stereo width ---

- (void)setStereoWidthPercent:(double)pct {
    float w = (float)(pct / 100.0);
    for (AirmedyDeck *d in self.decks) {
        AirmedyStereoWidener *au = (AirmedyStereoWidener *)d.widener.AUAudioUnit;
        au.width = w;
    }
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

    NSImage *image = nil;
    if (artworkPath.length > 0) {
        image = [[NSImage alloc] initWithContentsOfFile:artworkPath];
    }
    if (!image) {
        // No artwork for this track. Control Center keeps the previous track's
        // artwork when the key is simply omitted, so overwrite it with a blank
        // 1x1 transparent image to force a clear.
        image = [[NSImage alloc] initWithSize:NSMakeSize(1, 1)];
    }
    MPMediaItemArtwork *artwork = [[MPMediaItemArtwork alloc]
        initWithBoundsSize:image.size
            requestHandler:^NSImage *(CGSize size) { return image; }];
    info[MPMediaItemPropertyArtwork] = artwork;

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

void SetPreampGainPlayer(void *playerPtr, double db) {
    [(__bridge AirmedyPlayer *)playerPtr setPreampGainDB:db];
}

void SetEQPreampPlayer(void *playerPtr, double db) {
    [(__bridge AirmedyPlayer *)playerPtr setEQPreampDB:db];
}

void SetStereoWidthPlayer(void *playerPtr, double pct) {
    [(__bridge AirmedyPlayer *)playerPtr setStereoWidthPercent:pct];
}

void EnqueueNextPlayer(void *playerPtr, const char *path) {
    NSString *p = [NSString stringWithUTF8String:path];
    [(__bridge AirmedyPlayer *)playerPtr enqueueNext:p];
}

void ClearEnqueuedPlayer(void *playerPtr) {
    [(__bridge AirmedyPlayer *)playerPtr clearEnqueued];
}

void SetCrossfadeDurationPlayer(void *playerPtr, double seconds) {
    [(__bridge AirmedyPlayer *)playerPtr setCrossfadeDurationSec:seconds];
}

int BeginCrossfadePlayer(void *playerPtr, double durationSec, double nextPreampDB) {
    return [(__bridge AirmedyPlayer *)playerPtr beginCrossfade:durationSec nextPreampDB:nextPreampDB] ? 1 : 0;
}

void FinishCrossfadePlayer(void *playerPtr) {
    [(__bridge AirmedyPlayer *)playerPtr finishCrossfade];
}

int StartPreloadedPlayer(void *playerPtr) {
    return [(__bridge AirmedyPlayer *)playerPtr startPreloadedHard] ? 1 : 0;
}
