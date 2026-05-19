//go:build darwin && amd64

package audio

/*
#cgo CFLAGS: -fmodules -F${SRCDIR}/sfb_libs/SFBAudioEngine.xcframework/macos-x86_64
#cgo LDFLAGS: -F${SRCDIR}/sfb_libs/SFBAudioEngine.xcframework/macos-x86_64
#cgo LDFLAGS: -framework SFBAudioEngine
#cgo LDFLAGS: -framework CoreFoundation -framework Security -framework AudioToolbox
*/
import "C"
