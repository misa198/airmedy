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

void setSquareRect(RECT *rect, WPARAM edge, const RECT &current) {
    const int width = rect->right - rect->left;
    const int height = rect->bottom - rect->top;
    const int widthDelta = std::abs(width - (current.right - current.left));
    const int heightDelta = std::abs(height - (current.bottom - current.top));
    const bool adjustHeight = widthDelta >= heightDelta;

    if (adjustHeight) {
        if (isTopEdge(edge)) rect->top = rect->bottom - width;
        else rect->bottom = rect->top + width;
        return;
    }
    if (isLeftEdge(edge)) rect->left = rect->right - height;
    else rect->right = rect->left + height;
}

LRESULT CALLBACK miniPlayerAspectProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam,
                                      UINT_PTR subclassID, DWORD_PTR refData) {
    if (message == WM_SIZING) {
        RECT current{};
        if (GetWindowRect(hwnd, &current)) {
            setSquareRect(reinterpret_cast<RECT *>(lParam), wParam, current);
        }
    }
    return DefSubclassProc(hwnd, message, wParam, lParam);
}
} // namespace

void LockMiniPlayerSquare(void *window) {
    if (window != nullptr) {
        SetWindowSubclass(static_cast<HWND>(window), miniPlayerAspectProc,
                          kMiniPlayerAspectSubclassID, 0);
    }
}
