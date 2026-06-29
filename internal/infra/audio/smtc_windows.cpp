// smtc_windows.cpp — Windows System Media Transport Controls (SMTC) backend.
//
// Provides OS-level Now Playing (title/artist/album, album-art thumbnail, media
// keys, play/pause glyph, and a seek scrubber) for the miniaudio player on
// Windows. Built with mingw-w64 GCC via cgo using the WinRT C-ABI projection
// headers (ABI::Windows::*), not MSVC C++/WinRT.
//
// Design: a dedicated STA thread owns a message-only window, obtains the SMTC
// for that window via ISystemMediaTransportControlsInterop::GetForWindow, and
// runs a message pump. Public Smtc* entry points (called from arbitrary Go
// goroutine threads) marshal work onto that thread via PostMessage, so every
// COM call happens on the owning apartment. OS media-button events are delivered
// on the STA thread and forwarded to Go via the goWinNowPlaying* exports.

// SMTC and the shcore WinRT stream helpers (CreateRandomAccessStreamOnFile) are
// gated behind a Windows 8+ SDK target, so raise it before any SDK header is pulled
// in. mingw's default target is too low and leaves those declarations hidden.
#undef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00
#undef NTDDI_VERSION
#define NTDDI_VERSION 0x0A000000

// mingw-w64 header bug: <windows.foundation.h> defines IReference<boolean> and
// IReference<BYTE> as separate specializations, but WinRT `boolean` and Win32
// `BYTE` are both `unsigned char`, so the two collide ("redefinition of struct
// ABI::Windows::Foundation::IReference<unsigned char>"). We use neither, so
// pre-define the boolean interface guard to suppress that duplicate definition.
// Must precede <windows.h>, which transitively pulls in <windows.foundation.h>.
#ifndef ____FIReference_1_boolean_INTERFACE_DEFINED__
#define ____FIReference_1_boolean_INTERFACE_DEFINED__
#endif

#include <windows.h>
#include <roapi.h>
#include <winstring.h>
#include <shobjidl.h>
#include <windows.foundation.h>
#include <windows.media.h>
#include <windows.storage.streams.h>
#include <systemmediatransportcontrolsinterop.h>
#include <shcore.h>

#include <atomic>
#include <mutex>
#include <string>
#include <cstdio>

#include "smtc_windows.h"

namespace {

// Emit a debug trace visible in WinDbg / DebugView / VS Output.
// Uses a fixed-size buffer; long messages are truncated.
void SmtcDbg(const char* fmt, ...) {
	char buf[512];
	va_list ap;
	va_start(ap, fmt);
	vsnprintf(buf, sizeof(buf), fmt, ap);
	va_end(ap);
	buf[sizeof(buf) - 1] = '\0';
	// Prepend a tag so traces are easy to filter.
	char tagged[544];
	snprintf(tagged, sizeof(tagged), "[smtc] %s\n", buf);
	OutputDebugStringA(tagged);
}

// Log an HRESULT, returning whether it represents failure.
bool SmtcHrFail(HRESULT hr, const char* context) {
	if (FAILED(hr)) {
		SmtcDbg("HRESULT 0x%08X failed: %s", (unsigned)hr, context);
		return true;
	}
	return false;
}

}  // namespace (helpers)

using namespace ABI::Windows::Foundation;
using namespace ABI::Windows::Media;
using namespace ABI::Windows::Storage::Streams;

