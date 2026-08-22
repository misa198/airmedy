#include <gtk/gtk.h>
#ifdef GDK_WINDOWING_X11
#include <gdk/x11/gdkx.h>
#include <X11/Xutil.h>
#endif

#include "mini_player_aspect_linux.h"

void LockMiniPlayerSquare(void *window) {
#ifdef GDK_WINDOWING_X11
    GtkWindow *gtkWindow = GTK_WINDOW(window);
    GdkSurface *surface = gtk_native_get_surface(GTK_NATIVE(gtkWindow));
    if (surface == NULL || !GDK_IS_X11_SURFACE(surface)) return;

    GdkDisplay *gdkDisplay = gdk_surface_get_display(surface);
    Display *display = gdk_x11_display_get_xdisplay(GDK_X11_DISPLAY(gdkDisplay));
    Window xid = gdk_x11_surface_get_xid(GDK_X11_SURFACE(surface));
    XSizeHints hints = {0};
    long supplied = 0;
    XGetWMNormalHints(display, xid, &hints, &supplied);
    hints.flags |= PAspect;
    hints.min_aspect.x = hints.min_aspect.y = 1;
    hints.max_aspect.x = hints.max_aspect.y = 1;
    XSetWMNormalHints(display, xid, &hints);
    XFlush(display);
#else
    (void)window;
#endif
}
