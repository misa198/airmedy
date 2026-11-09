//go:build darwin

package wails

/*
#cgo LDFLAGS: -framework AppKit
#include <stdlib.h>
#include "tray_pill_darwin.h"
*/
import "C"

import (
	"strings"
	"unsafe"

	"airmedy/internal/app/i18n"
	"airmedy/internal/app/player"
	"airmedy/internal/domain"

	"github.com/wailsapp/wails/v3/pkg/application"
	"github.com/wailsapp/wails/v3/pkg/events"
)

var trayPillClick func()
var trayPillHidden func()
var trayPillMenu func()
var trayPillShow func()
var trayPillQuit func()

//export goTrayPillMenu
func goTrayPillMenu() {
	if trayPillMenu != nil {
		application.InvokeAsync(trayPillMenu)
	}
}

//export goTrayPillShow
func goTrayPillShow() {
	if trayPillShow != nil {
		application.InvokeAsync(trayPillShow)
	}
}

//export goTrayPillQuit
func goTrayPillQuit() {
	if trayPillQuit != nil {
		application.InvokeAsync(trayPillQuit)
	}
}

const trayPillPopupWidth = 300
const trayPillPopupHeight = 170

//export goTrayPillClicked
func goTrayPillClicked() {
	if trayPillClick != nil {
		application.InvokeAsync(trayPillClick)
	}
}

//export goTrayPillHidden
func goTrayPillHidden() {
	if trayPillHidden != nil {
		application.InvokeAsync(trayPillHidden)
	}
}

func trayPillWindowOptions(theme string) application.WebviewWindowOptions {
	return application.WebviewWindowOptions{
		Title: "Airmedy", Width: trayPillPopupWidth, Height: trayPillPopupHeight, Hidden: true,
		Frameless: true, AlwaysOnTop: true, DisableResize: true,
		BackgroundType: application.BackgroundTypeTransparent,
		URL:            "/?mode=tray-popup#/tray-popup",
		Mac: application.MacWindow{
			CollectionBehavior: application.MacWindowCollectionBehaviorTransient,
			WindowClass:        application.MacWindowClassPanel,
			PanelPreferences:   application.MacPanelPreferences{NonActivating: true},
		},
	}
}

func trayPillTitle(track *domain.TrackDTO, translations *i18n.Service) string {
	if track == nil {
		return "Airmedy"
	}
	copy := *track
	if strings.TrimSpace(copy.Title) == "" {
		copy.Title = translations.T("tray.unknown_track")
	}
	hasArtist := false
	for _, artist := range copy.Artists {
		if artist != nil && strings.TrimSpace(artist.Name) != "" {
			hasArtist = true
			break
		}
	}
	if !hasArtist {
		copy.Artists = []*domain.Artist{{Name: translations.T("tray.unknown_artist")}}
	}
	return trackLabel(&copy)
}

