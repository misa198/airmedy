# Desktop changelog

## v1.3.3

Changes since v1.3.2.

- chore: bump version to 1.3.3 (1d806823)
- chore: upgrade wails3 to stable version (8aedda81)
- perf(app): limit favorite updates to affected state and playlists (a4923bf7)
- fix(ui): prevent darker outline on favorite icon (a278ba4e)
- chore: bump selected landing download versions (32a86a11)

## v1.3.2

Changes since v1.3.1.

- chore: bump version to 1.3.2 (59e75234)
- feat(ui): add favorite button to fullscreen player (77bdb6ef)
- fix(ui): preserve playlist track list during favorite refresh (0f4f1cbf)
- chore: bump selected landing download versions (b7beb970)

## v1.3.1

Changes since v1.3.0.

- chore: bump version to 1.3.1 (b4865211)
- feat(player): add favourite button to footer player and mini player (39ad7ae5)
- feat: set default glow = 0 (08439a48)
- feat: improve font for windows and linux (008679f3)
- chore: upgrade deps (af1be3f4)

## v1.3.0

Changes since v1.2.0.

- chore: bump version to 1.3.0 (54854d5d)
- chore: upgrade deps (c2f1e0ac)
- fix(ui): remove context menu hover transition (97aebab4)
- feat(ui): expand fullscreen artwork to control width (4f88640a)
- chore: increase sharp living artwork (e861448c)
- feat(lyrics): glow enhanced lrc setting (77baa67f)
- fix: write crash log to file (7dc744bd)
- feat(lyrics): word by word lyrics animation (c28f8c40)
- fix(ui): smooth romanization controls across player views (be10f25d)
- fix(ui): recover interrupted artwork loads (d9e7c9ba)
- feat(lyrics): remove space event on lyrics (b708fe70)
- chore(frontend): update deps (56cbbe3d)
- fix(ui): prevent enhanced LRC active-line flash (45697341)
- fix: maximize window on macos (0045339d)
- chore: upgrade wails (9a64fbc2)
- refactor(fullscreen-player): render lyrics using gpu (365d3b57)
- feat(ui): support enhanced LRC lyrics (a9c50d90)
- docs: add ci badges (848173bf)
- chore: bump selected landing download versions (9c2b84fe)

## v1.2.0

Changes since v1.1.1.

- chore: bump version to 1.2.0 (a1870f59)
- ci: add skip-ci option (0496f15a)
- ci: update macos to 27 (3f513c06)
- fix: linux arm64 ci (c9c54d07)

## v1.1.1

Changes since v1.1.0.

- chore: bump version to 1.1.1 (e42c2100)
- fix(audio): restore playback position on macOS (bc2330cb)
- chore: upgrade ffmpeg to 8.1.2 (425c3711)
- chore: upgrade sfbaudioengine to 0.14.0 (9809028d)
- feat(desktop): add romanization lyrics setting (66114cd6)
- fix: add bottom mini panel lyrics (9378165a)
- ci: fix upload apk (9cfe2ee6)
- ci: fix upload apk (52f6b26a)
- ci(android): fix merge master build mobile (9a2f49e7)
- ci(android): build both of prod and debug apk when merge (61ce6c63)
- ci(android): build both of prod and debug apks (dd3f8128)
- refactor(romanization): remove Japanese support (74e4dd1b)
- fix(ui): localize romanization labels (2a1322a7)
- docs: update readme and landing page for romanization (b662f076)
- fix: reset scroll state when toggle (8cc56272)
- feat(ui): share lyrics romanization across views (679b952c)
- feat(lyrics): add offline CJK romanization (e79ad1e9)
- fix(app): avoid unavailable Android SDK tools package (ecded55e)
- fix(landing-page): fix macos download link (7c8c6124)
- chore: upgrade wails (5e7b75c8)
- chore: update wails (d0849a28)
- fix(ui): delay queue track dragging (bd1b353a)
- chore: bump selected landing download versions (8e6c9d79)

## v1.1.0

Changes since v1.0.5.

