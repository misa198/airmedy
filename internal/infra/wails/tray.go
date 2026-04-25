package wails

import (
	"fmt"
	"strings"

	"airmedy/internal/app/player"
	"airmedy/internal/domain"

	"github.com/wailsapp/wails/v3/pkg/application"
)

type TrayManager struct {
	app            *application.App
	playerService  *player.PlayerService
	libraryService *LibraryService

	currentTrackItem *application.MenuItem
	nextTrackItem    *application.MenuItem
	playPauseItem    *application.MenuItem
	nextActionItem   *application.MenuItem
	prevActionItem   *application.MenuItem
	repeatItem       *application.MenuItem
	shuffleItem      *application.MenuItem
	favoriteItem     *application.MenuItem
}

func NewTrayManager(app *application.App, playerService *player.PlayerService, libraryService *LibraryService) *TrayManager {
	return &TrayManager{
		app:            app,
		playerService:  playerService,
		libraryService: libraryService,
	}
}

func (m *TrayManager) Setup(tray *application.SystemTray, mainWindow *application.WebviewWindow) {
	menu := application.NewMenu()

	m.currentTrackItem = menu.Add("No track playing")
	m.currentTrackItem.SetEnabled(false)

	m.nextTrackItem = menu.Add("Next: None")
	m.nextTrackItem.SetEnabled(false)

	menu.AddSeparator()

	m.playPauseItem = menu.Add("Play").OnClick(func(ctx *application.Context) {
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

	m.nextActionItem = menu.Add("Next Track").OnClick(func(ctx *application.Context) {
		_ = m.playerService.Next()
	})

	m.prevActionItem = menu.Add("Previous Track").OnClick(func(ctx *application.Context) {
		_ = m.playerService.Previous()
	})

	menu.AddSeparator()

	status := m.playerService.GetStatus()

	m.repeatItem = menu.AddCheckbox("Repeat", status.RepeatMode != domain.RepeatModeOff).OnClick(func(ctx *application.Context) {
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

	m.shuffleItem = menu.AddCheckbox("Shuffle", status.Shuffle).OnClick(func(ctx *application.Context) {
		status := m.playerService.GetStatus()
		_ = m.playerService.SetShuffle(!status.Shuffle)
	})

	menu.AddSeparator()

	m.favoriteItem = menu.AddCheckbox("Favorite", false).OnClick(func(ctx *application.Context) {
		track := m.playerService.GetCurrentTrack()
		if track != nil {
			_, _ = m.libraryService.ToggleFavorite(track.ID)
		}
	})

	menu.AddSeparator()

	menu.Add("Show Airmedy").OnClick(func(ctx *application.Context) {
		mainWindow.Show()
		mainWindow.Focus()
	})

	menu.Add("Quit").OnClick(func(ctx *application.Context) {
		m.app.Quit()
	})

	tray.SetMenu(menu)

	// Register listeners
	m.playerService.AddStatusListener(m.onStatusChange)
	m.playerService.AddQueueListener(m.onQueueChange)
}

func (m *TrayManager) onStatusChange(status domain.PlayerStatus) {
	// Update Play/Pause label
	if status.PlaybackState == domain.PlaybackStatePlaying {
		m.playPauseItem.SetLabel("Pause")
	} else {
		m.playPauseItem.SetLabel("Play")
	}

	// Update Repeat label and check state
	repeatLabel := "Repeat"
	switch status.RepeatMode {
	case domain.RepeatModeAll:
		repeatLabel = "Repeat (All)"
	case domain.RepeatModeOne:
		repeatLabel = "Repeat (One)"
	}
	m.repeatItem.SetLabel(repeatLabel)
	m.repeatItem.SetChecked(status.RepeatMode != domain.RepeatModeOff)

	// Update Shuffle check state
	m.shuffleItem.SetChecked(status.Shuffle)

	// Update current track title and next track title
	m.updateTrackLabels()
}

func (m *TrayManager) onQueueChange(queue []*domain.TrackDTO) {
	m.updateTrackLabels()
}

func (m *TrayManager) updateTrackLabels() {
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
		m.currentTrackItem.SetLabel("No track playing")
		m.favoriteItem.SetChecked(false)

		m.playPauseItem.SetEnabled(true)
		m.repeatItem.SetEnabled(true)
		m.shuffleItem.SetEnabled(true)
		m.favoriteItem.SetEnabled(false)
	}

	nextTrack := m.playerService.PeekNextTrack()
	if nextTrack != nil {
		m.nextTrackItem.SetLabel(fmt.Sprintf("Next: %s", nextTrack.Title))
		m.nextActionItem.SetEnabled(true)
	} else {
		m.nextTrackItem.SetLabel("Next: None")
		m.nextActionItem.SetEnabled(false)
	}

	prevTrack := m.playerService.PeekPreviousTrack()
	if prevTrack != nil {
		m.prevActionItem.SetEnabled(true)
	} else {
		m.prevActionItem.SetEnabled(false)
	}
}
