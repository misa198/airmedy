//go:build windows && amd64

package audio

/*
#cgo CFLAGS: -I${SRCDIR}/ffmpeg_libs/include
#cgo LDFLAGS: -L${SRCDIR}/ffmpeg_libs/windows/amd64
#cgo LDFLAGS: -Wl,-Bstatic -lavcodec -lavformat -lavutil -lswresample -lz -lbz2 -liconv -llzma -Wl,-Bdynamic -lmfplat -lmf -lmfuuid -lstrmiids -lws2_32 -lsecur32 -lbcrypt -lole32 -loleaut32 -luuid -lwinmm -lversion -static-libgcc
*/
import "C"
