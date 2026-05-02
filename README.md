<div align="center">

<img src="catalog/airmedy.png" alt="Airmedy" width="96" height="96" />

# Airmedy

**All in one offline music player.**

[![License](https://img.shields.io/github/license/misa198/airmedy?style=flat-square&color=0d1117)](LICENSE)
[![Go](https://img.shields.io/badge/Go-1.25-00ADD8?style=flat-square&logo=go&logoColor=white)](https://go.dev)
[![Wails](https://img.shields.io/badge/Wails-3-da0b0b?style=flat-square&logo=wails&logoColor=white)](https://wails.io/)
[![Last.fm](https://img.shields.io/badge/last.fm-da0b0b?style=flat-square&logo=last.fm&logoColor=white)](https://last.fm/)

[![MacoOS](https://shields.io/badge/MacOS--9cf?logo=Apple&style=social)](https://airdemy.netlify.app)
[![Windows](https://custom-icon-badges.demolab.com/badge/Windows-0078D6?logo=windows11&logoColor=white)](https://airdemy.netlify.app)
<!-- [![Linux](https://img.shields.io/badge/Linux-FCC624?logo=linux&logoColor=black)](#) -->

</div>

---

## Features

- **Your whole library** — add any folder and Airmedy scans it instantly, even with tens of thousands of tracks.
- **Lyrics that follow along** — synced lyrics scroll line-by-line as the song plays. Plain-text lyrics shown when sync data isn't available.
- **Fullscreen & miniplayer modes** — go fullscreen for an immersive listening experience, or shrink to a miniplayer that stays out of your way.
- **Playlists** — create and manage playlists, import and export them, and browse your collection by genre, artist, or album.
- **10-band equalizer** — tune the sound to your headphones or speakers. Runs natively on macOS (AVFoundation) and Windows/Linux (miniaudio) for optimal performance.
- **Lock screen & media keys** — control playback from your keyboard, lock screen, or Control Center — just like a first-party app.
- **Last.fm scrobbling** — sync your listening history and loved tracks automatically.
- **Fast search** — find any track, album, or artist in milliseconds.
- **Plays in the background** — close the window and music keeps going. Quit when you actually mean it.
- **Tray menu** — control playback from the system tray.

## Audio Format Support

| Format               |    macOS    | Windows | Linux |
| -------------------- | :---------: | :-----: | :---: |
| MP3                  | ✅ (Native) |   ✅    |  ✅   |
| AAC / M4A / ALAC     | ✅ (Native) |   ✅    |  ✅   |
| FLAC                 | ✅ (Native) |   ✅    |  ✅   |
| WAV / AIFF           | ✅ (Native) |   ✅    |  ✅   |
| Ogg Vorbis           | ✅ (FFmpeg) |   ✅    |  ✅   |
| Opus                 | ✅ (FFmpeg) |   ✅    |  ✅   |
| APE (Monkey's Audio) | ✅ (FFmpeg) |   ✅    |  ✅   |
| WavPack              | ✅ (FFmpeg) |   ✅    |  ✅   |
| DSD / DSF / DFF      | ✅ (FFmpeg) |   ✅    |  ✅   |

---

On macOS, playback runs through **AVFoundation** — hardware-accelerated, battery-efficient, the same engine Apple uses. On Windows and Linux, **miniaudio** provides high-performance audio output, while **FFmpeg** serves as the universal decoding backend for all supported formats, ensuring consistent and robust playback across the entire library.

```
┌─────────────────────────────────────────────────────────┐
│                      Airmedy Core                       │
│                                                         │
│   ┌─────────────┐       ┌──────────────────────────┐    │
│   │  macOS Path │       │  Windows / Linux Path    │    │
│   │             │       │                          │    │
│   │ AVAudioEngine       │      miniaudio           │    │
│   │  (output)   │       │   (output engine)        │    │
│   └──────┬──────┘       └───────────┬──────────────┘    │
│          │                          │                   │
│   ┌──────┴──────┐          ┌────────┴────────┐          │
│   │Format Native?          │  FFmpeg Decoder │          │
│   │  ┌───┴───┐  │          │ (All Formats)   │          │
│   │ YES      NO │          └────────┬────────┘          │
│   │  │       │  │                   │                   │
│   │AVAudio- FFmpeg                  │                   │
│   │ File    Stream                  │                   │
│   └──────┬──────┘                   │                   │
│          │                          │                   │
│          └──────────┬───────────────┘                   │
│                     │                                   │
│            Consistent Audio Stream                      │
│                (Float32 PCM)                            │
└─────────────────────────────────────────────────────────┘
```

The FFmpeg libraries are statically compiled and bundled inside `internal/infra/audio/ffmpeg_libs/`. No system FFmpeg installation is ever required.

---

## Tech Stack

| Layer                | Technology                  |
| -------------------- | --------------------------- |
| Backend runtime      | Go 1.25, Wails v3           |
| Dependency injection | uber-go/fx                  |
| Database             | SQLite via golang-migrate   |
| Search index         | Bleve                       |
| File watching        | fsnotify                    |
| Audio (macOS)        | AVFoundation + FFmpeg (CGo) |
| Audio (Win/Linux)    | miniaudio + FFmpeg (CGo)    |
| Metadata             | go-taglib                   |
| Frontend framework   | Vue 3 (Composition API)     |
| State management     | Pinia                       |
| UI components        | ShadCN-vue + Tailwind CSS   |
| Lyrics               | LRCLIB API                  |

---

## Architecture

Airmedy follows a **Hexagonal / Ports & Adapters** pattern:

```
cmd/
└── main.go                  # Entry point

internal/
├── domain/                  # Business logic, interfaces, DTOs
├── app/                     # Application services (use cases)
└── infra/
    ├── audio/               # AVFoundation / miniaudio / FFmpeg adapters
    ├── db/                  # SQLite migrations and queries
    ├── search/              # Bleve index adapter
    └── wails/               # Thin Wails bindings (frontend ↔ app)

frontend/
├── src/
│   ├── components/          # Feature components (AlbumCard, TrackTable…)
│   │   └── ui/              # Stateless UI primitives (Button, Slider…)
│   ├── views/               # Route-level pages
│   ├── stores/              # Pinia stores
│   ├── composables/         # Shared logic
│   └── locales/             # i18n locale files
```

Dependencies always point inward — `infra` depends on `app`, `app` depends on `domain`, never the reverse.

---

## Building from Source

### Prerequisites

| Tool         | Version                                                    |
| ------------ | ---------------------------------------------------------- |
| Go           | ≥ 1.25                                                     |
| Node.js      | ≥ 20                                                       |
| pnpm         | ≥ 9                                                        |
| Task         | [taskfile.dev](https://taskfile.dev)                       |
| Wails CLI v3 | `go install github.com/wailsapp/wails/v3/cmd/wails@latest` |

> **No system FFmpeg required.** Pre-built static libraries for `darwin/amd64`, `darwin/arm64`, `windows/amd64`, `linux/amd64`, and `linux/arm64` are bundled in `internal/infra/audio/ffmpeg_libs/`.

### Clone & Run

```bash
git clone https://github.com/misa198/airmedy.git
cd airmedy

# Install frontend dependencies
cd frontend && pnpm install && cd ..

# Run in development mode
wails3 dev

# Build production binary
wails3 build
```

### Verify

```bash
wails3 task verify   # runs all Go unit tests + Vue component tests + linters
```

### Run task

```bash
wails3 task {task_name}
```

---

## Roadmap

- [ ] **Gapless Playback** — zero-gap crossfade between tracks
- [ ] **Smart Playlists** — rule-based auto-playlists (genre, BPM, play count)
- [ ] **Podcast support** — RSS feed management alongside music library
- [ ] **Album and artist arts** — online search for missing arts
- [ ] **AirPlay 2** — stream to any AirPlay speaker or Apple TV directly from Airmedy.

---

## Contributing

1. Fork the repo and create a feature branch
2. Follow [Conventional Commits](https://www.conventionalcommits.org/) — enforced by the `commit-msg` hook (`task setup:hooks`)
3. All new features and bug fixes require accompanying tests
4. Open a pull request against `master`

---

## License

MIT © [misa198](https://github.com/misa198)

---

<div align="center">
  <sub>Built with Go + Vue 3 + AVFoundation. No Electron. No bloat.</sub>
</div>