namespace {

// Custom window messages used to marshal work onto the SMTC STA thread.
constexpr UINT WM_SMTC_UPDATE = WM_APP + 1;
constexpr UINT WM_SMTC_POSITION = WM_APP + 2;
constexpr UINT WM_SMTC_STATUS = WM_APP + 3;
constexpr UINT WM_SMTC_CLEAR = WM_APP + 4;
constexpr UINT WM_SMTC_QUIT = WM_APP + 5;

constexpr wchar_t kWindowClass[] = L"AirmedySmtcWindow";
constexpr wchar_t kAppUserModelID[] = L"me.misa198.airmedy";
constexpr wchar_t kAppDisplayName[] = L"Airmedy";

// 100-nanosecond ticks per second (WinRT TimeSpan unit).
constexpr double kTicksPerSecond = 10000000.0;

// All COM interfaces below are touched only on the STA thread.
std::atomic<bool> g_running{false};
std::mutex g_lifecycleMu;
HANDLE g_thread = nullptr;
HANDLE g_readyEvent = nullptr;
HWND g_hwnd = nullptr;

ISystemMediaTransportControls* g_smtc = nullptr;
ISystemMediaTransportControls2* g_smtc2 = nullptr;
ISystemMediaTransportControlsDisplayUpdater* g_updater = nullptr;
IRandomAccessStreamReferenceStatics* g_streamRefStatics = nullptr;

EventRegistrationToken g_buttonToken{};
EventRegistrationToken g_posToken{};
double g_durationSec = 0.0;

// Heap payload posted to the STA thread for a full metadata update.
struct UpdatePayload {
	std::wstring title;
	std::wstring artist;
	std::wstring album;
	std::wstring artwork;
	double duration = 0.0;
	double position = 0.0;
};

std::wstring Utf8ToWide(const char* s) {
	if (s == nullptr || *s == '\0') {
		return std::wstring();
	}
	int n = MultiByteToWideChar(CP_UTF8, 0, s, -1, nullptr, 0);
	if (n <= 0) {
		return std::wstring();
	}
	std::wstring out(static_cast<size_t>(n - 1), L'\0');
	MultiByteToWideChar(CP_UTF8, 0, s, -1, &out[0], n);
	return out;
}

// Delegate type aliases. Needed because __uuidof is a function-like macro and would
// otherwise choke on the comma inside the template argument list.
using ButtonPressedDelegate =
    ITypedEventHandler<SystemMediaTransportControls*,
                       SystemMediaTransportControlsButtonPressedEventArgs*>;
using PositionChangeDelegate =
    ITypedEventHandler<SystemMediaTransportControls*,
                       PlaybackPositionChangeRequestedEventArgs*>;

// Event handler for media-button presses (Play/Pause/Next/Previous).
struct ButtonHandler : ButtonPressedDelegate {
	LONG ref = 1;
	HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppv) override {
		if (ppv == nullptr) {
			return E_POINTER;
		}
		// Only respond to interfaces that share the IUnknown vtable shape: IUnknown,
		// the delegate IID itself, and the IAgileObject marker (which lets the runtime
		// invoke us directly without marshaling). Everything else — notably IMarshal
		// and IInspectable — must fail: returning `this` for IInspectable would expose
		// a 4-slot delegate vtable where 6 slots are expected and corrupt event
		// delivery (the reason media buttons silently never fire).
		if (IsEqualGUID(riid, IID_IUnknown) ||
		    IsEqualGUID(riid, IID_IAgileObject) ||
		    IsEqualGUID(riid, __uuidof(ButtonPressedDelegate))) {
			*ppv = static_cast<IUnknown*>(this);
			AddRef();
			return S_OK;
		}
		*ppv = nullptr;
		return E_NOINTERFACE;
	}
	ULONG STDMETHODCALLTYPE AddRef() override { return InterlockedIncrement(&ref); }
	ULONG STDMETHODCALLTYPE Release() override {
		LONG r = InterlockedDecrement(&ref);
		if (r == 0) {
			delete this;
		}
		return r;
	}
	HRESULT STDMETHODCALLTYPE Invoke(
	    ISystemMediaTransportControls* /*sender*/,
	    ISystemMediaTransportControlsButtonPressedEventArgs* args) override {
		SystemMediaTransportControlsButton btn;
		if (args != nullptr && SUCCEEDED(args->get_Button(&btn))) {
			switch (btn) {
				case SystemMediaTransportControlsButton_Play:
					SmtcDbg("button: play");
					goWinNowPlayingPlay();
					break;
				case SystemMediaTransportControlsButton_Pause:
					SmtcDbg("button: pause");
					goWinNowPlayingPause();
					break;
				case SystemMediaTransportControlsButton_Next:
					SmtcDbg("button: next");
					goWinNowPlayingNext();
					break;
				case SystemMediaTransportControlsButton_Previous:
					SmtcDbg("button: previous");
					goWinNowPlayingPrevious();
					break;
				default:
					SmtcDbg("button: unknown (%d)", (int)btn);
					break;
			}
		}
		return S_OK;
	}
};

