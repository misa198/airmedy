package wails

import (
	"fmt"
	"strings"

	"airmedy/internal/app/i18n"
	"airmedy/internal/app/player"
	"airmedy/internal/domain"

	"github.com/wailsapp/wails/v3/pkg/application"
)

type TrayManager struct {
	app            *application.App
	playerService  *player.PlayerService
	libraryService *LibraryService
	i18nService    *i18n.Service

	tray             *application.SystemTray
	mainWindow       *application.WebviewWindow
	currentTrackItem *application.MenuItem
	nextTrackItem    *application.MenuItem
	playPauseItem    *application.MenuItem
	nextActionItem   *application.MenuItem
	prevActionItem   *application.MenuItem
	repeatItem       *application.MenuItem
	shuffleItem      *application.MenuItem
	favoriteItem     *application.MenuItem
	showAirmedyItem  *application.MenuItem
	quitItem         *application.MenuItem
}

func NewTrayManager(app *application.App, playerService *player.PlayerService, libraryService *LibraryService, i18nService *i18n.Service) *TrayManager {
	return &TrayManager{
		app:            app,
		playerService:  playerService,
		libraryService: libraryService,
		i18nService:    i18nService,
	}
}

func (m *TrayManager) Setup(tray *application.SystemTray, mainWindow *application.WebviewWindow) {
	m.tray = tray
	m.mainWindow = mainWindow
	menu := application.NewMenu()

	m.currentTrackItem = menu.Add(m.i18nService.T("tray.no_track"))
	m.currentTrackItem.SetEnabled(false)

	m.nextTrackItem = menu.Add(m.i18nService.T("tray.next_none"))
	m.nextTrackItem.SetEnabled(false)

	menu.AddSeparator()

	m.playPauseItem = menu.Add(m.i18nService.T("tray.play")).OnClick(func(ctx *application.Context) {
		status := m.playerService.GetStatus()
		if status.PlaybackState == domain.PlaybackStatePlaying {
			_ = m.playerService.Pause()
		} else {
			// If queue is empty, shuffle all tracks
			if m.playerService.IsQueueEmpty() {
				tracks, err := m.libraryService.GetAllTracks()
				if err == nil && len(tracks) > 0 {
					_ = m.playerService.ShuffleTracks(tracks)
					return
				}
			}
			_ = m.playerService.Play()
		}
	})

	m.nextActionItem = menu.Add(m.i18nService.T("tray.next_track")).OnClick(func(ctx *application.Context) {
		_ = m.playerService.Next()
	})

	m.prevActionItem = menu.Add(m.i18nService.T("tray.previous_track")).OnClick(func(ctx *application.Context) {
		_ = m.playerService.Previous()
	})

	menu.AddSeparator()

	status := m.playerService.GetStatus()

	m.repeatItem = menu.AddCheckbox(m.i18nService.T("tray.repeat"), status.RepeatMode != domain.RepeatModeOff).OnClick(func(ctx *application.Context) {
		status := m.playerService.GetStatus()
		nextMode := domain.RepeatModeOff
		switch status.RepeatMode {
		case domain.RepeatModeOff:
			nextMode = domain.RepeatModeAll
		case domain.RepeatModeAll:
			nextMode = domain.RepeatModeOne
		case domain.RepeatModeOne:
			nextMode = domain.RepeatModeOff
		}
		_ = m.playerService.SetRepeatMode(nextMode)
	})

	m.shuffleItem = menu.AddCheckbox(m.i18nService.T("tray.shuffle"), status.Shuffle).OnClick(func(ctx *application.Context) {
		status := m.playerService.GetStatus()
		_ = m.playerService.SetShuffle(!status.Shuffle)
	})

	menu.AddSeparator()

	m.favoriteItem = menu.AddCheckbox(m.i18nService.T("tray.favorite"), false).OnClick(func(ctx *application.Context) {
		track := m.playerService.GetCurrentTrack()
		if track != nil {
			_, _ = m.libraryService.ToggleFavorite(track.ID)
		}
	})

	menu.AddSeparator()

	m.showAirmedyItem = menu.Add(m.i18nService.T("tray.show_airmedy")).OnClick(func(ctx *application.Context) {
		mainWindow.Show()
		mainWindow.Focus()
	})

	m.quitItem = menu.Add(m.i18nService.T("tray.quit")).OnClick(func(ctx *application.Context) {
		m.app.Quit()
	})

	tray.SetMenu(menu)

	// Register listeners
	m.playerService.AddStatusListener(m.onStatusChange)
	m.playerService.AddQueueListener(m.onQueueChange)
}

