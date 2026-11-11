#import <AppKit/AppKit.h>
#import <QuartzCore/QuartzCore.h>
#include <string.h>
#include "tray_pill_darwin.h"

extern void goTrayPillClicked(void);
extern void goTrayPillMenu(void);
extern void goTrayPillShow(void);
extern void goTrayPillQuit(void);

static NSStatusItem *item;
static NSPoint lastClickLocation;
static NSTimeInterval lastClickTime;
static NSColor *barStartColor;
static NSColor *barEndColor;
static NSColor *barFromStartColor;
static NSColor *barFromEndColor;
static CGFloat barColorProgress = 1;
static int trayTheme;

static NSAppearance *PillAppearance(void) {
    if (trayTheme == 1) return [NSAppearance appearanceNamed:NSAppearanceNameAqua];
    if (trayTheme == 2 || trayTheme == 3) return [NSAppearance appearanceNamed:NSAppearanceNameDarkAqua];
    NSString *style = [NSUserDefaults.standardUserDefaults stringForKey:@"AppleInterfaceStyle"];
    return [NSAppearance appearanceNamed:[style isEqualToString:@"Dark"] ? NSAppearanceNameDarkAqua : NSAppearanceNameAqua];
}

static NSColor *PillColor(const char *hex) {
    if (!hex || strlen(hex) != 7 || hex[0] != '#') return nil;
    unsigned value = 0;
    for (int i = 1; i < 7; i++) {
        char c = hex[i];
        unsigned digit = c >= '0' && c <= '9' ? c - '0' :
            c >= 'a' && c <= 'f' ? c - 'a' + 10 :
            c >= 'A' && c <= 'F' ? c - 'A' + 10 : 16;
        if (digit == 16) return nil;
        value = (value << 4) | digit;
    }
    return [NSColor colorWithSRGBRed:((value >> 16) & 255) / 255.0
        green:((value >> 8) & 255) / 255.0 blue:(value & 255) / 255.0 alpha:1];
}