- chore: bump version to 1.1.0 (c35aa913)
- fix: remove test only flag (82d4a83d)
- fix: fix find android prod apk (e0f0c390)
- fix: bump download tag (8c7d1979)
- ci: add bump download version for android (49a3ffa4)
- ci: update build ci (f7e629c3)
- chore: update deps (c7c37f64)
- ci: skip desktop build for mobile changes (157b3890)
- fix(macos): add-support-macos-27-tray-menu (1a66e85f)
- fix(ui): prevent fullscreen control icon outlines (9c42fcbf)
- feat(analysis): retry failed library tracks (2df5f5cd)
- feat(app): add mobile mood radio (83380d5f)
- fix(player): preserve state when navigating queue (c60ceadc)
- fix(ui): fill home track grid by column (d814dd5d)
- fix: gpu compositing border tear on blurred background (a4480e3a)
- fix(ui): balance living artwork palette (2fab4ad6)
- fix(ui): use backdrop tint for player artwork (2dcffa23)
- fix: update history header (af5ec421)
- fix(sync): improve mobile library synchronization (20cf4ae2)
- fix: fix test (f568633c)
- fix(sync): clear mobile tracks after playlist deletion (1f016f72)
- fix: responsive most played tracks table (81057ae2)
- feat(android): reorder playlist (220cab33)
- fix(ui): prevent stale sync selection rows (4c4be7a5)
- fix(ui): refresh sync scope tabs (69fcf9ce)
- fix(app): reconcile mobile playlists (a0b8d68a)
- fix(ui): drag queue rows (6ff12385)
- fix(ui): restore mini player drag region (780ababa)
- fix: padding synced lyrics (d33ff5e7)
- fix(ui): preserve mini player panel at minimum size (334d2887)
- fix(ui): prevent mini player resize flicker on windows (b0e7022a)
- docs: update readme for new miniplayer (a1c8be92)
- docs(landing-page): update screenshot (b2819ce3)
- fix(ui): prevent mini queue scroll jump (1309f6e9)
- fix(ui): smooth fullscreen lyrics motion (bf3e1698)
- fix(ui): synchronize lyrics scrolling motion (6d12859d)
- fix: optimze synced lyrics panel (534f68c1)
- fix(ui): stabilize mini player volume control (8ef7636c)
- docs(public): add keyboard shortcuts FAQ (5735a093)
- fix(ui): preserve landing page dropdown blur (ab9cac6b)
- fix: fix test (566fabd0)
- fix: missing lib windows (5c734d45)
- feat(ui): add fullscreen artwork backgrounds (ba4bbd16)
- fix(ui): refine mini player volume pill (adf90dd2)
- fix(app): prevent startup window and lastfm crashes (66beb76b)
- feat(mini-player): queue (d0236b79)
- feat(mini-player): lyrics (ac3d789e)
- feat(mini-player): queue - lyrics skeleton (69149174)
- feat(miniplayer): lock miniplayer ratio (bcd29be3)
- feat(ui): show mood radio in fullscreen queue (df886a46)
- feat(ui): show copyright in track info (27a78dfe)
- fix(ui): smooth living artwork background (b0f3fa47)
- fix(ui): preserve fullscreen queue scroll position (7fc00fbd)
- fix(tray-menu): populate labels immediately so tracks restored from a previous session (420aa5ba)
- fix: tray menu next track (074b2112)
- fix(ci): add --break-system-packages to pip3 install for macOS 15 runner (72f1d5c8)
- feat(android): subset Material Symbols font at build time (58179c6d)
- fix(android): synced deleted playlist (5f5eaf4b)
- refactor(ui): restructure FAQ content into subdirectories (debc71d5)
- feat: add fullscreen toglle shortcut (3149a5be)
- fix: fix landing page style (122bfa86)
- feat(mobile): fix sync favourite (dd708b49)
- ci: add retry (94bbd06a)
- fix: fix build windows ci (83980176)
- ci: update mobile ci (336a3820)
- fix: fix unit test dessktop (03653596)
- ci: remove build x86 android (ac348544)
- feat(ui): add mobile sync help links (d364ea7e)
- docs: update landing page (c555f925)
- fix(infra): rank Kugou lyric candidates (1b652b64)
- feat(app): add mobile sync storage preflight (a48799d2)
- fix: fix unit test (8114de44)
- ci(android): mobile ci (96ef99b9)
- docs: rewrite mobile catalog (3a6b7069)
- fix(mobile-sync): fix hang app desktop (ba8bae08)
- chore: update desktop app deps (128b46fe)
- fix(android): fix some weird ui bug (be205985)
- feat(android): hide favourite from playlist syncing (dee73508)
- feat(android): optimize syncing (c67dfee4)
- fix(android): fix build prod release (ef46579a)
- feat(android): insight page (ed6409ca)
- feat(insight): add filter insight of multi devices (258c6933)
- feat(android): search page (50bc2e14)
- feat(android): add tracking and syncing (74c6afa0)
- feat(desktop): add add to queue into context menu (81689066)
- feat(android): playlist context menu (78ed83af)
- feat(mobile): playlist bottom sheet (19f84db6)
- feat(android): playlist (f6f34aaf)
- fix(sync): preserve complete track metadata (43cd084b)
- chore(android): app config (c0dceeec)
- feat(ui): improve desktop synced lyrics browsing (1c439761)
- feat(mobile): sync lrc & txt lyrics (dd6dd358)
- feat(android): album details page (6ace8abf)
- feat(mobile): player engine and now playing (5ff39524)
- feat(sync): sync libary to mobile (ac502b9a)
- feat(pairing): pait with trusted device (752c5428)
- feat(pairing): handshake mobile and desktop (9b8f1cee)
- style(ui): extract dim text opacity patterns into reusable utilities (a8d12678)
- chore: mobile app skeleton (2aa8e515)
- fix(infra): rely on Windows Start Menu categorization (d312c89c)
- fix(infra): group Windows shortcut under entertainment (d0305d4b)
- fix(infra): support shortcut cleanup during uninstall (3593abfb)
- fix(infra): clean duplicate Windows shortcuts on update (ed1e79e5)
- chore: upgrade deps (60fe37b0)
- fix(player): recover missed initial lyrics events (acc3ed42)

## v1.0.5

Changes since v1.0.4.

