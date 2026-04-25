#!/usr/bin/env bash
# Build minimal FFmpeg static libraries for Windows (amd64) using MSYS2/MinGW.
# Output: internal/infra/audio/ffmpeg_libs/windows/amd64/*.a
#         internal/infra/audio/ffmpeg_libs/include/  (shared headers)
#
# Run this script inside an MSYS2 MINGW64 shell on Windows:
#   pacman -S mingw-w64-x86_64-gcc mingw-w64-x86_64-nasm make diffutils pkg-config
#   bash scripts/build-ffmpeg-windows.sh
#
# Usage: bash scripts/build-ffmpeg-windows.sh

set -euo pipefail

FFMPEG_VERSION="8.1"
FFMPEG_URL="https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.gz"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUT_BASE="${REPO_ROOT}/internal/infra/audio/ffmpeg_libs/windows"
BUILD_DIR="${TEMP:-/tmp}/ffmpeg-build-airmedy-windows"

CONFIGURE_FLAGS=(
    --disable-everything
    --disable-doc
    --disable-programs
    --disable-debug
    --enable-static
    --disable-shared
    --enable-avcodec
    --enable-avformat
    --enable-avutil
    --enable-swresample
    # Decoders
    --enable-decoder=vorbis
    --enable-decoder=opus
    --enable-decoder=ape
    --enable-decoder=wavpack
    --enable-decoder=dsd_lsbf_planar
    --enable-decoder=dsd_msbf_planar
    --enable-decoder=dsd_lsbf
    --enable-decoder=dsd_msbf
    # Demuxers
    --enable-demuxer=ogg
    --enable-demuxer=ape
    --enable-demuxer=wv
    --enable-demuxer=dsf
    --enable-demuxer=dff
    # Parsers
    --enable-parser=vorbis
    --enable-parser=opus
    # Protocol
    --enable-protocol=file
    # Windows-specific
    --target-os=mingw32
    --arch=x86_64
    --enable-w32threads
    --disable-pthreads
)

LIBS=(libavcodec libavformat libavutil libswresample)

SRC_DIR="${BUILD_DIR}/src"
BUILD_ARCH_DIR="${BUILD_DIR}/build-amd64"
INSTALL_DIR="${BUILD_DIR}/install-amd64"

mkdir -p "${SRC_DIR}" "${BUILD_ARCH_DIR}" "${INSTALL_DIR}"

if [[ ! -f "${BUILD_DIR}/ffmpeg.tar.gz" ]]; then
    echo "==> Downloading FFmpeg ${FFMPEG_VERSION}..."
    curl -L "${FFMPEG_URL}" -o "${BUILD_DIR}/ffmpeg.tar.gz"
fi
echo "==> Extracting..."
tar -xzf "${BUILD_DIR}/ffmpeg.tar.gz" -C "${SRC_DIR}" --strip-components=1

echo "==> Configuring FFmpeg for Windows amd64..."
cd "${BUILD_ARCH_DIR}"
"${SRC_DIR}/configure" \
    "${CONFIGURE_FLAGS[@]}" \
    --prefix="${INSTALL_DIR}"

echo "==> Building..."
make -j"$(nproc 2>/dev/null || echo 4)"
make install

mkdir -p "${OUT_BASE}/amd64"
for LIB in "${LIBS[@]}"; do
    cp "${INSTALL_DIR}/lib/${LIB}.a" "${OUT_BASE}/amd64/${LIB}.a"
    echo "    copied ${LIB}.a -> ffmpeg_libs/windows/amd64/"
done

# Copy headers if not already present (shared with other platforms)
INCLUDE_OUT="${REPO_ROOT}/internal/infra/audio/ffmpeg_libs/include"
if [[ ! -d "${INCLUDE_OUT}" ]]; then
    echo "==> Copying FFmpeg headers..."
    cp -R "${INSTALL_DIR}/include" "${INCLUDE_OUT}"
fi

echo ""
echo "==> Done."
du -sh "${OUT_BASE}/amd64"
