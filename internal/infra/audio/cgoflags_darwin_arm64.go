//go:build darwin && arm64

package audio

/*
#cgo CFLAGS: -I${SRCDIR}/ffmpeg_libs/include
#cgo LDFLAGS: -L${SRCDIR}/ffmpeg_libs/darwin/arm64
#cgo LDFLAGS: -lavcodec -lavformat -lavutil -lswresample
#cgo LDFLAGS: -lz -lbz2 -liconv -llzma
#cgo LDFLAGS: -framework CoreFoundation -framework Security
#cgo LDFLAGS: -framework VideoToolbox -framework AudioToolbox -framework CoreMedia
*/
import "C"