- chore: bump version to 1.0.5 (2fd611a8)
- fix(infra): enable cache writes after merges (dfcae3f2)
- fix(ui): remove new playlist menu icon (a5b7a074)
- fix(ui): close fullscreen player with escape (4ef87857)
- feat(ui): create playlists from context menu (5a3ae18e)
- chore: craete playlist modal always auto focus (6212fb00)
- chore: remove streak hover shadow (47a43604)
- fix(player): reload edited track safely (c1a369d2)
- feat(ui): add top tracks expand toggle (360f59a0)
- fix(ui): separate mood radio menu action (36cd782b)
- fix(ui): prevent lyrics loading race (a86ac877)
- fix(lyrics): send valid lyric timestamps (9749b52c)
- fix: disable drag a tag (18e2aed6)
- fix(player): preserve resolved lyrics (f2cb9ebf)
- fix(ui): refresh cached library data after metadata edits (b348335e)
- fix(player): correlate lyrics requests (e8299a8e)
- fix(ui): improve home analytics scrolling (1c58e27e)
- fix(ui): preserve top artist artwork across pagination (0473ba46)
- test(ui): fix analytics i18n test mock (9a12613c)
- feat(analytics): track playback outcomes (7185fbf5)
- feat(ui): add analytics library growth and streak (1ff7bbf7)
- fix(infra): use complete aubio source distribution (3242adb5)
- fix(infra): download aubio from github (0569d1f5)
- docs: describe listening insights (2cebfb7e)
- test(core): isolate native menu test binary (82ba90dd)
- fix(analytics): break top track ties by listening time (8e21721b)
- fix(player): persist listening time on pause (87c9d2a2)
- chore: i18n message (17e271e8)
- feat(ui): prefer white foreground for saturated accents (71aeec69)
- feat(ui): add search result context menus (1200a6c6)
- fix(ui): show period play counts in top tracks (7d6d0dac)
- feat(analytics): expand top insight lists (c001a13d)
- fix(ui): translate track change notification settings (10273e20)
- fix(player): restore playback position after quit (083e42c8)
- fix(ui): format short durations in seconds (c2285dbb)
- fix(ui): show analytics card placeholders (f875e97a)
- fix(ui): derive opaque primary shades (cd73a5ae)
- feat(analytics): add listening insights (411e55e8)
- fix(ui): keep lossless badge rose (76d777e0)
- test(ui): mount teleported color picker input (5bdb2710)
- fix(ui): wrap primary color presets (1058305d)
- fix(ui): close fullscreen player before settings navigation (a277f92b)
- feat(ui): add configurable primary color (67c431e1)

## v1.0.4

Changes since v1.0.3.

- chore: bump version to 1.0.4 (e0fa852e)
- docs: update landing page (feaf66cf)
- fix(player): harden now playing artwork handling (4871f29b)
- chore: upgrade SFBAudioEngine 0.12.1 -> 0.13.0 (a864d810)
- test(ui): fix frontend verification mocks (ee5196af)
- fix(ui): preserve maximized window on notification (35aebcf6)
- fix(player): restore queue order and refill mood radio (4c4e8a6f)
- fix(ui): stabilize settings app icon shadow (8a2656fd)
- feat(ui): add draggable development tools overlay (76fa2b57)
- fix(eq): gate preamp and stereo width with toggle (77c6b37f)
- fix(player): shuffle only upcoming tracks (49862eff)

## v1.0.3

Changes since v1.0.2.

- chore: bump version to 1.0.3 (44e94d74)
- docs: update copyright owner to combine name and github username (2f419543)
- docs: add libogg, libvorbis, libopus, libFLAC, and WavPack to NOTICES (4e975e59)
- docs: add Wails, FFTW3, TagLib, and mpg123 to NOTICES (1509a36e)
- chore: bump wails version (fd9d3e67)
- fix(ui): use sort_title/sort_name for track and album sorting, persist tracks sort state (d6438d6e)
- fix(ui): remove primary color from playing track title in track table (85c18968)
- docs: add player quick settings to features list (c890781a)
- fix(ui): improve EntityExplorerLayout scroll-to-selected reliability (3f6a3d0e)
- fix(ui): scroll to active lyric on LyricsDrawer open (matching SyncedLyricsView) (8bc10f4f)
- fix(ui): skip reload on background library:sync-finished to prevent startup flash (71a91b0f)
- feat(ui): add option to open playback settings from quick settings menu (d8a928db)
- fix(player): focus main window from notifications (5cb03f99)
- feat(player): add macos track advance notifications (773797d5)
- fix(macos-miniplayer): preserve mini player pin on macos dock activation (d7a23f8b)
- refactor(lyrics): optimize gpu render for synced lyrics (0b39f223)
- feat(ui): cache sidebar width (78722e57)
- fix(lyrics): fix font jitter synced lyrics (f55f854d)
- chore(artwork): recvert logic palette color (28823d5d)
- ci: remove standalone bin for windows (b9797a09)
- chore(artwork): recvert logic palette color (21255e49)
- fix(ui): scroll to selected item in entity explorer layout when navigating from elsewhere (cba8ff44)
- feat(ui): resolve stereo width visual confusion with localized labels and improved arcs (01a93b21)
- feat: support html xml syntax in lrc (f3875eb6)
- feat(player): add preamp and stereo width to eq panel (abe203fb)
- feat(ui): add EQ toggle to quick settings and synchronize panel UI (cda56506)
- feat(player): add player quick settings context menu (ae481651)
- fix(ui): keep track table header index sticky (03c94b20)
- fix(ui): scroll synced lyrics on initial layout (301ea712)
- fix(player): preserve playback when starting mood radio (34d00fc2)
- feat(app): add brightness to mood playlists (1b08e533)
- feat(analysis): version analysis by source (262409cb)
- feat(app): vary mood radio queues (18f1c8bf)
- feat(app): redefine application menu (4503e399)
- chore(player): change default crossfade duration from 6 to 4 (be5c4463)
- fix(infra): prevent micro-logo from hijacking vibrant accent color (35a07c9b)
- chore: fix unit test (3d924371)
- docs: update readme hero img (52c12b71)
- feat(player): add immersive lyrics (46e7aa59)
- feat(ui): sync artwork blend with crossfade (50073c6e)
- feat(player): resyncPreQueue when modifing queue (5d5882ea)
- fix(ui): restore fullscreen queue playback (868a7dd5)
- feat(analysis): make library-analysis worker count user-configurable (11b44443)
- fix(infra): build aubio against fftw3f (ad53845f)
- fix(analysis): cap workers and deprioritize to stop laptop freeze (523f67c8)
- feat(player): cross fade (69a5591a)
- chore: optimize font for windows (1019b979)
- fix(frontend): remove Google Fonts Inter to fix italic text on WKWebView (fb158428)
- perf: split playlist detail fetch into metadata + tracks phases (04c4abb7)
- fix: reload detail pages when KeepAlive reactivates a stale cached instance (5641f815)
- fix: hide remove-from-playlist action for smart playlist tracks (e7bfb2c5)
- fix: guard detail-page loads against stale out-of-order responses (49084399)
- fix: hide smart playlists from add-to-playlist context menus (62999e27)
- perf: virtualize album/track list in artist/genre/composer detail views (b99bcad5)
- feat: add max queue size setting (133cb6e7)
- fix: teleport filter submenu panel to match level-1 glass background (9462b42b)
- fix: add missing aubio-windows build job to release workflow (ac5f856e)