// Event handler for scrubber seek requests from the OS.
struct PositionHandler : PositionChangeDelegate {
	LONG ref = 1;
	HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void** ppv) override {
		if (ppv == nullptr) {
			return E_POINTER;
		}
		// See ButtonHandler::QueryInterface — only IUnknown-shaped interfaces may
		// return this; IInspectable/IMarshal must fail.
		if (IsEqualGUID(riid, IID_IUnknown) ||
		    IsEqualGUID(riid, IID_IAgileObject) ||
		    IsEqualGUID(riid, __uuidof(PositionChangeDelegate))) {
			*ppv = static_cast<IUnknown*>(this);
			AddRef();
			return S_OK;
		}
		*ppv = nullptr;
		return E_NOINTERFACE;
	}
	ULONG STDMETHODCALLTYPE AddRef() override { return InterlockedIncrement(&ref); }
	ULONG STDMETHODCALLTYPE Release() override {
		LONG r = InterlockedDecrement(&ref);
		if (r == 0) {
			delete this;
		}
		return r;
	}
	HRESULT STDMETHODCALLTYPE Invoke(
	    ISystemMediaTransportControls* /*sender*/,
	    IPlaybackPositionChangeRequestedEventArgs* args) override {
		TimeSpan ts;
		if (args != nullptr && SUCCEEDED(args->get_RequestedPlaybackPosition(&ts))) {
			double posSec = static_cast<double>(ts.Duration) / kTicksPerSecond;
			SmtcDbg("seek requested: %.2fs", posSec);
			goWinNowPlayingSeek(posSec);
		}
		return S_OK;
	}
};

template <typename T>
HRESULT GetActivationFactory(const wchar_t* className, T** out) {
	HSTRING h = nullptr;
	HRESULT hr =
	    WindowsCreateString(className, static_cast<UINT32>(wcslen(className)), &h);
	if (FAILED(hr)) {
		return hr;
	}
	hr = RoGetActivationFactory(h, __uuidof(T), reinterpret_cast<void**>(out));
	WindowsDeleteString(h);
	return hr;
}

void ApplyTimeline(double positionSec) {
	if (g_smtc2 == nullptr) {
		return;
	}
	HSTRING cls = nullptr;
	const wchar_t* name = L"Windows.Media.SystemMediaTransportControlsTimelineProperties";
	if (FAILED(WindowsCreateString(name, static_cast<UINT32>(wcslen(name)), &cls))) {
		return;
	}
	IInspectable* insp = nullptr;
	if (SUCCEEDED(RoActivateInstance(cls, &insp)) && insp != nullptr) {
		ISystemMediaTransportControlsTimelineProperties* tl = nullptr;
		if (SUCCEEDED(insp->QueryInterface(
		        __uuidof(ISystemMediaTransportControlsTimelineProperties),
		        reinterpret_cast<void**>(&tl))) &&
		    tl != nullptr) {
			TimeSpan zero{0};
			TimeSpan end{static_cast<INT64>(g_durationSec * kTicksPerSecond)};
			TimeSpan pos{static_cast<INT64>(positionSec * kTicksPerSecond)};
			tl->put_StartTime(zero);
			tl->put_MinSeekTime(zero);
			tl->put_Position(pos);
			tl->put_EndTime(end);
			tl->put_MaxSeekTime(end);
			g_smtc2->UpdateTimelineProperties(tl);
			tl->Release();
		}
		insp->Release();
	}
	WindowsDeleteString(cls);
}

