#!/usr/bin/env bash
# Builds the Android FFmpeg decoder used by mobile/androidApp.
# Source is downloaded to a temporary cache exactly like the desktop scripts;
# no FFmpeg source or generated library is committed to this repository.
#
# Usage: bash scripts/build-ffmpeg-android.sh [arm64-v8a|x86_64|all]
set -euo pipefail

FFMPEG_VERSION="8.1"
FFMPEG_URL="https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.gz"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ANDROID_DIR="${REPO_ROOT}/mobile/androidApp"
SDK_DIR="$(awk -F= '/^sdk.dir=/{print $2}' "${REPO_ROOT}/mobile/local.properties")"
NDK_VERSION="30.0.15729638"
NDK="${SDK_DIR}/ndk/${NDK_VERSION}"
TOOLCHAIN="$(find "${NDK}/toolchains/llvm/prebuilt" -maxdepth 1 -type d -name 'darwin-*' -print -quit)"
BUILD_DIR="${TMPDIR:-/tmp}/ffmpeg-build-airmedy-android-${FFMPEG_VERSION}"
JNI_OUT="${ANDROID_DIR}/src/main/jniLibs"
INCLUDE_OUT="${ANDROID_DIR}/build/ffmpeg/include"
API=31

[[ -x "${TOOLCHAIN}/bin/clang" ]] || { echo "Android NDK ${NDK_VERSION} is required at ${NDK}" >&2; exit 1; }

# FFmpeg is the sole decoder. Enable its complete decoder, demuxer and parser
# registry so every music format supported by the pinned upstream source stays
# supported without maintaining a fragile allow-list. Programs, encoders,
# muxers, filters, devices and network protocols remain excluded.

download_source() {
    mkdir -p "${BUILD_DIR}/src"
    if [[ ! -f "${BUILD_DIR}/ffmpeg.tar.gz" ]]; then
        echo "==> Downloading FFmpeg ${FFMPEG_VERSION}..."
        curl --fail --location --retry 3 "${FFMPEG_URL}" -o "${BUILD_DIR}/ffmpeg.tar.gz"
    fi
}

build_arch() {
    local ABI="$1" ARCH TRIPLE
    case "${ABI}" in
        arm64-v8a) ARCH="aarch64"; TRIPLE="aarch64-linux-android" ;;
        x86_64) ARCH="x86_64"; TRIPLE="x86_64-linux-android" ;;
        *) echo "Unsupported ABI: ${ABI}" >&2; exit 1 ;;
    esac
    local SRC="${BUILD_DIR}/src-${ABI}" INSTALL="${BUILD_DIR}/install-${ABI}"
    rm -rf "${SRC}" "${INSTALL}"
    mkdir -p "${SRC}" "${INSTALL}"
    tar -xzf "${BUILD_DIR}/ffmpeg.tar.gz" -C "${SRC}" --strip-components=1
    echo "==> Building FFmpeg ${FFMPEG_VERSION} for ${ABI}..."
    pushd "${SRC}" >/dev/null
    ./configure \
        --prefix="${INSTALL}" \
        --target-os=android --arch="${ARCH}" --enable-cross-compile \
        --cc="${TOOLCHAIN}/bin/${TRIPLE}${API}-clang" \
        --cxx="${TOOLCHAIN}/bin/${TRIPLE}${API}-clang++" \
        --ar="${TOOLCHAIN}/bin/llvm-ar" --ranlib="${TOOLCHAIN}/bin/llvm-ranlib" --strip="${TOOLCHAIN}/bin/llvm-strip" \
        --disable-everything --disable-autodetect --disable-programs --disable-doc --disable-debug --disable-network \
        --enable-shared --disable-static --enable-small --enable-pic \
        --enable-avutil --enable-avcodec --enable-avformat --enable-swresample \
        --enable-protocol=file --enable-decoders --enable-demuxers --enable-parsers \
        --disable-avdevice --disable-avfilter --disable-swscale \
        --extra-cflags="-Oz -ffunction-sections -fdata-sections" \
        --extra-ldflags="-Wl,--gc-sections -Wl,-z,max-page-size=16384"
    make -j"$(sysctl -n hw.ncpu)"
    make install
    popd >/dev/null
    mkdir -p "${JNI_OUT}/${ABI}"
    for lib in libavutil libswresample libavcodec libavformat; do
        cp -L "${INSTALL}/lib/${lib}.so" "${JNI_OUT}/${ABI}/${lib}.so"
    done
    rm -rf "${INCLUDE_OUT}"
    mkdir -p "$(dirname "${INCLUDE_OUT}")"
    cp -R "${INSTALL}/include" "${INCLUDE_OUT}"
}

download_source
case "${1:-all}" in
    arm64-v8a|x86_64) build_arch "$1" ;;
    all) build_arch arm64-v8a; build_arch x86_64 ;;
    *) echo "Usage: $0 [arm64-v8a|x86_64|all]" >&2; exit 1 ;;
esac
echo "==> Done. Generated FFmpeg libraries are in ${JNI_OUT}."
