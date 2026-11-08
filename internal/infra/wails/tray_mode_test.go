package wails

import (
	"airmedy/internal/domain"
	"testing"
)

func TestUseTrayPill(t *testing.T) {
	for _, tc := range []struct {
		platform, mode string
		shown, want    bool
	}{
		{"darwin", "menu", true, true},
		{"darwin", "pill", true, true},
		{"darwin", "pill", false, false},
		{"windows", "pill", true, false},
		{"linux", "menu", true, false},
		{"darwin", "menu", false, false},
	} {
		settings := &domain.AppSettings{ShowTrayIcon: tc.shown, TrayMode: tc.mode}
		if got := UseTrayPill(tc.platform, settings); got != tc.want {
			t.Errorf("%s %s shown=%v: got %v, want %v", tc.platform, tc.mode, tc.shown, got, tc.want)
		}
	}
}

func TestHideTrayPopupAfterFocusLoss(t *testing.T) {
	if hideTrayPopupAfterFocusLoss(false, true) {
		t.Fatal("clicking the tray must leave the popup open for the click handler to toggle it")
	}
	if hideTrayPopupAfterFocusLoss(true, false) {
		t.Fatal("a delayed focus-loss event must not hide a focused popup")
	}
	if !hideTrayPopupAfterFocusLoss(false, false) {
		t.Fatal("clicking away from the popup must hide it")
	}
}