void ApplyThumbnail(const std::wstring& path) {
	if (g_updater == nullptr) {
		return;
	}
	if (path.empty()) {
		// No artwork for this track — clear any thumbnail left over from the
		// previous track, otherwise SMTC keeps showing the stale album art.
		g_updater->put_Thumbnail(nullptr);
		SmtcDbg("thumbnail: cleared (no artwork)");
		return;
	}
	if (g_streamRefStatics == nullptr) {
		SmtcDbg("thumbnail: missing statics, skipping");
		return;
	}

	// Open the artwork file as a WinRT IRandomAccessStream and hand SMTC an actual
	// readable stream via CreateFromStream. The previous CreateFromUri(file://...)
	// route is unreliable for SMTC thumbnails on unpackaged desktop apps — the shell
	// silently fails to resolve the file URI, so no art shows. (StorageFile +
	// CreateFromFile is the documented route but mingw's WinRT headers only
	// forward-declare IStorageFileStatics, so it isn't usable here.)
	IRandomAccessStream* ras = nullptr;
	HRESULT hr = CreateRandomAccessStreamOnFile(
	    path.c_str(), STGM_READ, __uuidof(IRandomAccessStream),
	    reinterpret_cast<void**>(&ras));
	if (FAILED(hr) || ras == nullptr) {
		SmtcDbg("thumbnail: CreateRandomAccessStreamOnFile failed 0x%08X for %ls",
		        (unsigned)hr, path.c_str());
		return;
	}

	IRandomAccessStreamReference* ref = nullptr;
	hr = g_streamRefStatics->CreateFromStream(ras, &ref);
	ras->Release();
	if (SUCCEEDED(hr) && ref != nullptr) {
		g_updater->put_Thumbnail(ref);
		ref->Release();
		SmtcDbg("thumbnail: applied");
	} else {
		SmtcDbg("thumbnail: CreateFromStream failed 0x%08X", (unsigned)hr);
	}
}

void SetMusicString(IMusicDisplayProperties* music,
                    HRESULT (STDMETHODCALLTYPE IMusicDisplayProperties::*setter)(HSTRING),
                    const std::wstring& value) {
	HSTRING h = nullptr;
	if (SUCCEEDED(WindowsCreateString(value.c_str(),
	                                  static_cast<UINT32>(value.size()), &h))) {
		(music->*setter)(h);
		WindowsDeleteString(h);
	}
}

void DoUpdate(const UpdatePayload& p) {
	if (g_updater == nullptr || g_smtc == nullptr) {
		SmtcDbg("DoUpdate: updater or smtc is null, skipping");
		return;
	}
	SmtcDbg("update: duration=%.1fs position=%.1fs artwork=%s",
	        p.duration, p.position,
	        p.artwork.empty() ? "(none)" : "(set)");
	g_durationSec = p.duration;
	g_updater->put_Type(MediaPlaybackType_Music);

	IMusicDisplayProperties* music = nullptr;
	if (SUCCEEDED(g_updater->get_MusicProperties(&music)) && music != nullptr) {
		SetMusicString(music, &IMusicDisplayProperties::put_Title, p.title);
		SetMusicString(music, &IMusicDisplayProperties::put_Artist, p.artist);
		IMusicDisplayProperties2* music2 = nullptr;
		if (SUCCEEDED(music->QueryInterface(__uuidof(IMusicDisplayProperties2),
		                                    reinterpret_cast<void**>(&music2))) &&
		    music2 != nullptr) {
			HSTRING h = nullptr;
			if (SUCCEEDED(WindowsCreateString(
			        p.album.c_str(), static_cast<UINT32>(p.album.size()), &h))) {
				music2->put_AlbumTitle(h);
				WindowsDeleteString(h);
			}
			music2->Release();
		}
		music->Release();
	}

	ApplyThumbnail(p.artwork);
	g_updater->Update();
	g_smtc->put_PlaybackStatus(MediaPlaybackStatus_Playing);
	ApplyTimeline(p.position);
}

void DoClear() {
	SmtcDbg("clear now playing");
	if (g_smtc != nullptr) {
		g_smtc->put_PlaybackStatus(MediaPlaybackStatus_Stopped);
	}
	if (g_updater != nullptr) {
		g_updater->ClearAll();
		g_updater->Update();
	}
	g_durationSec = 0.0;
}

LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
	switch (msg) {
		case WM_SMTC_UPDATE: {
			UpdatePayload* p = reinterpret_cast<UpdatePayload*>(lParam);
			if (p != nullptr) {
				DoUpdate(*p);
				delete p;
			}
			return 0;
		}
		case WM_SMTC_POSITION: {
			double* d = reinterpret_cast<double*>(lParam);
			if (d != nullptr) {
				ApplyTimeline(*d);
				delete d;
			}
			return 0;
		}
		case WM_SMTC_STATUS: {
			if (g_smtc != nullptr) {
				g_smtc->put_PlaybackStatus(wParam != 0 ? MediaPlaybackStatus_Playing
				                                       : MediaPlaybackStatus_Paused);
			}
			return 0;
		}
		case WM_SMTC_CLEAR: {
			DoClear();
			return 0;
		}
		case WM_SMTC_QUIT: {
			PostQuitMessage(0);
			return 0;
		}
		default:
			return DefWindowProcW(hwnd, msg, wParam, lParam);
	}
}

bool SetupControls() {
	ISystemMediaTransportControlsInterop* interop = nullptr;
	HRESULT hr = GetActivationFactory(L"Windows.Media.SystemMediaTransportControls", &interop);
	if (SmtcHrFail(hr, "GetActivationFactory(SMTC)") || interop == nullptr) {
		return false;
	}
	hr = interop->GetForWindow(g_hwnd, __uuidof(ISystemMediaTransportControls),
	                           reinterpret_cast<void**>(&g_smtc));
	interop->Release();
	if (SmtcHrFail(hr, "GetForWindow") || g_smtc == nullptr) {
		return false;
	}

	HRESULT hr2 = g_smtc->QueryInterface(__uuidof(ISystemMediaTransportControls2),
	                                     reinterpret_cast<void**>(&g_smtc2));
	if (FAILED(hr2)) {
		SmtcDbg("ISystemMediaTransportControls2 unavailable (0x%08X) — no timeline/seek", (unsigned)hr2);
	}
	g_smtc->get_DisplayUpdater(&g_updater);

	g_smtc->put_IsEnabled(TRUE);
	g_smtc->put_IsPlayEnabled(TRUE);
	g_smtc->put_IsPauseEnabled(TRUE);
	g_smtc->put_IsNextEnabled(TRUE);
	g_smtc->put_IsPreviousEnabled(TRUE);

	ButtonHandler* bh = new ButtonHandler();
	SmtcHrFail(g_smtc->add_ButtonPressed(bh, &g_buttonToken), "add_ButtonPressed");
	bh->Release();

	if (g_smtc2 != nullptr) {
		PositionHandler* ph = new PositionHandler();
		SmtcHrFail(g_smtc2->add_PlaybackPositionChangeRequested(ph, &g_posToken),
		           "add_PlaybackPositionChangeRequested");
		ph->Release();
	}

	SmtcHrFail(
	    GetActivationFactory(L"Windows.Storage.Streams.RandomAccessStreamReference",
	                         &g_streamRefStatics),
	    "GetActivationFactory(RandomAccessStreamReference)");
	SmtcDbg("controls ready (smtc2=%s, streamRef=%s)",
	        g_smtc2 != nullptr ? "yes" : "no",
	        g_streamRefStatics != nullptr ? "yes" : "no");
	return true;
}

void TeardownControls() {
	SmtcDbg("teardown controls");
	if (g_smtc != nullptr && g_buttonToken.value != 0) {
		g_smtc->remove_ButtonPressed(g_buttonToken);
		g_buttonToken = EventRegistrationToken{};
	}
	if (g_smtc2 != nullptr && g_posToken.value != 0) {
		g_smtc2->remove_PlaybackPositionChangeRequested(g_posToken);
		g_posToken = EventRegistrationToken{};
	}
	if (g_streamRefStatics != nullptr) {
		g_streamRefStatics->Release();
		g_streamRefStatics = nullptr;
	}
	if (g_updater != nullptr) {
		g_updater->Release();
		g_updater = nullptr;
	}
	if (g_smtc2 != nullptr) {
		g_smtc2->Release();
		g_smtc2 = nullptr;
	}
	if (g_smtc != nullptr) {
		g_smtc->put_IsEnabled(FALSE);
		g_smtc->Release();
		g_smtc = nullptr;
	}
	if (g_hwnd != nullptr) {
		DestroyWindow(g_hwnd);
		g_hwnd = nullptr;
	}
}

