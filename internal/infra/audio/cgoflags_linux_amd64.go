//go:build linux && amd64

package audio

/*
#cgo CFLAGS: -I${SRCDIR}/ffmpeg_libs/include
#cgo LDFLAGS: -L${SRCDIR}/ffmpeg_libs/linux/amd64
#cgo LDFLAGS: -lavformat -lavcodec -lswresample -lavutil
#cgo LDFLAGS: -lz -lbz2 -llzma -lpthread -lm -ldl
*/
import "C"