## v1.0.2

Changes since v1.0.1.

- chore: bump version to 1.0.2 (2d92f47d)
- fix: remove heatmap rounding and selection border clipping (9cb74043)
- docs: remove tracks image in hero docs (2460b8ed)
- fix: truncate tray menu track labels to 40 chars (e0752a44)
- docs: convert image to webp (8cd5a97c)
- docs: add keyboard shortcuts and screenshot gallery to README (0283716f)
- feat(lyrics): add optional .lrc file save to Find Lyrics dialog (b598ec27)
- chore: change eq switch icon (ce134256)
- fix: select type (f90107c1)
- chore: bump deps (469f8e74)
- feat(landing): add mood/smart playlist + EQ feature cards, fix license to GPL-3.0 (10394da2)
- fix(audio): serialize ffmpeg probe/open across analyzer and player (4e7919fc)
- chore: update home most played title (8f4acbce)
- chore: optimize details page responsive (4dacc6ee)
- fix(player): start playback from queue when no track is loaded (ce5d251a)
- feat(library): add sort by date added and configurable columns for albums/tracks (1071dad2)
- fix: fix dialog layout (4554c3da)
- fix(library): prevent duplicate sync runs and show button busy during background sync (694da5fa)
- ci: bump aubio/ffmpeg cache keys to force rebuild (864f4f96)
- fix(playlists): stop live artwork/mosaic updates from going stale in cached grid view (f1fb5414)
- feat(playlists): let Favorites use custom artwork (b726361d)
- fix(analysis): serialize ffmpeg probe/open and aubio calls to stop crash (d78c193e)
- feat(playlists): add mood-based smart playlists (energy x danceability picker) (a8e2f1cf)
- chore(license): fix GitHub license detection, add aubio notice (dddf1d80)
- refactor(analysis): drop unused musical-key/mode/valence features (f2e3a281)
- fix(queue): make full row clickable in queue drawer (771a5d33)
- feat(playlists): add smart playlists with rule-based filtering (3e0a7c9b)
- fix(ui): add solid primary-100/200 selected-row colors, fix tsconfig deprecations, upgrade toolchain (a1dc206d)
- fix(albums): stop album detail hero from getting stuck on scroll (8eca07bc)
- feat(playlists): add pinned playlists, grid view, and play/shuffle context menu (adfaa2ec)
- fix(player): optimize shuffle event payload (dd5a39d4)
- refactor: refactor layout album details view (e8d46a78)
- feat: increase tracks batch size (b0b2e0e1)
- fix(library): serialize bulk sync ops to stop DB-lock storm and crash (8d43a4c1)
- fix(tracks): avoid too-many-variables error on GetByIDs for large libraries (a68d6d1a)
- feat(library): replace file watcher with periodic sync scheduler (4906761a)
- fix(albums): remove double padding wrapper on album list view (20c19b98)
- feat(track-info): add tiered audio quality badge (Lossy/Lossless/Hi-Res/DSD) (7279981d)
- chore(frontend): bump vite/typescript/vue-tsc toolchain, generic EntityExplorerLayout (e80be0ae)
- chore: relayout find lyrics (574e21b0)
- feat(context-menu): add Add to Queue option for tracks (52e2cec6)
- fix(mood-radio): clear radio state when normal playback starts (3775b51e)
- fix(album-filter): drop primary color on active sort label and icon (edce5f22)
- fix(grid): full-bleed scrollbar, scrollable edge padding, stable resize (23deb0c1)
- refactor(mini-player): replace WebGL blur with CSS glassmorphism panel (ce3bb03f)
- docs: rename AGENT.md to AGENTS.md, drop GEMINI.md (fe3cd957)
- fix(analysis): mood pipeline correctness + Last.fm favorite-only love sync (5f6e451a)
- chore: upgrade wails (df19a3cf)
- chore(license): switch to GPL-3.0 for FFTW3 compliance, add Mood Radio to README (5c24f524)
- feat(analysis): fetch progress snapshot on mount instead of event-only (2c35561d)
- feat(mood): trigger percentile recompute right after a folder sync finishes (4b682ffb)
- fix(mood): don't derive energy/danceability against empty percentile cache (f44a5e36)
- fix(scripts): resolve on-demand FFTW3 build via absolute path, assert output (2c6e48ab)
- ci: bust poisoned keyfinder cache, save right after build (24bcbb41)
- ci: install g++-aarch64-linux-gnu, set CXX for Linux ARM64 cross-build (64bede15)
- ci: save native dep caches immediately after build (18c36e94)
- ci(build): install g++-mingw for keyfinder Windows cross-build (61558ea3)
- fix(scripts): correct libkeyfinder tag URL, fail-fast on bad download (9f5d9242)
- ci: build keyfinder + FFTW3 native libs, grant cache write scope (ace79c6c)
- fix(search): autofocus search input on mount and keep-alive activation (47cc8b8f)
- feat: add keyfinder/mood-radio analysis pipeline, debounced percentile recompute (6a59ea15)
- fix: batch remaining unbatched delete/resplit writes, bound analysis hangs (2ad573b7)
- fix(library): stop analysis pool from blocking track/folder deletion, fix slow removal and resync (6ae6a067)
- fix: add SFBAudioEngine rpath, fix case-insensitive lyrics lookup, doc volume normalization (09dfc144)
- ci: build aubio for Linux CI job, bump ffmpeg cache key (4d7cabfc)
- refactor(ui): extract shared SettingSection/SettingRow components (0b7dd0f5)
- feat(eq): enlarge EQ panel sliders and add scale marks (62c61dd7)
- ci: transfer SFB darwin build via cache restore, not artifact (eb9f4f77)
- ci: fix runner.os comparison — value is 'macOS' not 'Darwin' (f94d5f50)
- ci: drop redundant nasm from Linux matrix job apt install (d5e7d30b)
- feat(analysis): add audio analysis pipeline + volume normalization (564adba3)
- fix(lyrics): resolve lyric loading issues, prevent flashing, and display duration (89a51960)
- docs(search): update search catalog mapping and lifecycle specifications (91ebb095)
- fix(search): use custom analyzer without stop words and recreate index on reindex (a7370106)
- fix(lyrics): restore lyrics on app reopen after session restore (51f73f78)
- chore: stop tracking .turbo cache directories (6f7042ea)
- fix(nowplaying): register Start Menu shortcut with AUMID for SMTC name (26c6fecc)
- perf(library): parallel-hybrid folder sync (31200948)
- fix(nowplaying): show Airmedy as app name in Windows SMTC (1049abe6)
- feat(library): user-configurable tag delimiters with delimiter-aware sync (5f776215)
- fix(lyrics): stop online fetch when prefer-local lyrics present (6c275f21)
- feat(ui): add mouse scroll support to volume sliders (6660f0da)
- feat(miniplayer): remember position, size, pin mode across restarts (06afe58b)
- fix(audio): resume from paused position on macOS instead of restarting (af093001)
- fix(ui): hide show tray icon option on Windows/Linux, force-enable if disabled (d5b63c5e)
- chore: remove deprecated pnpm field from package.json (3887f088)
- fix(infra): detect network interface type accurately per OS (6a59376d)
- fix(ui): fix blank space at bottom of track table in compact mode (7a3dcfc7)
- docs(landing-page): add download option for windows arm64 (c14fcc67)

