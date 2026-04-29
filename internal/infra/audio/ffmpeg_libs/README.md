# FFmpeg Static Libraries

Pre-built minimal FFmpeg static libraries. Required for non-native audio formats (OGG, OPUS, APE, WavPack, DSD).

## Directory Structure

```
ffmpeg_libs/
  include/              Headers (shared across all platforms)
  darwin/
    arm64/              macOS Apple Silicon
    amd64/              macOS Intel
  linux/
    amd64/              Linux x86_64
    arm64/              Linux ARM64
  windows/
    amd64/              Windows x86_64
```

## Building

| Platform | Script | Shell |
|----------|--------|-------|
| macOS | `bash scripts/build-ffmpeg-darwin.sh` | Terminal |
| Linux | `bash scripts/build-ffmpeg-linux.sh` | bash |
| Windows | `bash scripts/build-ffmpeg-windows.sh --zig` | bash / Zig |

### Prerequisites

**macOS:** Xcode CLI tools. `nasm` optional (`brew install nasm` for SIMD).

**Linux:** `gcc make curl`. `nasm` optional (`apt install nasm`). For arm64 cross-compile: `apt install gcc-aarch64-linux-gnu`.

**Windows (Recommended):**
Zig compiler (0.13.0+). No MSYS2 required. Produces statically linked libraries.

**Windows (Legacy MSYS2 MINGW64):**
```
pacman -S mingw-w64-x86_64-gcc mingw-w64-x86_64-nasm make diffutils curl
```

## Included Codecs/Demuxers

| Format | Decoder | Demuxer |
|--------|---------|---------|
| OGG Vorbis | `vorbis` | `ogg` |
| OPUS | `opus` | `ogg` |
| APE (Monkey's Audio) | `ape` | `ape` |
| WavPack | `wavpack` | `wv` |
| DSD (DSF) | `dsd_lsbf_planar`, `dsd_msbf_planar` | `dsf` |
| DSD (DFF) | `dsd_lsbf`, `dsd_msbf` | `dff` |
