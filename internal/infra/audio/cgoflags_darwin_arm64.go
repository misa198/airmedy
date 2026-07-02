//go:build darwin && arm64

package audio

/*
#cgo CFLAGS: -fmodules -F${SRCDIR}/sfb_libs/SFBAudioEngine.xcframework/macos-arm64
#cgo LDFLAGS: -F${SRCDIR}/sfb_libs/SFBAudioEngine.xcframework/macos-arm64
#cgo LDFLAGS: -Wl,-rpath,${SRCDIR}/sfb_libs/SFBAudioEngine.xcframework/macos-arm64
#cgo LDFLAGS: -Wl,-rpath,${SRCDIR}/sfb_libs/FLAC.xcframework/macos-arm64_x86_64
#cgo LDFLAGS: -Wl,-rpath,${SRCDIR}/sfb_libs/mpg123.xcframework/macos-arm64_x86_64
#cgo LDFLAGS: -Wl,-rpath,${SRCDIR}/sfb_libs/ogg.xcframework/macos-arm64_x86_64
#cgo LDFLAGS: -Wl,-rpath,${SRCDIR}/sfb_libs/opus.xcframework/macos-arm64_x86_64
#cgo LDFLAGS: -Wl,-rpath,${SRCDIR}/sfb_libs/vorbis.xcframework/macos-arm64_x86_64
#cgo LDFLAGS: -Wl,-rpath,${SRCDIR}/sfb_libs/wavpack.xcframework/macos-arm64_x86_64
#cgo LDFLAGS: -framework SFBAudioEngine
#cgo LDFLAGS: -framework CoreFoundation -framework Security -framework AudioToolbox
*/
import "C"