## v1.0.1

Changes since v1.0.0.

- chore: bump version to 1.0.1 (8b70d483)
- docs: update catalog for remote feature (ecade666)
- fix: drawer header height (940fc5c8)
- feat(remote-ui): add network interface icons and tooltips to access URLs layout (899dd181)
- feat(remote): classify network interfaces and selectable QR URL (5da8ad66)
- feat(artwork): strict online gate + prefer-local artist artwork toggle (deaeb7b4)
- fix: prune orphaned entities and refresh views after metadata edit (55ca360b)
- fix: fs watch when update metadata (6bfedea2)
- docs(catalog): document GetArtistColors and per-view hero tint (f6757ef9)
- feat(artist): tint hero from artist artwork colors (971b0532)
- fix(lyrics): make synced view auto-scroll reliably (08331759)
- feat(windows): add taskbar thumbnail toolbar media controls (cff0a8fe)
- docs(architecture): sync service inventory with code (cd52ad30)
- fix(audio): play DSD files by wrapping in PCM decoder on macOS (995333e2)
- feat(track-info): show album artist, hide empty detail rows (b2c8aa84)
- chore: upgrade deps (b1db8c58)
- fix(search): batch reindex to stop Windows hang on optimize (a9410a73)
- refactor(ui): nest lyrics sub-settings in expandable rows (a601a127)
- feat(lyrics): per-track lyrics subfolder option (ffcc4774)
- feat(lyrics): optional dedicated lyrics folder (d6197762)
- feat(artist): local + custom artist images with multi-source artwork (237e8291)
- ci: silence clang dllexport-redeclaration warnings on Windows ARM64 (5335c049)
- fix(ffmpeg): fresh source tree per Windows arch (4d20c6c4)
- ci: bust stale Windows FFmpeg cache (v2) (062c5015)
- ci: group libav archives for Windows ARM64 link (a7d967c3)
- ci: set arm64 CXX for Windows ARM64 link (1970cce0)
- ci: cross-compile Windows ARM64 with llvm-mingw (4a8107c4)
- ci: use llvm-mingw for Windows ARM64 ffmpeg cross-compile (301ab41f)
- fix(lyrics): honor local-lyrics preference deterministically (2cdc032a)
- feat(lyrics): support local .lrc/.txt sibling files (b74afe21)
- ci: apt-get update before installing mingw cross-compilers (404c0e7b)
- ci: build all platforms on PR merge without release (1b391bef)
- fix(ui): enlarge fullscreen lyrics/queue panels with balanced spacing (c2f4f9f6)
- fix(ui): scale fullscreen player artwork and gaps to viewport height (91f530ed)
- fix(ui): restore main-window titlebar double-click, disable in miniplayer (e4bcc3d1)
- fix(ui): make window drag overlay click-through, hide in mini mode (37d9c661)
- feat(library): download original artwork from metadata edit dialog (df281aae)
- fix(ui): keep modal open when text-selection drag ends on backdrop (a8f891d8)
- docs: refresh catalog + README for now-playing, updater, single-instance, windows arm64 (40f0bdc1)
- fix: tray show airmedy reveals only active window (92637c3e)
- chore: fix package windows on local (39ab2d8a)
- feat(ui): match windows title bar color to app theme (85c831d9)
- fix: can not drag and drop miniplayer on windows (f714750e)
- feat: support windows arm64 (c7253d10)

