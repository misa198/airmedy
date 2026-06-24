<div align="center">

<img src="docs/airmedy.png" alt="Airmedy" width="96" height="96" />

# Airmedy

**All in one offline music player.**

[![License](https://img.shields.io/github/license/misa198/airmedy?style=flat-square&color=0d1117)](LICENSE)
[![Go](https://img.shields.io/badge/Go-1.25-00ADD8?style=flat-square&logo=go&logoColor=white)](https://go.dev)
[![Wails](https://img.shields.io/badge/Wails-3-da0b0b?style=flat-square&logo=wails&logoColor=white)](https://wails.io/)
[![Last.fm](https://img.shields.io/badge/last.fm-da0b0b?style=flat-square&logo=last.fm&logoColor=white)](https://last.fm/)
[![CI](https://img.shields.io/badge/CI-pass-00ADD8?style=flat-square&logo=githubactions)](https://github.com/misa198/airmedy/actions)

[![MacoOS](https://shields.io/badge/MacOS--9cf?logo=Apple&style=social)](https://airmedy.netlify.app)
[![Windows](https://custom-icon-badges.demolab.com/badge/Windows-0078D6?logo=windows11&logoColor=white)](https://airmedy.netlify.app)
[![Linux](https://custom-icon-badges.demolab.com/badge/Linux-FCC624?logo=linux&logoColor=black)](https://airmedy.netlify.app)

[![Website](https://custom-icon-badges.demolab.com/badge/Website-0078D6?logo=website&logoColor=white)](https://airmedy.netlify.app)
[![Latest Release](https://img.shields.io/github/v/release/misa198/airmedy?display_name=release&style=flat-square&label=Latest%20Release&color=26a69a)](https://github.com/misa198/airmedy/releases/latest)

</div>

---

<div align="center">
<img src="docs/remote.png" alt="remote" width="824" />
<img src="docs/tracks.png" alt="tracks" width="824" />
</div>

---

## Features

- **Your whole library** — add any folder and Airmedy scans it instantly, even with tens of thousands of tracks.
- **Lyrics that follow along** — synced lyrics scroll line-by-line as the song plays. Plain-text lyrics shown when sync data isn't available. Supports both embedded lyrics and online lyrics from LRCLIB and Kugou.
- **Fullscreen & miniplayer modes** — go fullscreen for an immersive listening experience, or shrink to a miniplayer that stays out of your way.
- **Playlists** — create and manage playlists, import and export them, and browse your collection by genre, artist, or album.
- **Gapless playback** — tracks transition without any silence or interruption.
- **10-band equalizer** — tune the sound to your headphones or speakers. Runs natively on macOS (SFBAudioEngine) and Windows/Linux (miniaudio) for optimal performance.
- **Lock screen & media keys** — control playback from your keyboard, lock screen, or Control Center — just like a first-party app.
- **Last.fm scrobbling** — sync your listening history and loved tracks automatically.
- **Fast search** — find any track, album, or artist in milliseconds.
- **Metadata editor** — update track titles, artists, albums, and other tags. Support for updating album artwork with automatic JPEG conversion.
- **Plays in the background** — close the window and music keeps going. Quit when you actually mean it.
- **Tray menu** — control playback from the system tray.
- **Prevent sleep** — optionally keep your system awake while music is playing.
- **Themes** — light, dark (gray), and black (pure black for OLED screens) themes.
- **Online Artist Arts** — fetch and display artist arts from Deezer.
- **Remote control** — control playback from any browser on the same network. Enable the remote server in settings, open the displayed URL on your phone or tablet, and enter the PIN to start controlling playback, managing the queue, and viewing lyrics remotely.

## Audio Format Support

Every format plays on **macOS · Windows · Linux**.

| Format               | Extensions          |
| -------------------- | ------------------- |
| MP3                  | `.mp3`              |
| AAC / ALAC           | `.m4a` `.aac` `.mp4`|
| FLAC                 | `.flac`             |
| WAV / AIFF           | `.wav` `.aiff`      |
| Ogg Vorbis           | `.ogg`              |
| Opus                 | `.opus`             |
| APE (Monkey's Audio) | `.ape`              |
| WavPack              | `.wv`               |
| DSD                  | `.dsf` `.dff`       |

---

On macOS, playback runs through **SFBAudioEngine** — a powerful, high-performance audio engine that provides native support for almost every format without needing FFmpeg. On Windows and Linux, **miniaudio** provides high-performance audio output, while **FFmpeg** serves as the universal decoding backend for all supported formats, ensuring consistent and robust playback across the entire library.

```mermaid
flowchart TB
    subgraph Core["Airmedy Core"]
        subgraph macOS["macOS Path"]
            SFB["SFBAudioEngine<br/>(All Formats)"]
        end
        subgraph WinLinux["Windows / Linux Path"]
            FF["FFmpeg Decoder<br/>(All Formats)"] --> MA["miniaudio<br/>(output engine)"]
        end
        SFB --> Stream
        MA --> Stream["Consistent Audio Stream<br/>(Float32 PCM)"]
    end
```

The FFmpeg libraries are statically compiled and bundled inside `internal/infra/audio/ffmpeg_libs/`. No system FFmpeg installation is ever required.

---

## Tech Stack

| Layer                | Technology                        |
| -------------------- | --------------------------------- |
| Backend runtime      | Go 1.25, Wails v3                 |
| Dependency injection | uber-go/fx                        |
| Database             | SQLite via golang-migrate         |
| Search index         | Bleve                             |
| File watching        | fsnotify                          |
| Audio (macOS)        | SFBAudioEngine + CGo              |
| Audio (Win/Linux)    | miniaudio + FFmpeg (CGo)          |
| Metadata             | go-taglib                         |
| Frontend framework   | Vue 3 (Composition API)           |
| State management     | Pinia 3                           |
| UI components        | Radix Vue + Tailwind CSS v4       |
| Monorepo             | pnpm workspaces + Turbo           |
| UI package           | @airmedy/ui (packages/ui)         |
| Utils package        | @airmedy/utils (packages/utils)   |
| Lyrics               | LRCLIB API                        |

---

## Architecture

Airmedy follows a **Hexagonal / Ports & Adapters** pattern:

```
main.go                      # Entry point

internal/
├── domain/                  # Business logic, interfaces, DTOs
├── app/                     # Application services (use cases)
└── infra/
    ├── audio/               # SFBAudioEngine / miniaudio / FFmpeg adapters
    ├── db/                  # SQLite migrations and queries
    ├── search/              # Bleve index adapter
    └── wails/               # Thin Wails bindings (frontend ↔ app)

frontend/                    # Main desktop UI (Vue 3)
├── src/
│   ├── components/          # Feature components (AlbumCard, TrackTable…)
│   ├── views/               # Route-level pages
│   ├── stores/              # Pinia stores
│   ├── composables/         # Shared logic
│   └── locales/             # i18n locale files

packages/
├── ui/                      # @airmedy/ui — stateless UI primitives (Slider, Input…)
└── utils/                   # @airmedy/utils — shared utilities (logger, utils)

remote/                      # Browser-based remote control SPA (Vue 3)
└── src/
    ├── components/          # Remote UI components
    ├── stores/              # Pinia stores (player state over WS)
    └── locales/             # i18n locale files
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

> **No system FFmpeg required.** FFmpeg is the decode backend on Windows/Linux only — macOS uses SFBAudioEngine and ships no FFmpeg. Pre-built static libraries for `windows/amd64`, `windows/arm64`, `linux/amd64`, and `linux/arm64` are bundled in `internal/infra/audio/ffmpeg_libs/`.
>
> To rebuild them: `scripts/build-ffmpeg-linux.sh` and `scripts/build-ffmpeg-windows.sh` (the latter cross-compiles `amd64` + `arm64` from Linux via `gcc-mingw-w64`). `scripts/build-ffmpeg-windows-msys2.sh` builds Windows `amd64` natively inside an MSYS2 MINGW64 shell. All produce minimal static `.a` libs (FFmpeg 8.1, decoders only).

### Clone & Run

```bash
git clone https://github.com/misa198/airmedy.git
cd airmedy

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

- [ ] **Smart Playlists** — rule-based auto-playlists (genre, BPM, play count)
- [ ] **AirPlay 2** — stream to any AirPlay speaker or Apple TV directly from Airmedy.

---

## Contributing

1. Fork the repo and create a feature branch
2. Follow [Conventional Commits](https://www.conventionalcommits.org/) — enforced by the `commit-msg` hook (`wails3 task setup:hooks`)
3. All new features and bug fixes require accompanying tests
4. Open a pull request against `master`

---

## License

MIT © [misa198](https://github.com/misa198)

---
