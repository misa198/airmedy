//go:build darwin

package power

/*
#cgo LDFLAGS: -framework IOKit -framework CoreFoundation
#include <IOKit/pwr_mgt/IOPMLib.h>
#include <CoreFoundation/CoreFoundation.h>

static uint32_t acquireSleepAssertion() {
    IOPMAssertionID assertionID = 0;
    CFStringRef reason = CFStringCreateWithCString(NULL, "Music is playing", kCFStringEncodingUTF8);
    IOPMAssertionCreateWithName(
        kIOPMAssertionTypePreventUserIdleSystemSleep,
        kIOPMAssertionLevelOn,
        reason,
        &assertionID
    );
    CFRelease(reason);
    return (uint32_t)assertionID;
}

static void releaseSleepAssertion(uint32_t assertionID) {
    if (assertionID != 0) {
        IOPMAssertionRelease((IOPMAssertionID)assertionID);
    }
}
*/
import "C"

import (
	"airmedy/internal/domain"
	"sync"
)

type darwinInhibitor struct {
	mu          sync.Mutex
	assertionID C.uint32_t
}

func NewInhibitor() domain.SleepInhibitor {
	return &darwinInhibitor{}
}

func (i *darwinInhibitor) Inhibit() error {
	i.mu.Lock()
	defer i.mu.Unlock()
	if i.assertionID != 0 {
		return nil
	}
	i.assertionID = C.acquireSleepAssertion()
	return nil
}

func (i *darwinInhibitor) Release() error {
	i.mu.Lock()
	defer i.mu.Unlock()
	if i.assertionID == 0 {
		return nil
	}
	C.releaseSleepAssertion(i.assertionID)
	i.assertionID = 0
	return nil
}
