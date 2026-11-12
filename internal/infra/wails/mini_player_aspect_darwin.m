#import <AppKit/AppKit.h>
#include "mini_player_aspect_darwin.h"

void LockMiniPlayerAspect(void *window, bool expanded) {
    if (window == nil) return;
    NSWindow *nsWindow = (NSWindow *)window;
    [nsWindow setContentAspectRatio:NSMakeSize(1, expanded ? 2 : 1)];
}

void SetMiniPlayerSizeNoAnimation(void *window, int width, int height) {
    if (window == nil) return;
    NSWindow *nsWindow = (NSWindow *)window;
    NSRect frame = [nsWindow frame];
    CGFloat top = NSMaxY(frame);
    frame.size = NSMakeSize(width, height);
    frame.origin.y = top - height;
    [nsWindow setFrame:frame display:YES animate:NO];
}
