//go:build linux && arm64

package audio

/*
#cgo CFLAGS: -I${SRCDIR}/ffmpeg_libs/include
#cgo LDFLAGS: -L${SRCDIR}/ffmpeg_libs/linux/arm64
#cgo LDFLAGS: -lavformat -lavcodec -lswresample -lavutil
#cgo LDFLAGS: -lz -lbz2 -llzma -lpthread -lm -ldl
*/
import "C"
