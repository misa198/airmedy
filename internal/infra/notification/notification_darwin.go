//go:build darwin

package notification

/*
#cgo CFLAGS: -x objective-c -fobjc-arc -fmodules
#cgo LDFLAGS: -framework Foundation -framework UserNotifications
#include <stdlib.h>

void SendTrackAdvancedNotification(const char* title, const char* body, const char* artworkPath);
*/
import "C"

import (
	"airmedy/internal/domain"
	"unsafe"
)

type darwinTrackTransitionNotifier struct{}

func NewTrackTransitionNotifier() domain.TrackTransitionNotifier {
	return &darwinTrackTransitionNotifier{}
}

func (n *darwinTrackTransitionNotifier) NotifyTrackAdvanced(title, body, artworkPath string) {
	cTitle := C.CString(title)
	cBody := C.CString(body)
	cArtworkPath := C.CString(artworkPath)
	defer C.free(unsafe.Pointer(cTitle))
	defer C.free(unsafe.Pointer(cBody))
	defer C.free(unsafe.Pointer(cArtworkPath))

	C.SendTrackAdvancedNotification(cTitle, cBody, cArtworkPath)
}
