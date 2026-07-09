//go:build linux && arm64

package audio

/*
#cgo CFLAGS: -I${SRCDIR}/ffmpeg_libs/include -I${SRCDIR}/aubio_libs/include
#cgo LDFLAGS: -L${SRCDIR}/ffmpeg_libs/linux/arm64 -L${SRCDIR}/aubio_libs/linux/arm64 -L${SRCDIR}/fftw3_libs/linux/arm64
#cgo LDFLAGS: -lavfilter -lavformat -lavcodec -lswresample -lavutil -laubio -lfftw3f
#cgo LDFLAGS: -lz -lbz2 -llzma -lpthread -lm -ldl
*/
import "C"