static CGFloat PillLuminance(NSColor *color) {
    color = [color colorUsingColorSpace:NSColorSpace.deviceRGBColorSpace];
    CGFloat r = color.redComponent, g = color.greenComponent, b = color.blueComponent;
    r = r <= 0.04045 ? r / 12.92 : pow((r + 0.055) / 1.055, 2.4);
    g = g <= 0.04045 ? g / 12.92 : pow((g + 0.055) / 1.055, 2.4);
    b = b <= 0.04045 ? b / 12.92 : pow((b + 0.055) / 1.055, 2.4);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

static NSColor *PillReadableColor(NSColor *accent, NSColor *foreground, NSColor *background) {
    CGFloat backdrop = PillLuminance(background);
    BOOL lighter = PillLuminance(foreground) > backdrop;
    for (int step = 0; step <= 10; step++) {
        NSColor *candidate = [accent blendedColorWithFraction:step / 10.0 ofColor:foreground];
        CGFloat value = PillLuminance(candidate);
        CGFloat contrast = (MAX(value, backdrop) + 0.05) / (MIN(value, backdrop) + 0.05);
        if (contrast >= 3 && (lighter ? value > backdrop : value < backdrop)) return candidate;
    }
    return foreground;
}

static NSColor *PillBarColor(NSColor *from, NSColor *to, NSColor *foreground, NSColor *background) {
    NSColor *start = PillReadableColor(from ?: foreground, foreground, background);
    NSColor *end = PillReadableColor(to ?: foreground, foreground, background);
    return [start blendedColorWithFraction:barColorProgress ofColor:end];
}

@interface AirmedyPillView : NSView {
    CGFloat barHeights[5];
    CGFloat barFrom[5];
    CGFloat barTargets[5];
    NSInteger barStep;
    CGFloat pauseFrom[5];
    CGFloat pauseProgress;
	NSImage *previousArtwork;
	CGFloat artworkFadeProgress;
	NSString *previousTitle;
	CGFloat previousTitleOffset;
	CGFloat titleSlideProgress;
	NSTimeInterval titleSlideStartedAt;
	CADisplayLink *titleDisplayLink;
}
@property (nonatomic, copy) NSString *title;
@property (nonatomic, strong) NSImage *artwork;
@property (nonatomic) BOOL playing;
@property (nonatomic) CGFloat offset;
@property (nonatomic) NSInteger pauseTicks;
@property (nonatomic, strong) NSTimer *timer;
- (void)refreshImage;
- (void)beginArtworkFade;
- (void)beginTitleSlide;
- (void)stopTitleDisplayLink;
@end

@implementation AirmedyPillView
- (instancetype)initWithFrame:(NSRect)frame {
    if ((self = [super initWithFrame:frame])) {
        _title = @"Airmedy";
        _pauseTicks = 300;
        pauseProgress = 1;
		artworkFadeProgress = 1;
		titleSlideProgress = 1;
        barStep = 18;
        for (int i = 0; i < 5; i++) barHeights[i] = 2;
        self.timer = [NSTimer timerWithTimeInterval:1.0/60.0 target:self selector:@selector(tick:) userInfo:nil repeats:YES];
        [NSRunLoop.mainRunLoop addTimer:self.timer forMode:NSRunLoopCommonModes];
    }
    return self;
}
- (void)dealloc {
	[previousArtwork release];
	[previousTitle release];
	[super dealloc];
}
- (void)beginArtworkFade {
	[previousArtwork release];
	previousArtwork = [self.artwork retain];
	artworkFadeProgress = 0;
}
- (void)beginTitleSlide {
	[previousTitle release];
	previousTitle = [self.title copy];
	previousTitleOffset = self.offset;
	titleSlideProgress = 0;
	titleSlideStartedAt = NSProcessInfo.processInfo.systemUptime;
	if (!titleDisplayLink && item.button.window) {
		titleDisplayLink = [[item.button.window displayLinkWithTarget:self selector:@selector(titleFrame:)] retain];
		[titleDisplayLink addToRunLoop:NSRunLoop.mainRunLoop forMode:NSRunLoopCommonModes];
	}
}
- (void)titleFrame:(CADisplayLink *)link {
	titleSlideProgress = MIN(1, (NSProcessInfo.processInfo.systemUptime - titleSlideStartedAt) / 0.3);
	if (titleSlideProgress == 1) {
		[previousTitle release];
		previousTitle = nil;
		[self stopTitleDisplayLink];
	}
	if (link) [self refreshImage];
}
- (void)stopTitleDisplayLink {
	[titleDisplayLink invalidate];
	[titleDisplayLink release];
	titleDisplayLink = nil;
}
- (void)setPlaying:(BOOL)playing {
    if (_playing && !playing) {
        for (int i = 0; i < 5; i++) pauseFrom[i] = barHeights[i];
        pauseProgress = 0;
    }
    if (!_playing && playing) barStep = 18;
    _playing = playing;
}
- (void)tick:(NSTimer *)timer {
    BOOL resized = !NSEqualSizes(self.bounds.size, item.button.bounds.size);
    if (resized) self.frame = item.button.bounds;
    CGFloat height = NSHeight(self.bounds);
    BOOL settling = !self.playing && pauseProgress < 1;
    if (self.playing) {
        if (barStep >= 18) {
            for (int i = 0; i < 5; i++) {
                barFrom[i] = barHeights[i];
                barTargets[i] = height * 0.62 * (35 + arc4random_uniform(66)) / 100.0;
            }
            barStep = 0;
        }
        CGFloat progress = ++barStep / 18.0;
        CGFloat eased = progress * progress * (3 - 2 * progress);
        for (int i = 0; i < 5; i++) {
            barHeights[i] = barFrom[i] + (barTargets[i] - barFrom[i]) * eased;
        }
    } else if (settling) {
        pauseProgress = MIN(1, pauseProgress + 1.0 / 24.0);
        CGFloat eased = pauseProgress * pauseProgress * (3 - 2 * pauseProgress);
        for (int i = 0; i < 5; i++) barHeights[i] = pauseFrom[i] + (2 - pauseFrom[i]) * eased;
    }
	BOOL fadingArtwork = artworkFadeProgress < 1;
	if (fadingArtwork) {
		artworkFadeProgress = MIN(1, artworkFadeProgress + 1.0 / 24.0);
		if (artworkFadeProgress == 1) {
			[previousArtwork release];
			previousArtwork = nil;
		}
	}
	BOOL titleSliding = titleSlideProgress < 1;
	if (titleSliding && !titleDisplayLink) [self titleFrame:nil];
	BOOL colorChanging = barColorProgress < 1;
	if (colorChanging) {
		barColorProgress = MIN(1, barColorProgress + 1.0 / 90.0);
		if (barColorProgress == 1) {
			[barFromStartColor release];
			[barFromEndColor release];
			barFromStartColor = nil;
			barFromEndColor = nil;
		}
	}
    CGFloat titleWidth = NSWidth(self.bounds) - 30 - (16 + height * 0.7);
    CGFloat fontSize = MIN(11, MAX(6, height * 0.5));
    NSDictionary *attrs = @{NSFontAttributeName: [NSFont systemFontOfSize:fontSize weight:NSFontWeightMedium]};
    CGFloat textWidth = [self.title sizeWithAttributes:attrs].width;
    if (!titleSliding && textWidth > titleWidth) {
        if (self.pauseTicks > 0) self.pauseTicks--;
        else {
            self.offset += 20.0 / 60.0;
            if (self.offset >= textWidth + 32) {
                self.offset = 0;
                self.pauseTicks = 300;
            }
        }
    } else self.offset = 0;
    if (resized || self.playing || settling || fadingArtwork || (titleSliding && !titleDisplayLink) || colorChanging || textWidth > titleWidth) [self refreshImage];
}
- (void)refreshImage {
    NSSize size = self.bounds.size;
    NSBitmapImageRep *rep = [[NSBitmapImageRep alloc] initWithBitmapDataPlanes:NULL
        pixelsWide:(NSInteger)(size.width * 2) pixelsHigh:(NSInteger)(size.height * 2)
        bitsPerSample:8 samplesPerPixel:4 hasAlpha:YES isPlanar:NO
        colorSpaceName:NSDeviceRGBColorSpace bytesPerRow:0 bitsPerPixel:0];
    rep.size = size;
    NSGraphicsContext *context = [NSGraphicsContext graphicsContextWithBitmapImageRep:rep];
    [NSGraphicsContext saveGraphicsState];
    [NSGraphicsContext setCurrentContext:context];
    [PillAppearance() performAsCurrentDrawingAppearance:^{
        [self drawRect:self.bounds];
    }];
    [context flushGraphics];
    [NSGraphicsContext restoreGraphicsState];
    NSImage *image = [[NSImage alloc] initWithSize:size];
    [image addRepresentation:rep];
    item.button.image = image;
    [image release];
    [rep release];
}
- (void)drawArtwork:(NSImage *)artwork inRect:(NSRect)rect foreground:(NSColor *)foreground opacity:(CGFloat)opacity {
	[NSGraphicsContext saveGraphicsState];
	[[NSBezierPath bezierPathWithRoundedRect:rect xRadius:3 yRadius:3] addClip];
	CGContextRef context = NSGraphicsContext.currentContext.CGContext;
	CGContextSetAlpha(context, opacity);
	CGContextBeginTransparencyLayer(context, NULL);
	if (artwork) {
		[artwork drawInRect:rect];
	} else {
		[[foreground colorWithAlphaComponent:0.12] setFill];
		[NSBezierPath fillRect:rect];
		CGFloat fontSize = NSHeight(rect) * 0.8;
		NSDictionary *attrs = @{NSFontAttributeName: [NSFont systemFontOfSize:fontSize], NSForegroundColorAttributeName: foreground};
		NSString *symbol = @"♫";
		NSSize symbolSize = [symbol sizeWithAttributes:attrs];
		[symbol drawAtPoint:NSMakePoint(NSMidX(rect) - symbolSize.width / 2, NSMidY(rect) - symbolSize.height / 2) withAttributes:attrs];
	}
	CGContextEndTransparencyLayer(context);
	[NSGraphicsContext restoreGraphicsState];
}
- (void)drawRect:(NSRect)dirtyRect {
    NSRect bounds = self.bounds;
    CGFloat midY = NSMidY(bounds);
    CGFloat height = NSHeight(bounds);
    NSRect pillRect = bounds;
    CGFloat artSize = height * 0.7;
    CGFloat titleX = 16 + artSize;
    CGFloat titleWidth = NSWidth(bounds) - 30 - titleX;
    NSColor *foreground = NSColor.labelColor;
    NSColor *background = trayTheme == 3 ? NSColor.blackColor : [NSColor.controlBackgroundColor colorWithAlphaComponent:0.9];
    [background setFill];
    [[NSBezierPath bezierPathWithRoundedRect:pillRect xRadius:NSHeight(pillRect)/2 yRadius:NSHeight(pillRect)/2] fill];
    NSRect artRect = NSMakeRect(9, midY - artSize/2, artSize, artSize);
	if (artworkFadeProgress < 1) [self drawArtwork:previousArtwork inRect:artRect foreground:foreground opacity:1];
	[self drawArtwork:self.artwork inRect:artRect foreground:foreground opacity:artworkFadeProgress];
    [NSGraphicsContext saveGraphicsState];
    CGFloat fontSize = MIN(11, MAX(6, height * 0.5));
    NSFont *font = [NSFont systemFontOfSize:fontSize weight:NSFontWeightMedium];
    NSSize textSize = [self.title sizeWithAttributes:@{NSFontAttributeName: font}];
    CGFloat textY = midY - textSize.height / 2;
    [[NSBezierPath bezierPathWithRect:NSMakeRect(titleX, textY, titleWidth, textSize.height)] addClip];
    CGFloat slide = titleSlideProgress * titleSlideProgress * (3 - 2 * titleSlideProgress);
    for (int i = 0; i < (previousTitle ? 2 : 1); i++) {
        BOOL outgoing = previousTitle && i == 0;
        NSString *title = outgoing ? previousTitle : self.title;
        CGFloat offset = outgoing ? previousTitleOffset : self.offset;
        CGFloat shift = outgoing ? -titleWidth * slide : previousTitle ? titleWidth * (1 - slide) : 0;
        CGFloat opacity = outgoing ? 1 - 0.5 * slide : previousTitle ? 0.5 + 0.5 * slide : 1;
        NSDictionary *attrs = @{NSFontAttributeName: font, NSForegroundColorAttributeName: [foreground colorWithAlphaComponent:opacity]};
        CGFloat width = [title sizeWithAttributes:attrs].width;
        [NSGraphicsContext saveGraphicsState];
        [[NSBezierPath bezierPathWithRect:NSMakeRect(titleX + shift, textY, titleWidth, textSize.height)] addClip];
        [title drawAtPoint:NSMakePoint(titleX + shift - offset, textY) withAttributes:attrs];
        if (width > titleWidth) [title drawAtPoint:NSMakePoint(titleX + shift - offset + width + 32, textY) withAttributes:attrs];
        [NSGraphicsContext restoreGraphicsState];
    }
    [NSGraphicsContext restoreGraphicsState];
    NSBezierPath *bars = [NSBezierPath bezierPath];
    for (int i = 0; i < 5; i++) {
        CGFloat barHeight = barHeights[i];
        [bars appendBezierPath:[NSBezierPath bezierPathWithRoundedRect:NSMakeRect(NSWidth(bounds)-24+i*3, midY-barHeight/2, 2, barHeight) xRadius:1 yRadius:1]];
    }
    if (barStartColor || barEndColor || barFromStartColor || barFromEndColor) {
        [NSGraphicsContext saveGraphicsState];
        [bars addClip];
        NSGradient *gradient = [[NSGradient alloc] initWithStartingColor:PillBarColor(barFromStartColor, barStartColor, foreground, background)
            endingColor:PillBarColor(barFromEndColor, barEndColor, foreground, background)];
        [gradient drawInRect:NSMakeRect(NSWidth(bounds)-24, midY-height/2, 14, height) angle:0];
        [gradient release];
        [NSGraphicsContext restoreGraphicsState];
    } else {
        [foreground setFill];
        [bars fill];
    }
}
@end

@interface AirmedyPillTarget : NSObject
- (void)clicked:(id)sender;
- (void)show:(id)sender;
- (void)quit:(id)sender;
@end
@implementation AirmedyPillTarget
- (void)clicked:(id)sender {
    if (NSApp.currentEvent.type == NSEventTypeRightMouseUp) {
        goTrayPillMenu();
        return;
    }
    lastClickLocation = NSEvent.mouseLocation;
    lastClickTime = NSDate.timeIntervalSinceReferenceDate;
    goTrayPillClicked();
}
- (void)show:(id)sender { goTrayPillShow(); }
- (void)quit:(id)sender { goTrayPillQuit(); }
@end

static AirmedyPillView *pill;
static AirmedyPillTarget *target;
static NSString *lastArtworkPath;
static NSString *lastTrackID;

void TrayPillCreate(bool showPill, const void *icon, int iconLength) {
    if (item) return;
    item = [NSStatusBar.systemStatusBar statusItemWithLength:showPill ? 162 : NSVariableStatusItemLength];
    if (showPill) {
        pill = [[AirmedyPillView alloc] initWithFrame:item.button.bounds];
    } else {
        NSData *data = [NSData dataWithBytes:icon length:iconLength];
        NSImage *image = [[NSImage alloc] initWithData:data];
        image.size = NSMakeSize(18, 18);
        image.template = YES;
        item.button.image = image;
        [image release];
    }
    target = [AirmedyPillTarget new];
    item.button.imagePosition = NSImageOnly;
    item.button.imageScaling = NSImageScaleNone;
    item.button.target = target;
    item.button.action = @selector(clicked:);
    [item.button sendActionOn:NSEventMaskLeftMouseUp | NSEventMaskRightMouseUp];
    item.button.toolTip = @"Airmedy";
    [pill refreshImage];
}
void TrayPillShowMenu(const char *showLabel, const char *quitLabel) {
    if (!item || !target) return;
    NSMenu *menu = [[NSMenu alloc] initWithTitle:@""];
    NSMenuItem *show = [menu addItemWithTitle:[NSString stringWithUTF8String:showLabel] action:@selector(show:) keyEquivalent:@""];
    show.target = target;
    NSMenuItem *quit = [menu addItemWithTitle:[NSString stringWithUTF8String:quitLabel] action:@selector(quit:) keyEquivalent:@""];
    quit.target = target;
    NSRect frame = [item.button.window convertRectToScreen:[item.button convertRect:item.button.bounds toView:nil]];
    [menu popUpMenuPositioningItem:nil atLocation:NSMakePoint(NSMinX(frame), NSMinY(frame) - 8) inView:nil];
    [menu release];
}
void TrayPillUpdate(const char *title, const char *artworkPath, const char *trackID, const char *vibrant, const char *muted, bool playing) {
    if (!pill) return;
    NSString *nextTitle = title && title[0] ? [NSString stringWithUTF8String:title] : @"Airmedy";
    NSString *nextTrackID = trackID && trackID[0] ? [NSString stringWithUTF8String:trackID] : @"";
    if (lastTrackID && ![nextTrackID isEqualToString:lastTrackID]) {
        [pill beginArtworkFade];
        [pill beginTitleSlide];
    }
    if (![nextTitle isEqualToString:pill.title]) {
        pill.title = nextTitle;
        pill.offset = 0;
        pill.pauseTicks = 300;
    }
    NSString *nextPath = artworkPath && artworkPath[0] ? [NSString stringWithUTF8String:artworkPath] : @"";
	[lastTrackID release];
	lastTrackID = [nextTrackID copy];
    if (![nextPath isEqualToString:lastArtworkPath]) {
        NSImage *artwork = nextPath.length ? [[NSImage alloc] initWithContentsOfFile:nextPath] : nil;
        pill.artwork = artwork;
        [artwork release];
        [lastArtworkPath release];
        lastArtworkPath = [nextPath copy];
    }
    NSColor *nextStart = PillColor(vibrant);
    NSColor *nextEnd = PillColor(muted);
    if (!((barStartColor == nextStart || [barStartColor isEqual:nextStart]) &&
          (barEndColor == nextEnd || [barEndColor isEqual:nextEnd]))) {
        [PillAppearance() performAsCurrentDrawingAppearance:^{
            NSColor *foreground = NSColor.labelColor;
            NSColor *background = NSColor.controlBackgroundColor;
            NSColor *fromStart = [PillBarColor(barFromStartColor, barStartColor, foreground, background) retain];
            NSColor *fromEnd = [PillBarColor(barFromEndColor, barEndColor, foreground, background) retain];
            [barFromStartColor release];
            [barFromEndColor release];
            barFromStartColor = fromStart;
            barFromEndColor = fromEnd;
        }];
        [barStartColor release];
        [barEndColor release];
        barStartColor = [nextStart retain];
        barEndColor = [nextEnd retain];
        barColorProgress = 0;
    }
    pill.playing = playing;
    [pill refreshImage];
}
void TrayPillSetTheme(const char *theme) {
    trayTheme = strcmp(theme, "light") == 0 ? 1 : strcmp(theme, "dark") == 0 ? 2 : strcmp(theme, "black") == 0 ? 3 : 0;
    if (pill) [pill refreshImage];
}
void TrayPillRefreshTheme(void) {
    if (pill) [pill refreshImage];
}
void TrayPillPosition(int popupWidth, int *x, int *y) {
    NSRect frame = [item.button.window convertRectToScreen:[item.button convertRect:item.button.bounds toView:nil]];
    NSScreen *clickedScreen = item.button.window.screen;
    for (NSScreen *screen in NSScreen.screens) {
        if (NSPointInRect(lastClickLocation, screen.frame)) {
            clickedScreen = screen;
            break;
        }
    }
    CGFloat centerX = NSMidX(frame);
    if (clickedScreen != item.button.window.screen) {
        centerX = NSMaxX(clickedScreen.frame) - (NSMaxX(item.button.window.screen.frame) - centerX);
    }
    NSRect visible = clickedScreen.visibleFrame;
    CGFloat menuHeight = NSMaxY(clickedScreen.frame) - NSMaxY(visible);
    if (menuHeight <= 0) menuHeight = NSHeight(item.button.window.frame);
    *x = (int)MAX(NSMinX(visible), MIN(centerX - popupWidth / 2, NSMaxX(visible) - popupWidth));
    *y = (int)(NSMaxY(NSScreen.screens.firstObject.frame) - NSMaxY(clickedScreen.frame) + menuHeight + 4);
}
bool TrayPillPointerOverItem(void) {
    return item && item.button.window &&
        (NSDate.timeIntervalSinceReferenceDate - lastClickTime < 0.3 ||
         NSPointInRect(NSEvent.mouseLocation, item.button.window.frame));
}
void TrayPillDestroy(void) {
    [pill.timer invalidate];
    [pill stopTitleDisplayLink];
    [NSStatusBar.systemStatusBar removeStatusItem:item];
    [pill release];
    [target release];
    pill = nil;
    target = nil;
    item = nil;
    [lastArtworkPath release];
    lastArtworkPath = nil;
	[lastTrackID release];
	lastTrackID = nil;
    [barStartColor release];
    [barEndColor release];
    [barFromStartColor release];
    [barFromEndColor release];
    barStartColor = nil;
    barEndColor = nil;
    barFromStartColor = nil;
    barFromEndColor = nil;
    barColorProgress = 1;
    lastClickTime = 0;
}
