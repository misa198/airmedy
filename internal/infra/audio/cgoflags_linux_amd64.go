//go:build linux && amd64

package audio

/*
#cgo CFLAGS: -I${SRCDIR}/ffmpeg_libs/include -I${SRCDIR}/aubio_libs/include
#cgo LDFLAGS: -L${SRCDIR}/ffmpeg_libs/linux/amd64 -L${SRCDIR}/aubio_libs/linux/amd64 -L${SRCDIR}/fftw3_libs/linux/amd64
#cgo LDFLAGS: -lavfilter -lavformat -lavcodec -lswresample -lavutil -laubio -lfftw3f
#cgo LDFLAGS: -lz -lbz2 -llzma -lpthread -lm -ldl
*/
import "C"
