#include <windows.h>
#include <commctrl.h>

#include <cstdlib>

#include "mini_player_aspect_windows.h"

namespace {
constexpr UINT_PTR kMiniPlayerAspectSubclassID = 0x414D4459;

bool isLeftEdge(WPARAM edge) {
    return edge == WMSZ_LEFT || edge == WMSZ_TOPLEFT || edge == WMSZ_BOTTOMLEFT;
}

bool isTopEdge(WPARAM edge) {
    return edge == WMSZ_TOP || edge == WMSZ_TOPLEFT || edge == WMSZ_TOPRIGHT;
}

bool isCorner(WPARAM edge) {
    return edge == WMSZ_TOPLEFT || edge == WMSZ_TOPRIGHT ||
           edge == WMSZ_BOTTOMLEFT || edge == WMSZ_BOTTOMRIGHT;
}

void setAspectRect(RECT *rect, WPARAM edge, const RECT &current, int heightMultiplier) {
    const int width = rect->right - rect->left;
    const int height = rect->bottom - rect->top;
    const int widthDelta = std::abs(width - (current.right - current.left));
    const int heightDelta = std::abs(height - (current.bottom - current.top));
    // A corner drag changes both dimensions. Choosing by the previous frame's
    // delta can alternate axes on Windows, making the window visibly flicker.
    const bool adjustHeight = isCorner(edge) || widthDelta >= heightDelta;

    if (adjustHeight) {
        const int height = width * heightMultiplier;
        if (isTopEdge(edge)) rect->top = rect->bottom - height;
        else rect->bottom = rect->top + height;
        return;
    }
    const int adjustedWidth = height / heightMultiplier;
    if (isLeftEdge(edge)) rect->left = rect->right - adjustedWidth;
    else rect->right = rect->left + adjustedWidth;
}

LRESULT CALLBACK miniPlayerAspectProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam,
                                      UINT_PTR subclassID, DWORD_PTR refData) {
    if (message == WM_SIZING) {
        RECT current{};
        if (GetWindowRect(hwnd, &current)) {
            setAspectRect(reinterpret_cast<RECT *>(lParam), wParam, current,
                          refData == 2 ? 2 : 1);
        }
    }
    return DefSubclassProc(hwnd, message, wParam, lParam);
}
} // namespace

void LockMiniPlayerAspect(void *window, bool expanded) {
    if (window != nullptr) {
        SetWindowSubclass(static_cast<HWND>(window), miniPlayerAspectProc,
                          kMiniPlayerAspectSubclassID, expanded ? 2 : 1);
    }
}
