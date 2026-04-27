# macOS FFmpeg Static Libraries

Pre-built minimal FFmpeg static libraries for macOS. Required for non-AVFoundation audio formats (OGG, OPUS, APE, WavPack, DSD).

## Directory Structure

```
darwin/
  arm64/   libavcodec.a  libavformat.a  libavutil.a  libswresample.a
  amd64/   libavcodec.a  libavformat.a  libavutil.a  libswresample.a
```

## Rebuilding

Run from the repo root:

```bash
bash scripts/build-ffmpeg-darwin.sh
```

This downloads FFmpeg source, builds minimal static libs for both arm64 and amd64, and copies them here. Requires Xcode Command Line Tools and `nasm` (`brew install nasm`).

## Included Codecs/Demuxers

| Format | Decoder | Demuxer |
|--------|---------|---------|
| OGG Vorbis | `vorbis` | `ogg` |
| OPUS | `opus` | `ogg` |
| APE (Monkey's Audio) | `ape` | `ape` |
| WavPack | `wavpack` | `wv` |
| DSD (DSF) | `dsd_lsbf_planar`, `dsd_msbf_planar` | `dsf` |
| DSD (DFF) | `dsd_lsbf`, `dsd_msbf` | `dff` |
