package wails

import (
	"reflect"
	"testing"

	"github.com/wailsapp/wails/v3/pkg/application"
)

type recordingWindow struct {
	calls []string
}

func (w *recordingWindow) Show() application.Window {
	w.calls = append(w.calls, "show")
	return nil
}

func (w *recordingWindow) Focus() { w.calls = append(w.calls, "focus") }

func TestShowAndFocusPreservesWindowState(t *testing.T) {
	window := &recordingWindow{}

	showAndFocus(window)

	if want := []string{"show", "focus"}; !reflect.DeepEqual(window.calls, want) {
		t.Fatalf("window calls = %v, want %v", window.calls, want)
	}
}

func TestClampRectToWorkArea(t *testing.T) {
	// Primary screen work area: 1920x1080 at origin (dock-free for simplicity).
	primary := application.Rect{X: 0, Y: 0, Width: 1920, Height: 1080}

	tests := []struct {
		name string
		rect application.Rect
		wa   application.Rect
		want application.Rect
	}{
		{
			name: "already inside is unchanged",
			rect: application.Rect{X: 100, Y: 100, Width: 300, Height: 300},
			wa:   primary,
			want: application.Rect{X: 100, Y: 100, Width: 300, Height: 300},
		},
		{
			name: "off the right edge is pulled back in",
			rect: application.Rect{X: 1800, Y: 100, Width: 300, Height: 300},
			wa:   primary,
			want: application.Rect{X: 1620, Y: 100, Width: 300, Height: 300},
		},
		{
			name: "negative position (disconnected monitor) clamps to origin",
			rect: application.Rect{X: -500, Y: -400, Width: 300, Height: 300},
			wa:   primary,
			want: application.Rect{X: 0, Y: 0, Width: 300, Height: 300},
		},
		{
			name: "work area offset (taskbar/dock) respected",
			rect: application.Rect{X: 10, Y: 10, Width: 300, Height: 300},
			wa:   application.Rect{X: 0, Y: 50, Width: 1920, Height: 1030},
			want: application.Rect{X: 10, Y: 50, Width: 300, Height: 300},
		},
		{
			name: "window larger than work area shrinks to fit",
			rect: application.Rect{X: 0, Y: 0, Width: 500, Height: 500},
			wa:   application.Rect{X: 0, Y: 0, Width: 400, Height: 350},
			want: application.Rect{X: 0, Y: 0, Width: 400, Height: 350},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := clampRectToWorkArea(tt.rect, tt.wa)
			if got != tt.want {
				t.Errorf("clampRectToWorkArea(%+v, %+v) = %+v, want %+v", tt.rect, tt.wa, got, tt.want)
			}
		})
	}
}

func TestSquareMiniRect(t *testing.T) {
	tests := []struct {
		name string
		in   application.Rect
		want application.Rect
	}{
		{
			name: "keeps square geometry",
			in:   application.Rect{X: 10, Y: 20, Width: 300, Height: 300},
			want: application.Rect{X: 10, Y: 20, Width: 300, Height: 300},
		},
		{
			name: "uses the larger legacy dimension",
			in:   application.Rect{X: 10, Y: 20, Width: 280, Height: 180},
			want: application.Rect{X: 10, Y: 20, Width: 280, Height: 280},
		},
		{
			name: "caps oversized geometry",
			in:   application.Rect{Width: 600, Height: 400},
			want: application.Rect{Width: 500, Height: 500},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := squareMiniRect(tt.in); got != tt.want {
				t.Errorf("squareMiniRect(%+v) = %+v, want %+v", tt.in, got, tt.want)
			}
		})
	}
}

func TestClampSquareRectToWorkArea(t *testing.T) {
	got := clampSquareRectToWorkArea(
		application.Rect{X: 1800, Y: 900, Width: 500, Height: 500},
		application.Rect{X: 0, Y: 50, Width: 400, Height: 350},
	)
	want := application.Rect{X: 50, Y: 50, Width: 350, Height: 350}
	if got != want {
		t.Errorf("clampSquareRectToWorkArea() = %+v, want %+v", got, want)
	}
}