func SetupTrayPill(app *application.App, service *player.PlayerService, artwork domain.ArtworkCache, translations *i18n.Service, windowService *WindowService, theme, mode string, icon []byte) {
	popup := app.Window.NewWithOptions(trayPillWindowOptions(theme))
	closing, reopen := false, false
	showPopup := func() {
		var x, y C.int
		C.TrayPillPosition(trayPillPopupWidth, &x, &y)
		popup.SetPosition(int(x), int(y))
		C.TrayPillPreparePopup(popup.NativeWindow())
		popup.Show()
		popup.Focus()
		C.TrayPillAnimatePopup(popup.NativeWindow())
	}
	hidePopup := func() {
		if closing || !popup.IsVisible() {
			return
		}
		closing = true
		C.TrayPillAnimatePopupOut(popup.NativeWindow())
	}
	trayPillHidden = func() {
		if !closing {
			return
		}
		popup.Hide()
		closing = false
		if reopen {
			reopen = false
			showPopup()
		}
	}
	trayPillClick = func() {
		if closing {
			reopen = !reopen
			return
		}
		if popup.IsVisible() {
			hidePopup()
			return
		}
		showPopup()
	}
	trayPillMenu = func() {
		reopen = false
		hidePopup()
		showLabel := C.CString(translations.T("tray.show_airmedy"))
		quitLabel := C.CString(translations.T("tray.quit"))
		defer C.free(unsafe.Pointer(showLabel))
		defer C.free(unsafe.Pointer(quitLabel))
		C.TrayPillShowMenu(showLabel, quitLabel)
	}
	trayPillShow = windowService.ShowCurrent
	trayPillQuit = app.Quit
	popup.RegisterHook(events.Common.WindowLostFocus, func(_ *application.WindowEvent) {
		application.InvokeAsync(func() {
			if hideTrayPopupAfterFocusLoss(popup.IsFocused(), bool(C.TrayPillPointerOverItem())) {
				hidePopup()
			}
		})
	})
	popup.RegisterHook(events.Common.WindowClosing, func(e *application.WindowEvent) {
		hidePopup()
		e.Cancel()
	})
	offHide := app.Event.On("tray-popup:hide", func(_ *application.CustomEvent) {
		application.InvokeAsync(func() {
			reopen = false
			hidePopup()
		})
	})
	iconData := C.CBytes(icon)
	defer C.free(iconData)
	C.TrayPillCreate(C.bool(mode == "pill"), iconData, C.int(len(icon)))
	setTheme := func(theme string) {
		value := C.CString(theme)
		defer C.free(unsafe.Pointer(value))
		C.TrayPillSetTheme(value)
	}
	setTheme(theme)
	offTrayTheme := app.Event.On("settings:tray-theme", func(event *application.CustomEvent) {
		if value, ok := event.Data.(string); ok {
			application.InvokeAsync(func() { setTheme(value) })
		}
	})
	update := func(status domain.PlayerStatus) {
		track := service.GetCurrentTrack()
		title, path, trackID := trayPillTitle(track, translations), "", ""
		if track != nil {
			trackID = track.ID
			if track.ArtworkKey != "" && artwork.Exists(track.ArtworkKey) {
				path = artwork.GetPath(track.ArtworkKey)
			}
		}
		vibrant, muted := "", ""
		if status.Theme != nil {
			vibrant, muted = status.Theme.Vibrant, status.Theme.Muted
		}
		cTitle, cPath, cTrackID := C.CString(title), C.CString(path), C.CString(trackID)
		cVibrant, cMuted := C.CString(vibrant), C.CString(muted)
		defer C.free(unsafe.Pointer(cTitle))
		defer C.free(unsafe.Pointer(cPath))
		defer C.free(unsafe.Pointer(cTrackID))
		defer C.free(unsafe.Pointer(cVibrant))
		defer C.free(unsafe.Pointer(cMuted))
		C.TrayPillUpdate(cTitle, cPath, cTrackID, cVibrant, cMuted, C.bool(status.PlaybackState == domain.PlaybackStatePlaying))
	}
	service.AddStatusListener(func(status domain.PlayerStatus) { application.InvokeAsync(func() { update(status) }) })
	service.AddQueueListener(func(_ []*domain.TrackDTO) { application.InvokeAsync(func() { update(service.GetStatus()) }) })
	service.AddTrackMetadataListener(func(_ domain.PlayerTrackMetadata) { application.InvokeAsync(func() { update(service.GetStatus()) }) })
	update(service.GetStatus())
	offTheme := app.Event.OnApplicationEvent(events.Common.ThemeChanged, func(_ *application.ApplicationEvent) {
		application.InvokeAsync(func() { C.TrayPillRefreshTheme() })
	})
	offLanguage := app.Event.On("language:changed", func(_ *application.CustomEvent) {
		application.InvokeAsync(func() { update(service.GetStatus()) })
	})
	app.OnShutdown(func() {
		offTheme()
		offTrayTheme()
		offLanguage()
		offHide()
		popup.Hide()
		trayPillClick = nil
		trayPillHidden = nil
		trayPillMenu = nil
		trayPillShow = nil
		trayPillQuit = nil
		C.TrayPillDestroy()
	})
}
