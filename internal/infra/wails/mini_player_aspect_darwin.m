#import <AppKit/AppKit.h>
#import <WebKit/WebKit.h>

#include "mini_player_aspect_darwin.h"

static WKWebView *miniPlayerWebView(NSView *view) {
    if ([view isKindOfClass:[WKWebView class]]) return (WKWebView *)view;
    for (NSView *subview in view.subviews) {
        WKWebView *webView = miniPlayerWebView(subview);
        if (webView != nil) return webView;
    }
    return nil;
}

void LockMiniPlayerAspect(void *window, bool expanded) {
    if (window == nil) return;
    NSWindow *nsWindow = (NSWindow *)window;
    [nsWindow setContentAspectRatio:NSMakeSize(1, expanded ? 2 : 1)];

    // Wails makes translucent-backdrop WKWebViews transparent. During live
    // resize that exposes AppKit's light visual-effect view before WebKit draws
    // the next artwork frame, so make this artwork-only window self-backed.
    WKWebView *webView = miniPlayerWebView(nsWindow.contentView);
    if (webView == nil) return;
    NSColor *backingColor = [NSColor colorWithRed:10.0 / 255.0
                                             green:10.0 / 255.0
                                              blue:10.0 / 255.0
                                             alpha:1.0];
    [webView setValue:@YES forKey:@"drawsBackground"];
    [webView setValue:backingColor forKey:@"backgroundColor"];
    [webView setWantsLayer:YES];
    [webView setLayerContentsRedrawPolicy:NSViewLayerContentsRedrawDuringViewResize];
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
