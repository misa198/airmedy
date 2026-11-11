//go:build darwin

package wails

/*
#cgo LDFLAGS: -L${SRCDIR}/traypanel_lib -ltraypanel -L/usr/lib/swift -lswiftCore -framework AppKit
#include <stdlib.h>
#include "tray_pill_darwin.h"
*/
import "C"

import (
	_ "embed"
	"log/slog"
	"strings"
	"unsafe"

	"airmedy/internal/app/i18n"
	"airmedy/internal/app/player"
	"airmedy/internal/domain"

	"github.com/wailsapp/wails/v3/pkg/application"
	"github.com/wailsapp/wails/v3/pkg/events"
)

//go:embed tray_panel_darwin.swift
var trayPanelSource string // Invalidate Go's build cache when the native library changes.

var trayPillClick func()
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

const trayPillPopupWidth = 310

//export goTrayPillClicked
func goTrayPillClicked() {
	if trayPillClick != nil {
		application.InvokeAsync(trayPillClick)
	}
}

var trayPanelLostFocus func()
var trayPanelEscape func()
var trayPanelFavorite func()
var trayPanelTick func()
var trayPanelSeek func(float64, string)
var trayPanelControl func(int)

//export goTrayPanelControl
func goTrayPanelControl(action C.int) {
	if control := trayPanelControl; control != nil {
		application.InvokeAsync(func() { control(int(action)) })
	}
}

//export goTrayPanelTick
func goTrayPanelTick() {
	if trayPanelTick != nil {
		application.InvokeAsync(trayPanelTick)
	}
}

//export goTrayPanelSeek
func goTrayPanelSeek(position C.double, trackID *C.char) {
	if trayPanelSeek != nil && trackID != nil {
		id := C.GoString(trackID)
		application.InvokeAsync(func() { trayPanelSeek(float64(position), id) })
	}
}

//export goTrayPanelLostFocus
func goTrayPanelLostFocus() {
	if trayPanelLostFocus != nil {
		application.InvokeAsync(trayPanelLostFocus)
	}
}

//export goTrayPanelEscape
func goTrayPanelEscape() {
	if trayPanelEscape != nil {
		application.InvokeAsync(trayPanelEscape)
	}
}

//export goTrayPanelFavorite
func goTrayPanelFavorite() {
	if trayPanelFavorite != nil {
		application.InvokeAsync(trayPanelFavorite)
	}
}

func trayTrackMetadata(track *domain.TrackDTO, translations *i18n.Service) (string, string) {
	if track == nil {
		return translations.T("tray.no_track"), ""
	}
	title := strings.TrimSpace(track.Title)
	if title == "" {
		title = translations.T("tray.unknown_track")
	}
	artists := make([]string, 0, len(track.Artists))
	for _, artist := range track.Artists {
		if artist != nil && strings.TrimSpace(artist.Name) != "" {
			artists = append(artists, strings.TrimSpace(artist.Name))
		}
	}
	if len(artists) == 0 {
		return title, translations.T("tray.unknown_artist")
	}
	return title, strings.Join(artists, ", ")
}

func trayPillTitle(track *domain.TrackDTO, translations *i18n.Service) string {
	if track == nil {
		return "Airmedy"
	}
	title, artist := trayTrackMetadata(track, translations)
	return title + " • " + artist
}

func nextTrayRepeatMode(mode domain.RepeatMode) domain.RepeatMode {
	switch mode {
	case domain.RepeatModeOff:
		return domain.RepeatModeAll
	case domain.RepeatModeAll:
		return domain.RepeatModeOne
	default:
		return domain.RepeatModeOff
	}
}