func (m *TrayManager) UpdateLanguage() {
	// Assumes caller is on main thread or handles synchronization (e.g. main.go event listener)
	status := m.playerService.GetStatus()
	m.updateStatus(status)

	// Items not updated by updateStatus
	m.nextActionItem.SetLabel(m.i18nService.T("tray.next_track"))
	m.prevActionItem.SetLabel(m.i18nService.T("tray.previous_track"))
	m.favoriteItem.SetLabel(m.i18nService.T("tray.favorite"))
	m.showAirmedyItem.SetLabel(m.i18nService.T("tray.show_airmedy"))
	m.quitItem.SetLabel(m.i18nService.T("tray.quit"))
}

func (m *TrayManager) onStatusChange(status domain.PlayerStatus) {
	application.InvokeSync(func() {
		m.updateStatus(status)
	})
}

func (m *TrayManager) updateStatus(status domain.PlayerStatus) {
	// Update Play/Pause label
	if status.PlaybackState == domain.PlaybackStatePlaying {
		m.playPauseItem.SetLabel(m.i18nService.T("tray.pause"))
	} else {
		m.playPauseItem.SetLabel(m.i18nService.T("tray.play"))
	}

	// Update Repeat label and check state
	repeatLabel := m.i18nService.T("tray.repeat")
	switch status.RepeatMode {
	case domain.RepeatModeAll:
		repeatLabel = m.i18nService.T("tray.repeat_all")
	case domain.RepeatModeOne:
		repeatLabel = m.i18nService.T("tray.repeat_one")
	}
	m.repeatItem.SetLabel(repeatLabel)
	m.repeatItem.SetChecked(status.RepeatMode != domain.RepeatModeOff)

	// Update Shuffle check state
	m.shuffleItem.SetLabel(m.i18nService.T("tray.shuffle"))
	m.shuffleItem.SetChecked(status.Shuffle)

	// Update current track title and next track title
	m.updateTrackLabelsInternal()
}

func (m *TrayManager) onQueueChange(queue []*domain.TrackDTO) {
	application.InvokeSync(func() {
		m.updateTrackLabelsInternal()
	})
}

func (m *TrayManager) updateTrackLabelsInternal() {
	track := m.playerService.GetCurrentTrack()
	if track != nil {
		artistNames := []string{}
		for _, a := range track.Artists {
			artistNames = append(artistNames, a.Name)
		}
		label := track.Title
		if len(artistNames) > 0 {
			label = fmt.Sprintf("%s — %s", track.Title, strings.Join(artistNames, ", "))
		}
		m.currentTrackItem.SetLabel(label)
		m.favoriteItem.SetChecked(track.IsFavorite)

		m.playPauseItem.SetEnabled(true)
		m.repeatItem.SetEnabled(true)
		m.shuffleItem.SetEnabled(true)
		m.favoriteItem.SetEnabled(true)
	} else {
		m.currentTrackItem.SetLabel(m.i18nService.T("tray.no_track"))
		m.favoriteItem.SetChecked(false)

		m.playPauseItem.SetEnabled(true)
		m.repeatItem.SetEnabled(true)
		m.shuffleItem.SetEnabled(true)
		m.favoriteItem.SetEnabled(false)
	}

	nextTrack := m.playerService.PeekNextTrack()
	if nextTrack != nil {
		m.nextTrackItem.SetLabel(m.i18nService.T("tray.next", nextTrack.Title))
		m.nextActionItem.SetEnabled(true)
	} else {
		m.nextTrackItem.SetLabel(m.i18nService.T("tray.next_none"))
		m.nextActionItem.SetEnabled(false)
	}

	prevTrack := m.playerService.PeekPreviousTrack()
	if prevTrack != nil {
		m.prevActionItem.SetEnabled(true)
	} else {
		m.prevActionItem.SetEnabled(false)
	}
}
