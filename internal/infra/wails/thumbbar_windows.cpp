// thumbbar_windows.cpp — Windows Taskbar Thumbnail Toolbar (Prev/Play-Pause/Next).
//
// Displays three media-control buttons inside the taskbar preview popup using
// ITaskbarList3::ThumbBarAddButtons. Button clicks arrive as WM_COMMAND messages
// and are forwarded to Go via the goThumbBar* exports. Play/pause icon updates
// are posted from arbitrary goroutines via PostMessageW → WM_TB_SETPLAYING.
//
// Threading: ThumbBarInit is always called via application.InvokeAsync, which
// guarantees execution on the Win32 message thread. All COM work is done
// synchronously in ThumbBarInit on that thread. ThumbBarUpdateButtons runs in
// the subclass WndProc — also on the message thread — so the COM STA is shared.

#undef  _WIN32_WINNT
#define _WIN32_WINNT 0x0601
#undef  NTDDI_VERSION
#define NTDDI_VERSION 0x06010000

#include <windows.h>
#include <commctrl.h>
#include <shobjidl.h>
#include <objbase.h>

#include <cstdio>
#include <cstring>

#include "thumbbar_windows.h"

namespace {

constexpr UINT     TB_ID_PREV       = 1;
constexpr UINT     TB_ID_PLAYPAUSE  = 2;
constexpr UINT     TB_ID_NEXT       = 3;
constexpr UINT     THBN_CLICKED_VAL = 0x1800;
constexpr UINT     WM_TB_SETPLAYING = WM_APP + 10;
constexpr UINT_PTR SUBCLASS_ID      = 0xA171;

static HWND           g_hwnd         = nullptr;
static UINT           g_wmTBCreated  = 0;
static int            g_isPlaying    = 0;
static bool           g_subclassed   = false;

// The taskbar thumbnail popup is always dark regardless of the app theme,
// so white icons are always correct.
static const COLORREF g_fgColor      = RGB(255, 255, 255);

// All touched only on the message thread.
static ITaskbarList3* g_taskbar      = nullptr;
static HICON          g_icoPlay      = nullptr;
static HICON          g_icoPause     = nullptr;
static HICON          g_icoPrev      = nullptr;
static HICON          g_icoNext      = nullptr;
static bool           g_buttonsAdded = false;

static void TBDbg(const char* fmt, ...) {
    char buf[256];
    va_list ap; va_start(ap, fmt); vsnprintf(buf, sizeof buf, fmt, ap); va_end(ap);
    buf[sizeof buf - 1] = '\0';
    char tagged[288];
    snprintf(tagged, sizeof tagged, "[thumbbar] %s\n", buf);
    OutputDebugStringA(tagged);
}

// ---- GDI icon factory ------------------------------------------------------

struct IconCtx { HDC dc; HBITMAP bmp; DWORD* bits; };

static IconCtx MakeIconDC() {
    IconCtx ctx{};
    BITMAPINFO bmi{};
    bmi.bmiHeader.biSize        = sizeof(BITMAPINFOHEADER);
    bmi.bmiHeader.biWidth       = 16;
    bmi.bmiHeader.biHeight      = -16;
    bmi.bmiHeader.biPlanes      = 1;
    bmi.bmiHeader.biBitCount    = 32;
    bmi.bmiHeader.biCompression = BI_RGB;
    ctx.dc  = CreateCompatibleDC(nullptr);
    ctx.bmp = CreateDIBSection(ctx.dc, &bmi, DIB_RGB_COLORS,
                               reinterpret_cast<void**>(&ctx.bits), nullptr, 0);
    SelectObject(ctx.dc, ctx.bmp);
    memset(ctx.bits, 0, 16 * 16 * 4);
    return ctx;
}

static HICON FinalizeIcon(IconCtx& ctx) {
    // Black pixels stay transparent; all other pixels become fully opaque.
    for (int i = 0; i < 256; i++)
        if (ctx.bits[i] & 0x00FFFFFFu) ctx.bits[i] |= 0xFF000000u;
    HBITMAP hMask = CreateBitmap(16, 16, 1, 1, nullptr);
    ICONINFO ii{ TRUE, 0, 0, hMask, ctx.bmp };
    HICON icon = CreateIconIndirect(&ii);
    DeleteObject(hMask);
    DeleteObject(ctx.bmp);
    DeleteDC(ctx.dc);
    return icon;
}

// Select the foreground color brush; background stays transparent (black = 0).
static void BeginDraw(HDC dc) {
    SelectObject(dc, CreateSolidBrush(g_fgColor));
    SelectObject(dc, GetStockObject(NULL_PEN));
}

static void RightTri(HDC dc, int xtip, int ymid, int xb, int yt, int yb) {
    POINT p[3] = {{xtip,ymid},{xb,yt},{xb,yb}}; Polygon(dc, p, 3);
}
static void LeftTri(HDC dc, int xtip, int ymid, int xb, int yt, int yb) {
    POINT p[3] = {{xtip,ymid},{xb,yt},{xb,yb}}; Polygon(dc, p, 3);
}

static HICON MakePlayIcon()  { IconCtx c=MakeIconDC(); BeginDraw(c.dc); RightTri(c.dc,13,8,2,1,15);                              return FinalizeIcon(c); }
static HICON MakePauseIcon() { IconCtx c=MakeIconDC(); BeginDraw(c.dc); Rectangle(c.dc,2,2,6,14); Rectangle(c.dc,10,2,14,14);    return FinalizeIcon(c); }
static HICON MakePrevIcon()  { IconCtx c=MakeIconDC(); BeginDraw(c.dc); Rectangle(c.dc,1,2,4,14); LeftTri(c.dc,4,8,13,1,15);    return FinalizeIcon(c); }
static HICON MakeNextIcon()  { IconCtx c=MakeIconDC(); BeginDraw(c.dc); RightTri(c.dc,11,8,2,1,15); Rectangle(c.dc,12,2,15,14); return FinalizeIcon(c); }

// ---- button management -----------------------------------------------------

static void DoAddButtons() {
    if (!g_taskbar || g_buttonsAdded || !g_hwnd) return;

    THUMBBUTTON btns[3]{};
    btns[0] = {THB_ICON|THB_TOOLTIP|THB_FLAGS, TB_ID_PREV,      0, g_icoPrev,  {}, THBF_ENABLED};
    btns[1] = {THB_ICON|THB_TOOLTIP|THB_FLAGS, TB_ID_PLAYPAUSE, 0, g_isPlaying ? g_icoPause : g_icoPlay, {}, THBF_ENABLED};
    btns[2] = {THB_ICON|THB_TOOLTIP|THB_FLAGS, TB_ID_NEXT,      0, g_icoNext,  {}, THBF_ENABLED};
    wcsncpy(btns[0].szTip, L"Previous", 259);
    wcsncpy(btns[1].szTip, g_isPlaying ? L"Pause" : L"Play", 259);
    wcsncpy(btns[2].szTip, L"Next", 259);

    HRESULT hr = g_taskbar->ThumbBarAddButtons(g_hwnd, 3, btns);
    if (SUCCEEDED(hr)) { g_buttonsAdded = true; TBDbg("ThumbBarAddButtons OK"); }
    else                { TBDbg("ThumbBarAddButtons FAILED 0x%08X", (unsigned)hr); }
}

static void DoUpdatePlayPause(int playing) {
    g_isPlaying = playing;
    if (!g_buttonsAdded || !g_taskbar || !g_hwnd) return;

    THUMBBUTTON btn{};
    btn.dwMask  = THB_ICON | THB_TOOLTIP | THB_FLAGS;
    btn.iId     = TB_ID_PLAYPAUSE;
    btn.hIcon   = playing ? g_icoPause : g_icoPlay;
    wcsncpy(btn.szTip, playing ? L"Pause" : L"Play", 259);
    btn.dwFlags = THBF_ENABLED;

    HRESULT hr = g_taskbar->ThumbBarUpdateButtons(g_hwnd, 1, &btn);
    TBDbg("ThumbBarUpdateButtons %s", SUCCEEDED(hr) ? "OK" : "FAIL");
}

// ---- subclass WndProc ------------------------------------------------------

static LRESULT CALLBACK ThumbSubclassProc(
    HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam,
    UINT_PTR uIdSubclass, DWORD_PTR)
{
    if (msg == g_wmTBCreated && g_wmTBCreated != 0) {
        TBDbg("WM_TASKBARBUTTONCREATED — re-adding buttons");
        g_buttonsAdded = false;
        DoAddButtons();
        return 0;
    }
    if (msg == WM_TB_SETPLAYING) {
        DoUpdatePlayPause(static_cast<int>(wParam));
        return 0;
    }
    if (msg == WM_COMMAND && HIWORD(wParam) == THBN_CLICKED_VAL) {
        switch (LOWORD(wParam)) {
            case TB_ID_PREV:      TBDbg("prev");      goThumbBarPrev();      return 0;
            case TB_ID_PLAYPAUSE: TBDbg("playpause"); goThumbBarPlayPause(); return 0;
            case TB_ID_NEXT:      TBDbg("next");      goThumbBarNext();      return 0;
        }
    }
    if (msg == WM_DESTROY)
        RemoveWindowSubclass(hwnd, ThumbSubclassProc, uIdSubclass);

    return DefSubclassProc(hwnd, msg, wParam, lParam);
}

}  // namespace