func SetupTrayPill(app *application.App, service *player.PlayerService, library *LibraryService, artwork domain.ArtworkCache, translations *i18n.Service, windowService *WindowService, theme, panelTheme, mode string, showIndicator bool, icon []byte) {
	showPopup := func() {
		var x, y C.int
		C.TrayPillPosition(trayPillPopupWidth, &x, &y)
		C.TrayPanelShow(x, y)
	}
	hidePopup := func() {
		C.TrayPanelHide()
	}
	trayPillClick = trayClickHandler(mode, windowService.ShowCurrent, func() {
		if bool(C.TrayPanelIsVisible()) {
			hidePopup()
			return
		}
		showPopup()
	})
	trayPillMenu = func() {
		hidePopup()
		showLabel := C.CString(translations.T("tray.show_airmedy"))
		quitLabel := C.CString(translations.T("tray.quit"))
		defer C.free(unsafe.Pointer(showLabel))
		defer C.free(unsafe.Pointer(quitLabel))
		C.TrayPillShowMenu(showLabel, quitLabel)
	}
	trayPillShow = windowService.ShowCurrent
	trayPillQuit = app.Quit
	trayPanelLostFocus = func() {
		if hideTrayPopupAfterFocusLoss(false, bool(C.TrayPillPointerOverItem())) {
			hidePopup()
		}
	}
	trayPanelEscape = hidePopup
	trayPanelFavorite = func() {
		track := service.GetCurrentTrack()
		if track == nil || track.ID == "" {
			return
		}
		go func(id string) {
			if _, err := library.ToggleFavorite(id); err != nil {
				slog.Error("toggle tray favorite", "track_id", id, "error", err)
			}
		}(track.ID)
	}
	trayPanelSeek = func(position float64, id string) {
		track := service.GetCurrentTrack()
		if track == nil || track.ID != id {
			return
		}
		if err := service.Seek(position); err != nil {
			slog.Error("seek from tray panel", "track_id", id, "error", err)
		}
	}
	trayPanelControl = func(action int) {
		status := service.GetStatus()
		var err error
		switch action {
		case 0:
			err = service.SetShuffle(!status.Shuffle)
		case 1:
			err = service.Previous()
		case 2:
			if status.PlaybackState == domain.PlaybackStatePlaying {
				err = service.Pause()
			} else {
				err = service.Play()
			}
		case 3:
			err = service.Next()
		case 4:
			err = service.SetRepeatMode(nextTrayRepeatMode(status.RepeatMode))
		}
		if err != nil {
			slog.Error("tray playback control", "action", action, "error", err)
		}
	}
	updateControls := func(status domain.PlayerStatus) {
		playKey, repeatKey, repeatMode := "tray.play", "tray.repeat", 0
		if status.PlaybackState == domain.PlaybackStatePlaying {
			playKey = "tray.pause"
		}
		switch status.RepeatMode {
		case domain.RepeatModeAll:
			repeatMode = 1
		case domain.RepeatModeOne:
			repeatMode, repeatKey = 2, "tray.repeat_one"
		}
		shuffle := C.CString(translations.T("tray.shuffle"))
		previous := C.CString(translations.T("tray.previous_track"))
		play := C.CString(translations.T(playKey))
		next := C.CString(translations.T("tray.next_track"))
		repeat := C.CString(translations.T(repeatKey))
		defer C.free(unsafe.Pointer(shuffle))
		defer C.free(unsafe.Pointer(previous))
		defer C.free(unsafe.Pointer(play))
		defer C.free(unsafe.Pointer(next))
		defer C.free(unsafe.Pointer(repeat))
		C.TrayPanelSetControls(C.bool(status.PlaybackState == domain.PlaybackStatePlaying), C.bool(status.Shuffle), C.int(repeatMode), shuffle, previous, play, next, repeat)
	}
	updateProgress := func(status domain.PlayerStatus) {
		id := C.CString(status.TrackID)
		label := C.CString(translations.T("tray.seek"))
		defer C.free(unsafe.Pointer(id))
		defer C.free(unsafe.Pointer(label))
		C.TrayPanelSetProgress(C.double(status.Position), C.double(status.Duration), id, label)
	}
	trayPanelTick = func() {
		if bool(C.TrayPanelIsVisible()) {
			updateProgress(service.GetStatus())
		}
	}
	offHide := app.Event.On("tray-popup:hide", func(_ *application.CustomEvent) {
		application.InvokeAsync(hidePopup)
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
	setPanelTheme := func(theme string) {
		value := C.CString(theme)
		defer C.free(unsafe.Pointer(value))
		C.TrayPanelSetTheme(value)
	}
	setPanelTheme(panelTheme)
	C.TrayPanelSetIndicator(C.bool(showIndicator))
	offIndicator := app.Event.On("settings:player-indicator", func(event *application.CustomEvent) {
		if show, ok := event.Data.(bool); ok {
			application.InvokeAsync(func() { C.TrayPanelSetIndicator(C.bool(show)) })
		}
	})
	offTrayTheme := app.Event.On("settings:tray-theme", func(event *application.CustomEvent) {
		if value, ok := event.Data.(string); ok {
			application.InvokeAsync(func() { setTheme(value) })
		}
	})
	offPanelTheme := app.Event.On("settings:tray-panel-theme", func(event *application.CustomEvent) {
		if value, ok := event.Data.(string); ok {
			application.InvokeAsync(func() { setPanelTheme(value) })
		}
	})
	update := func(status domain.PlayerStatus) {
		updateProgress(status)
		updateControls(status)
		track := service.GetCurrentTrack()
		title, path, trackID := trayPillTitle(track, translations), "", ""
		panelTitle, panelArtist := trayTrackMetadata(track, translations)
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
		cPanelTitle, cPanelArtist := C.CString(panelTitle), C.CString(panelArtist)
		cFavoriteLabel := C.CString(translations.T("tray.favorite"))
		defer C.free(unsafe.Pointer(cPanelTitle))
		defer C.free(unsafe.Pointer(cPanelArtist))
		defer C.free(unsafe.Pointer(cFavoriteLabel))
		C.TrayPanelUpdate(cPanelTitle, cPanelArtist, cPath, C.bool(track != nil && track.ID != ""), C.bool(track != nil && track.IsFavorite), cFavoriteLabel)
	}
	service.AddStatusListener(func(status domain.PlayerStatus) { application.InvokeAsync(func() { update(status) }) })
	service.AddQueueListener(func(_ []*domain.TrackDTO) { application.InvokeAsync(func() { update(service.GetStatus()) }) })
	service.AddTrackMetadataListener(func(_ domain.PlayerTrackMetadata) { application.InvokeAsync(func() { update(service.GetStatus()) }) })
	offFavorite := app.Event.On("library:favorite-changed", func(_ *application.CustomEvent) {
		application.InvokeAsync(func() { update(service.GetStatus()) })
	})
	update(service.GetStatus())
	offTheme := app.Event.OnApplicationEvent(events.Common.ThemeChanged, func(_ *application.ApplicationEvent) {
		application.InvokeAsync(func() {
			C.TrayPillRefreshTheme()
			C.TrayPanelRefreshTheme()
		})
	})
	offLanguage := app.Event.On("language:changed", func(_ *application.CustomEvent) {
		application.InvokeAsync(func() { update(service.GetStatus()) })
	})
	app.OnShutdown(func() {
		offTheme()
		offTrayTheme()
		offPanelTheme()
		offIndicator()
		offLanguage()
		offFavorite()
		offHide()
		C.TrayPanelDestroy()
		trayPillClick = nil
		trayPanelLostFocus = nil
		trayPanelEscape = nil
		trayPanelFavorite = nil
		trayPanelTick = nil
		trayPanelSeek = nil
		trayPanelControl = nil
		trayPillMenu = nil
		trayPillShow = nil
		trayPillQuit = nil
		C.TrayPillDestroy()
	})
}