// Free payloads still queued at shutdown so we don't leak them.
void DrainQueue() {
	MSG msg;
	while (PeekMessageW(&msg, nullptr, WM_APP, WM_APP + 0xFF, PM_REMOVE)) {
		if (msg.message == WM_SMTC_UPDATE) {
			delete reinterpret_cast<UpdatePayload*>(msg.lParam);
		} else if (msg.message == WM_SMTC_POSITION) {
			delete reinterpret_cast<double*>(msg.lParam);
		}
	}
}

// Register a friendly DisplayName for our AppUserModelID. Without this, the
// shell can't resolve the AUMID of an unpackaged desktop app, so the Now Playing
// card shows "Unknown app". Writing HKCU\Software\Classes\AppUserModelId\<AUMID>
// with a DisplayName value is the documented route for unpackaged apps and needs
// no elevation (HKCU is per-user writable).
void RegisterAppDisplayName() {
	std::wstring key = L"Software\\Classes\\AppUserModelId\\";
	key += kAppUserModelID;
	HKEY hKey = nullptr;
	LONG rc = RegCreateKeyExW(HKEY_CURRENT_USER, key.c_str(), 0, nullptr,
	                          REG_OPTION_NON_VOLATILE, KEY_SET_VALUE, nullptr,
	                          &hKey, nullptr);
	if (rc != ERROR_SUCCESS) {
		SmtcDbg("RegCreateKeyEx(AppUserModelId) failed: %ld", rc);
		return;
	}
	const auto bytes =
	    static_cast<DWORD>((wcslen(kAppDisplayName) + 1) * sizeof(wchar_t));
	rc = RegSetValueExW(hKey, L"DisplayName", 0, REG_SZ,
	                    reinterpret_cast<const BYTE*>(kAppDisplayName), bytes);
	if (rc != ERROR_SUCCESS) {
		SmtcDbg("RegSetValueEx(DisplayName) failed: %ld", rc);
	}
	RegCloseKey(hKey);
}

DWORD WINAPI SmtcThread(LPVOID /*param*/) {
	SmtcDbg("STA thread started");

	HRESULT hrInit = RoInitialize(RO_INIT_SINGLETHREADED);
	bool initialized = SUCCEEDED(hrInit) || hrInit == S_FALSE;
	if (!initialized) {
		SmtcDbg("RoInitialize failed: 0x%08X", (unsigned)hrInit);
	} else {
		SmtcDbg("RoInitialize OK (hr=0x%08X)", (unsigned)hrInit);
	}

	RegisterAppDisplayName();
	SetCurrentProcessExplicitAppUserModelID(kAppUserModelID);

	WNDCLASSEXW wc{};
	wc.cbSize = sizeof(wc);
	wc.lpfnWndProc = WndProc;
	wc.hInstance = GetModuleHandleW(nullptr);
	wc.lpszClassName = kWindowClass;
	RegisterClassExW(&wc);

	// Must be a top-level window (parent = nullptr), NOT a message-only window
	// (HWND_MESSAGE). ISystemMediaTransportControlsInterop::GetForWindow requires
	// a top-level window; with HWND_MESSAGE the call succeeds but the shell never
	// registers a media session, so the Now Playing card silently never appears.
	// The window is created hidden (no WS_VISIBLE, never ShowWindow'd) and 0x0, so
	// it stays off-screen while still being a valid top-level window for SMTC.
	g_hwnd = CreateWindowExW(WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW, kWindowClass,
	                         L"Airmedy SMTC", WS_POPUP, 0, 0, 0, 0,
	                         nullptr, nullptr, wc.hInstance, nullptr);
	if (g_hwnd == nullptr) {
		SmtcDbg("CreateWindowEx failed: %lu", GetLastError());
	}

	bool ok = g_hwnd != nullptr && SetupControls();
	SmtcDbg("setup %s", ok ? "OK" : "FAILED");

	if (g_readyEvent != nullptr) {
		SetEvent(g_readyEvent);
	}

	if (ok) {
		SmtcDbg("entering message pump");
		MSG msg;
		while (GetMessageW(&msg, nullptr, 0, 0) > 0) {
			TranslateMessage(&msg);
			DispatchMessageW(&msg);
		}
		SmtcDbg("message pump exited");
	}

	DrainQueue();
	TeardownControls();
	UnregisterClassW(kWindowClass, wc.hInstance);
	if (initialized) {
		RoUninitialize();
	}
	SmtcDbg("STA thread exiting");
	return 0;
}

}  // namespace