## v1.0.0

Changes since v0.1.14.

- chore: bump version to 1.0.0 (613e500d)
- feat: recently added shows tracks instead of albums (f4fc2a3c)
- fix: restart playback after Next on last track with repeat off (ef66f389)
- ci: build windows (225bfeea)
- chore: fix ci build windows (e8edc30c)

## v0.1.14

Changes since v0.1.3.

- chore: bump version to 0.1.14 (dbfb2e5e)
- feat(player): now playing windows (bf685db8)
- fix: single instance linux windows (80fbbe0c)
- chore: fix go lint (584bcb20)
- feat(auto-update): render changelog using markdown (8e0123b3)
- feat(player): now playing linux (2abed563)
- fix: single instance linux windows (06d73064)

## v0.1.3

Changes since v0.1.2.

- chore: bump version to 0.1.3 (463c9a4e)

## v0.1.2

Changes since v0.1.12.

- chore: bump version to 0.1.2 (1144d7be)
- fix(ci): serialize pnpm install and dedupe AppImage StartupWMClass (ef6346f7)

## v0.1.12

Changes since v0.1.1.

- chore: bump version to 0.1.12 (932852fc)
- fix(auto-update): fix auto update on windows and linux (7b97d474)

## v0.1.1

Changes since v0.1.0.

- chore: bump version to 0.1.1 (8e3aa093)
- fix: backend i18n on windows (e1c2da2b)
- chore(ci): update windows build (f3a10543)

## v0.1.0

Changes since v0.0.13.

- chore: bump version to 0.1.0 (ea15b8c7)
- fix: build linux (51337c3a)
- fix(ci): combined SHA256SUMS without self-inclusion (eb7d5ce4)
- ci: unify release into single tag-driven workflow (08db44ea)
- fix: header button z-index (01aba95c)
- chore: upgrade wails version (ed269de2)
- fix(library): refresh views and clear orphaned albums on track delete (bbdc871d)
- chore: bump version to 0.0.14, download to 0.0.13 (b8e4031d)

## v0.0.13

Changes since v0.0.12.

- feat(library): add album list layout (6292e833)
- refactor(ui): rounded full input (33aa62fe)
- chore: bump version to 0.0.13, download to 0.0.12 (e35a7f0c)

## v0.0.12

Changes since v0.0.11.

- feat(player): add player indicator button (61de09f0)
- chore: fix app folder config (4e2522de)
- chore: fix unit test (74d367e2)
- fix: open miniplayer always checks for update (0a60e743)
- feat(remote): migrate playback position tracking to requestAnimationFrame interpolation (edc5c72d)
- feat: migrate playback position tracking to frontend interpolation (a6e8f97f)
- refactor: refactor about setting layout and add missing i18n keys (58be5ff5)
- chore: remove unused deps (bf10b6e1)
- refactor: optimize remote api (9eb9ad1f)
- refactor(frontend): refactor to monorepo (7d72f812)
- feat(remote): add remote server (4069930d)
- feat(player): prevent sleep while playing (c6c0e8bd)
- docs: fix download button (a1139637)
- chore: bump version to 0.0.12, download to 0.0.11 (54887460)
- chore: add bump version script (867c85a2)

## v0.0.11

Changes since v0.0.10.