// ---- public C ABI ----------------------------------------------------------

extern "C" {

// ThumbBarInit must be called on the Win32 message thread (via InvokeAsync).
// All COM work is done synchronously here; the subclass handles future events.
void ThumbBarInit(void* hwnd, int playing) {
    if (g_subclassed) { TBDbg("already init"); return; }
    if (!hwnd)        { TBDbg("null HWND");    return; }

    g_hwnd      = static_cast<HWND>(hwnd);
    g_isPlaying = playing;

    g_wmTBCreated = RegisterWindowMessageW(L"TaskbarButtonCreated");
    if (g_wmTBCreated)
        ChangeWindowMessageFilterEx(g_hwnd, g_wmTBCreated, MSGFLT_ALLOW, nullptr);

    // COM — called on the message thread. S_FALSE = already init'd; fine.
    // RPC_E_CHANGED_MODE = different model (MTA); ITaskbarList3 still works.
    HRESULT hr = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    if (FAILED(hr) && hr != (HRESULT)RPC_E_CHANGED_MODE) {
        TBDbg("CoInitializeEx FAILED 0x%08X", (unsigned)hr);
        return;
    }

    hr = CoCreateInstance(CLSID_TaskbarList, nullptr, CLSCTX_INPROC_SERVER,
                          IID_ITaskbarList3, reinterpret_cast<void**>(&g_taskbar));
    if (FAILED(hr) || !g_taskbar) { TBDbg("CoCreateInstance FAILED 0x%08X", (unsigned)hr); return; }

    hr = g_taskbar->HrInit();
    if (FAILED(hr)) { TBDbg("HrInit FAILED 0x%08X", (unsigned)hr); g_taskbar->Release(); g_taskbar=nullptr; return; }

    g_icoPlay  = MakePlayIcon();
    g_icoPause = MakePauseIcon();
    g_icoPrev  = MakePrevIcon();
    g_icoNext  = MakeNextIcon();

    // Install subclass for WM_COMMAND (clicks), WM_TB_SETPLAYING (updates),
    // and WM_TASKBARBUTTONCREATED (Explorer restart). Called on message thread ✓
    if (!SetWindowSubclass(g_hwnd, ThumbSubclassProc, SUBCLASS_ID, 0)) {
        TBDbg("SetWindowSubclass FAILED %lu", GetLastError());
    }
    g_subclassed = true;

    // Add buttons synchronously — we're on the message thread and COM is ready.
    // If Explorer hasn't built the taskbar button yet, WM_TASKBARBUTTONCREATED
    // will arrive and DoAddButtons() will retry.
    DoAddButtons();
    TBDbg("ThumbBarInit done (playing=%d, buttonsAdded=%d)", playing, (int)g_buttonsAdded);
}

void ThumbBarSetPlaying(int playing) {
    if (g_subclassed && g_hwnd)
        PostMessageW(g_hwnd, WM_TB_SETPLAYING, static_cast<WPARAM>(playing), 0);
}

void ThumbBarStop(void) {
    if (!g_subclassed) return;
    g_subclassed = false;
    if (g_hwnd) { RemoveWindowSubclass(g_hwnd, ThumbSubclassProc, SUBCLASS_ID); g_hwnd = nullptr; }
    if (g_taskbar) { g_taskbar->Release(); g_taskbar = nullptr; }
    if (g_icoPlay)  { DestroyIcon(g_icoPlay);  g_icoPlay  = nullptr; }
    if (g_icoPause) { DestroyIcon(g_icoPause); g_icoPause = nullptr; }
    if (g_icoPrev)  { DestroyIcon(g_icoPrev);  g_icoPrev  = nullptr; }
    if (g_icoNext)  { DestroyIcon(g_icoNext);  g_icoNext  = nullptr; }
    g_buttonsAdded = false;
    TBDbg("ThumbBarStop done");
}

}  // extern "C"