extern "C" {

void SmtcStart(void) {
	std::lock_guard<std::mutex> lock(g_lifecycleMu);
	if (g_running.load()) {
		SmtcDbg("SmtcStart: already running, ignoring");
		return;
	}
	SmtcDbg("SmtcStart: launching STA thread");
	g_readyEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
	g_running.store(true);
	g_thread = CreateThread(nullptr, 0, SmtcThread, nullptr, 0, nullptr);
	if (g_thread == nullptr) {
		SmtcDbg("SmtcStart: CreateThread failed: %lu", GetLastError());
		g_running.store(false);
		if (g_readyEvent != nullptr) {
			CloseHandle(g_readyEvent);
			g_readyEvent = nullptr;
		}
		return;
	}
	if (g_readyEvent != nullptr) {
		DWORD wait = WaitForSingleObject(g_readyEvent, 5000);
		CloseHandle(g_readyEvent);
		g_readyEvent = nullptr;
		SmtcDbg("SmtcStart: ready wait result=%lu", wait);
	}
}

void SmtcStop(void) {
	std::lock_guard<std::mutex> lock(g_lifecycleMu);
	if (!g_running.load()) {
		SmtcDbg("SmtcStop: not running, ignoring");
		return;
	}
	SmtcDbg("SmtcStop: posting quit");
	g_running.store(false);
	if (g_hwnd != nullptr) {
		PostMessageW(g_hwnd, WM_SMTC_QUIT, 0, 0);
	}
	if (g_thread != nullptr) {
		DWORD wait = WaitForSingleObject(g_thread, 5000);
		SmtcDbg("SmtcStop: thread join result=%lu", wait);
		CloseHandle(g_thread);
		g_thread = nullptr;
	}
	g_hwnd = nullptr;
	SmtcDbg("SmtcStop: done");
}

void SmtcUpdate(const char* title, const char* artist, const char* album,
                double duration, double position, const char* artworkPath) {
	if (!g_running.load() || g_hwnd == nullptr) {
		return;
	}
	UpdatePayload* p = new UpdatePayload();
	p->title = Utf8ToWide(title);
	p->artist = Utf8ToWide(artist);
	p->album = Utf8ToWide(album);
	p->artwork = Utf8ToWide(artworkPath);
	p->duration = duration;
	p->position = position;
	if (!PostMessageW(g_hwnd, WM_SMTC_UPDATE, 0, reinterpret_cast<LPARAM>(p))) {
		delete p;
	}
}

void SmtcUpdatePosition(double position) {
	if (!g_running.load() || g_hwnd == nullptr) {
		return;
	}
	double* d = new double(position);
	if (!PostMessageW(g_hwnd, WM_SMTC_POSITION, 0, reinterpret_cast<LPARAM>(d))) {
		delete d;
	}
}

void SmtcSetPlaybackStatus(int playing) {
	if (!g_running.load() || g_hwnd == nullptr) {
		return;
	}
	PostMessageW(g_hwnd, WM_SMTC_STATUS, playing != 0 ? 1 : 0, 0);
}

void SmtcClear(void) {
	if (!g_running.load() || g_hwnd == nullptr) {
		return;
	}
	PostMessageW(g_hwnd, WM_SMTC_CLEAR, 0, 0);
}

}  // extern "C"