- chore: fix go lint (eb64cb66)
- docs: update catalog (d2496a4f)
- fix(ui): fix modal contrast (dbda5e7b)
- fix(update): fix auto update macos (271b6413)
- fix(updater): implement bundle-level update on macOS to preserve code signature (de566141)
- feat(meta): implement robust auto-update with signing, notarization, and progress reporting (48d5bcc9)
- feat(ui): implement mini player window recreation on open/close (40137a21)
- fix(library): fix add multi tracks to playlist (0b1ce411)
- feat(ui): add toggle hide/show tray icon (44650613)
- feat(ui): add i18n backend (58bdd99e)
- docs: update landing page (c1e00f70)
- feat(ui): add compact mode option for track table (66b504ca)
- fix(ui): apply GPU acceleration and isolation to all global overlays (8d7a9ea2)
- fix(ui): eliminate opacity and backdrop-blur flickering in WebKit (0e376d2b)
- feat(lyrics): add manual lyrics search from LRCLIB and KuGou with in-memory settings caching (f2a8ab4f)
- fix(player): reset position to 0 when track changes to prevent stale progress display (20801312)
- style(ui): consolidate dialogs under Modal component with synchronized transitions (da1f3116)
- fix(settings): close fullscreen and mini player when opening settings (6a143c4e)
- fix(player): fix floating minplayer background (cafb25f5)
- fix(player): fix miniplayer randomly not opening (5632621a)
- fix(library): fix detail hero color (e39bd897)
- chore: bump version to 0.0.11 (abcc3ee0)

## v0.0.10

Changes since v0.0.9.

- fix(player): re-sync gapless pre-queue on repeat mode change (c69164e9)
- chore: upgrade wails (21d3a6a1)
- ci: update changelog generator (641d0f13)
- feat(player): add gapless playback (403049b7)
- ci(build): update github ci for new sfb (9f82dd79)
- chore: remove miniaudio.h from source (e7956089)
- feat(player-macos): migrate to SFBAudioEngine (fc732729)
- fix(player-macos): track cannot be sought from 'now playing' (8ab34778)
- fix(player): fix remove all tracks from queue behavior (de5c3e02)
- feat: add OLED theme, remap dark to gray background (9805024a)
- chore: add clear button to input (ef47068a)
- fix(auto-update-darwin): fix auto update not patch Info.plist (1900c2a7)
- chore: improve the readability of synced lyrics (1b419a17)
- fix(player): fix living artwork contrast (166614cd)
- docs: update catalog (8d4c13b7)
- fix(player): fix play/shuffle action behavior (4ec5924a)
- chore: bump version to 0.0.10 (4c3e2314)

## v0.0.9

Changes since v0.0.8.

- fix(ui): fix player ui (3fef4fe9)
- fix(player): fix remove track from queue (81aacd55)
- fix(macos-player): fix open mini player when app is in fullscreen mode (3f39056a)
- feat(queue): add scroll to playing track button (8685df31)
- fix(library-ui): fix playing track animation (3d8d03d8)
- fix(setting): fix syncing setting library (7cc04f06)
- feat: add kugou lyrics provider (f2321d34)
- chore: bump version to 0.0.9 (0cee8252)
- fix(ci): fix github ci (b53e603d)

## v0.0.8

Changes since v0.0.7.

- fix(ci): fix github ci (739ba8f5)
- chore(frontend): improve memory usage in frontend (dc55e474)
- chore: bump version to 0.0.8 (308c26ee)

## v0.0.7

Changes since v0.0.6.

- fix: improve artist extraction and relation queries (f6aba33c)
- feat(library): implement online artist artwork fetching via Deezer (c977be0e)
- fix(lyrics): ensure persistence and improve fallback logic (9bc1a2e8)
- docs(meta): update catalog for metadata lyrics and lrclib_mode (4e510dce)
- feat(player): support metadata lyrics (960a687c)
- chore(build-ci): update macos version (49f93c50)
- fix(ui): fix memory leaks in Pinia stores and Vue components (41806bcf)
- fix(audio): fix CGO memory leaks in darwin player (2fd4955d)
- docs: update readme (ece4e3d1)
- chore: bump version to 0.0.7 (8b54b60f)

## v0.0.6

Changes since v0.0.5.

- chore: update wails version (95975128)
- fix: fix auto update not update info.plist (62b622dd)
- chore: bump version to 0.0.6 (eb86725d)

## v0.0.5

Changes since v0.0.4.

- fix(eq): fix state persistence when switching between profiles (7b6acbe2)
- feat(eq): persist equalizer enabled/disabled state across app restarts (f8755491)
- fix(ui): update favorite icons and protect favorites playlist (a10c42f4)
- chore: bump version to 0.0.5 (b6580f02)
- feat(library): implement multi-track selection and bulk context menu actions (51c3b1cf)
- fix(library): fix can not add track to empty playlist (f96b73e0)
- feat(library): implement accent-insensitive filtering (58a6d2af)
- fix(library): fix sync library (d3cf5846)

## v0.0.4

Changes since v0.0.3.

- chore: bump wails version to alpha.84 (649ecbf9)
- chore: bump version to 0.0.4 (eeb54edf)
- refactor(playlist): migrate track positioning to LexoRank (16e1e3ec)
- feat(library): update dnd virtual scroll (c66f0b2a)
- feat(player): implement dnd for queue (5138d7aa)
- feat(library): migrate vue-virtual-scroller (e52f1760)

## v0.0.3

Changes since v0.0.2.

- ci: lint & build ci (dc4dd50d)
- feat(library): update metadata (f4ba6686)
- feat(setting): add auto update (4e05418c)

## v0.0.2

Changes since v0.0.1.

