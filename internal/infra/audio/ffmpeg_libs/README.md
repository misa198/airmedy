# FFmpeg Static Libraries

Pre-built minimal FFmpeg static libraries. Used by the **Windows and Linux** audio adapters (miniaudio) for non-native audio formats.

> **macOS:** FFmpeg is no longer used on darwin. The macOS adapter uses SFBAudioEngine which natively decodes all required formats. See `sfb_libs/` and `scripts/build-sfbaudioengine-darwin.sh`.

## Directory Structure

```
ffmpeg_libs/
  include/              Headers (shared across Linux/Windows)
  linux/
    amd64/              Linux x86_64
    arm64/              Linux ARM64
  windows/
    amd64/              Windows x86_64
```

## Building

| Platform | Script | Shell |
|----------|--------|-------|
| Linux | `bash scripts/build-ffmpeg-linux.sh` | bash |
| Windows | `bash scripts/build-ffmpeg-windows.sh --zig` | bash / Zig |

### Prerequisites

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