- chore: bump version (80b22025)
- feat(update): auto update from github release (bcbd1530)
- fix: simple single instance (bb9b5211)
- fix(build): fix build windows (7443f79d)
- fix(ui): fix window not show when start app on windows (12555a1d)
- fix(player): fix playing status (0c33c116)
- chore(build): remove build ios android (0844632b)
- doc(web): add landing page (0ad37556)
- chore: refactor log module (f32f9c8c)
- feat(library): import playlist m3u8 (11e9796e)
- feat(library): export playlist m3u8 (8fa25a00)
- ci: fix windows build (1b059a71)
- chore(ci): fix build windows (736de674)
- feat(player): integrate last.fm (66c2d10b)
- fix(ci): build linux arm64 (18a3836d)
- fix(ci): fix build windows (dc667876)
- fix(library): fix cascade detele (e3d6e74a)
- chore(ci): fix build windows (11eefcae)
- feat(player): add shortcuts (2ab90143)
- feat(player): enhance seek control (9c08a97f)
- fix(playlist): fix ui contrast (576e0541)
- fix(player): fix deadlock miniaudio (2ed0f5f4)
- ci: fix ci build (77dfae14)
- fix(ui): fix low contract light mode (da5f6671)
- ci: ađ github ci (d4ffbdc0)

## v0.0.1

Initial release.

- ci: ađ github ci (64c4e986)
- feat(player): implement eq for windows and linux (dacdb584)
- fix(player, linux, windows): create ffmpeg stream (2233649c)
- fix(player): create ffmpeg stream (26044b6b)
- fix(player): fix build ffmpeg (2c6303a6)
- fix(library): fallback to filename if missing in metadata (182427af)
- docs: add catalog (b7eba3a3)
- docs(readme): update readme (e28f96e3)
- chore(ci): fix build windows (64bcfa09)
- feat(player): add support ogg opus ape dsd (6cda2017)
- feat(optimize): optimize artwork size (db159be2)
- feat(optimize): optimize artwork size (938c8f86)
- feat(optimize): optimize large list ref (ab3bf4b9)
- feat(optimize): optimize queue memory (b9b74ccb)
- feat(optimize): optimize memory usage (ddc8a759)
- feat(library): enhance add-remove track from playlist (265c250e)
- refactor(library): add multi search index tag (1e81a0d5)
- fix(player): fix track table ui fullscreen player (76b19ab7)
- fix(player): fixed a bug where playback wasnt being saved when quitting the app (4b8d4db9)
- feat(library): implement home view (ed6571aa)
- feat(library): make track table can draggable and sortable (5ed01003)
- refactor(library): refactor album and track header ui (0f4a32f0)
- fix(select): fix select dropdown too long (5d5343ea)
- chore(ui): change default font (9e7ee16b)
- feat(player): add option refresh lyrics (701c6599)
- feat(eq): allow add eq profile (17702ae5)
- chore(player): update miniaudio version (f2fec502)
- feat(player): add player control to tray menu (dc70410d)
- chore(config): update app detail (0c5e36ec)
- chore(build): remove gitignore (6678c86a)
- fix(player): fix show 2 drawers (ce4c90f0)
- fix(linux): fix webgl live artwork for linux (d05cd1af)
- chore(taglib): add log debug (6b865d6c)
- feat(track-info): add some fields (dc109abb)
- refactor(playlist): refactor ui playlist (1f33f202)
- refactor(library): optimize artist, album, genre, composer details (e8ccb567)
- refactor(player): refactor lytics (7084762b)
- feat(layout): add track info drawer (f4a104b3)
- refactor(player): refactor fullscreen player (292abfdb)
- fix(player): live artwork doesnt sycn when quit app (1b916d55)
- refactor(sidebar): refactor sidebar ui (0f79a23d)
- refactor(setting): refactor ui setting (cb98c449)
- feat(setting): fix update language not sync to ui (a0e1e2d8)
- chore(ci): fix build windows (ce7a6038)
- feat(player): add ffmpeg for linux and windows (9b9bf5cf)
- feat(player): fix miniaudio file path (723aff94)
- chore(ci): add github ci (7bc39710)
- feat(player): support windows and linux using miniaudio (ad57445e)
- fix(player): fix miniplayer doesnt sync data (70437b5c)
- feat(ui): support light-dark theme (98138465)
- feat(ui): selection (6c721c98)
- feat(setting): implement i18n (4bf915a6)
- feat(player): miniplayer (fea0e0c4)
- feat(player): cache player state when quit app (c7855da6)
- feat(track): track context menu (d8dd4f56)
- chore(app): update go module name (a0e939b3)
- feat(lyrics): synced lyrics (161c31de)
- feat: implement Phase 7 (search, playlists, equalizer, lyrics) (ac468b05)
- feat(fullscreen): refine layout transitions and stabilize content (5c2a877a)
- feat(player): queue sidebar (61b7f534)
- feat(ui): refactor player (bb11f4b4)
- feat(player): live player (11b5f578)
- feat(player): implement player and queue (8be91629)
- feat(core): implement audio playback engine and player service (360e75d6)
- feat(ui): implement grid virtualization and refactor album views (8ea9068e)
- feat(ui): fix album artwork and implement clickable artist navigation (8a3131fc)
- refactor(infra): consolidate migrations and remove unused columns (40c8d640)
- feat(core): implement entity normalization, many-to-many tags, and base UI architecture (9c8c40cb)
- docs(core): update development plan and file structure documentation (503bc79e)
- feat(core): implement library synchronization and folder watching (15dc0ef0)
- chore(app): setup log fx (78b9a650)
- feat(core): implement phase 2 - data layer, search, and metadata extraction (c2cf42fd)
- chore(app): init (12ff3b8f)
