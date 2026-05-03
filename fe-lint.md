Run wails3 task test:ui
task: [test:ui] pnpm test
> frontend@0.0.3 test /home/runner/work/airmedy/airmedy/frontend
> vitest run
 RUN  v1.6.1 /home/runner/work/airmedy/airmedy/frontend
 ❯ src/stores/player.test.ts  (11 tests | 1 failed) 27ms
   ❯ src/stores/player.test.ts > usePlayerStore > returns null artworkUrl when no currentTrack
     → expected undefined to be null
stdout | src/stores/player.test.ts > usePlayerStore > init fetches status, theme and queue from backend
[PlayerStore] Initializing...
 ❯ src/components/PlayerFooter.spec.ts  (0 test)
 ❯ src/components/MetadataEditDialog.spec.ts  (6 tests | 3 failed) 206ms
   ❯ src/components/MetadataEditDialog.spec.ts > MetadataEditDialog > renders form when open
stderr | src/components/MetadataEditDialog.spec.ts > MetadataEditDialog > renders form when open
[intlify] Not found 'library.edit_metadata' key in 'en' locale messages.
[intlify] Not found 'common.change' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.track' key in 'en' locale messages.
[intlify] Not found 'track_info.bpm' key in 'en' locale messages.
[intlify] Not found 'library.total' key in 'en' locale messages.
[intlify] Not found 'library.disc' key in 'en' locale messages.
[intlify] Not found 'library.total_discs' key in 'en' locale messages.
[intlify] Not found 'common.cancel' key in 'en' locale messages.
[intlify] Not found 'common.save' key in 'en' locale messages.
stderr | src/components/MetadataEditDialog.spec.ts > MetadataEditDialog > initializes title input from track
[intlify] Not found 'library.edit_metadata' key in 'en' locale messages.
[intlify] Not found 'common.change' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.track' key in 'en' locale messages.
[intlify] Not found 'track_info.bpm' key in 'en' locale messages.
[intlify] Not found 'library.total' key in 'en' locale messages.
[intlify] Not found 'library.disc' key in 'en' locale messages.
[intlify] Not found 'library.total_discs' key in 'en' locale messages.
[intlify] Not found 'common.cancel' key in 'en' locale messages.
[intlify] Not found 'common.save' key in 'en' locale messages.
stderr | src/components/MetadataEditDialog.spec.ts > MetadataEditDialog > calls UpdateTrackMetadata on save
[intlify] Not found 'library.edit_metadata' key in 'en' locale messages.
[intlify] Not found 'common.change' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.track' key in 'en' locale messages.
[intlify] Not found 'track_info.bpm' key in 'en' locale messages.
[intlify] Not found 'library.total' key in 'en' locale messages.
[intlify] Not found 'library.disc' key in 'en' locale messages.
[intlify] Not found 'library.total_discs' key in 'en' locale messages.
[intlify] Not found 'common.cancel' key in 'en' locale messages.
[intlify] Not found 'common.save' key in 'en' locale messages.
[intlify] Not found 'library.edit_metadata' key in 'en' locale messages.
[intlify] Not found 'common.change' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.track' key in 'en' locale messages.
[intlify] Not found 'track_info.bpm' key in 'en' locale messages.
[intlify] Not found 'library.total' key in 'en' locale messages.
[intlify] Not found 'library.disc' key in 'en' locale messages.
[intlify] Not found 'library.total_discs' key in 'en' locale messages.
[intlify] Not found 'common.cancel' key in 'en' locale messages.
[intlify] Not found 'common.saving' key in 'en' locale messages.
[intlify] Not found 'library.edit_metadata' key in 'en' locale messages.
[intlify] Not found 'common.change' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.track' key in 'en' locale messages.
[intlify] Not found 'track_info.bpm' key in 'en' locale messages.
[intlify] Not found 'library.total' key in 'en' locale messages.
[intlify] Not found 'library.disc' key in 'en' locale messages.
[intlify] Not found 'library.total_discs' key in 'en' locale messages.
[intlify] Not found 'common.cancel' key in 'en' locale messages.
[intlify] Not found 'common.save' key in 'en' locale messages.
stderr | src/components/MetadataEditDialog.spec.ts > MetadataEditDialog > emits update:open=false after successful save
[intlify] Not found 'library.edit_metadata' key in 'en' locale messages.
[intlify] Not found 'common.change' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.track' key in 'en' locale messages.
[intlify] Not found 'track_info.bpm' key in 'en' locale messages.
[intlify] Not found 'library.total' key in 'en' locale messages.
[intlify] Not found 'library.disc' key in 'en' locale messages.
[intlify] Not found 'library.total_discs' key in 'en' locale messages.
[intlify] Not found 'common.cancel' key in 'en' locale messages.
[intlify] Not found 'common.save' key in 'en' locale messages.
[intlify] Not found 'library.edit_metadata' key in 'en' locale messages.
[intlify] Not found 'common.change' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.track' key in 'en' locale messages.
[intlify] Not found 'track_info.bpm' key in 'en' locale messages.
[intlify] Not found 'library.total' key in 'en' locale messages.
[intlify] Not found 'library.disc' key in 'en' locale messages.
[intlify] Not found 'library.total_discs' key in 'en' locale messages.
[intlify] Not found 'common.cancel' key in 'en' locale messages.
[intlify] Not found 'common.saving' key in 'en' locale messages.
[intlify] Not found 'library.edit_metadata' key in 'en' locale messages.
[intlify] Not found 'common.change' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.track' key in 'en' locale messages.
[intlify] Not found 'track_info.bpm' key in 'en' locale messages.
[intlify] Not found 'library.total' key in 'en' locale messages.
[intlify] Not found 'library.disc' key in 'en' locale messages.
[intlify] Not found 'library.total_discs' key in 'en' locale messages.
[intlify] Not found 'common.cancel' key in 'en' locale messages.
[intlify] Not found 'common.save' key in 'en' locale messages.
stderr | src/components/MetadataEditDialog.spec.ts > MetadataEditDialog > closes on Cancel click
[intlify] Not found 'library.edit_metadata' key in 'en' locale messages.
[intlify] Not found 'common.change' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.title' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.artist' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.album' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.genre' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'library.composer' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.label' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'track_info.isrc' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.year' key in 'en' locale messages.
[intlify] Not found 'library.track' key in 'en' locale messages.
[intlify] Not found 'track_info.bpm' key in 'en' locale messages.
[intlify] Not found 'library.total' key in 'en' locale messages.
[intlify] Not found 'library.disc' key in 'en' locale messages.
[intlify] Not found 'library.total_discs' key in 'en' locale messages.
[intlify] Not found 'common.cancel' key in 'en' locale messages.
[intlify] Not found 'common.save' key in 'en' locale messages.
     → expected 'library.edit_metadatacommon.changelib…' to contain 'metadata.edit_title'
   ❯ src/components/MetadataEditDialog.spec.ts > MetadataEditDialog > initializes title input from track
     → expected '' to be 'My Song' // Object.is equality
   ❯ src/components/MetadataEditDialog.spec.ts > MetadataEditDialog > calls UpdateTrackMetadata on save
     → expected "spy" to be called with arguments: [ 'track-1', Any<MetadataUpdate> ]
Received: 
  1st spy call:
  Array [
    "track-1",
-   Any<MetadataUpdate>,
+   Object {
+     "AlbumTitle": "My Album",
+     "Artist": "Artist One",
+     "ArtworkData": "",
+     "ArtworkMIME": "",
+     "BPM": 0,
+     "Composer": "Composer A",
+     "DiscNumber": 1,
+     "Genre": "Rock",
+     "ISRC": "",
+     "Label": "",
+     "Title": "My Song",
+     "TotalDiscs": 1,
+     "TotalTracks": 10,
+     "TrackNumber": 3,
+     "Year": 2024,
+   },
  ]
Number of calls: 1

 ❯ src/composables/useTrackContextMenu.spec.ts  (3 tests | 3 failed) 20ms
   ❯ src/composables/useTrackContextMenu.spec.ts > useTrackContextMenu > excludes "Play Next" if track is currently playing
     → [vitest] No "GetPlaylistsForTrack" export is defined on the "../../bindings/airmedy/internal/infra/wails/playlistservice" mock. Did you forget to return it from "vi.mock"?
If you need to partially mock a module, you can use "importOriginal" helper inside:

   ❯ src/composables/useTrackContextMenu.spec.ts > useTrackContextMenu > includes "Play Next" if track is not currently playing
     → [vitest] No "GetPlaylistsForTrack" export is defined on the "../../bindings/airmedy/internal/infra/wails/playlistservice" mock. Did you forget to return it from "vi.mock"?
If you need to partially mock a module, you can use "importOriginal" helper inside:

   ❯ src/composables/useTrackContextMenu.spec.ts > useTrackContextMenu > excludes "Play Next" if excludePlayNext option is true
     → [vitest] No "GetPlaylistsForTrack" export is defined on the "../../bindings/airmedy/internal/infra/wails/playlistservice" mock. Did you forget to return it from "vi.mock"?
If you need to partially mock a module, you can use "importOriginal" helper inside:

 ✓ src/components/ContextMenu.spec.ts  (7 tests) 86ms
 ❯ src/components/QueueDrawer.spec.ts  (0 test)
 ✓ src/stores/device.test.ts  (4 tests) 12ms
stdout | src/stores/device.test.ts > useDeviceStore > init identifies mac platform
stderr | src/stores/device.test.ts > useDeviceStore > init identifies mac platform
Failed to check window state TypeError: __vite_ssr_import_2__.Window.IsMaximised is not a function
    at checkFullscreen (/home/runner/work/airmedy/airmedy/frontend/src/stores/device.ts:18:34)
    at Proxy.init (/home/runner/work/airmedy/airmedy/frontend/src/stores/device.ts:32:7)
    at /home/runner/work/airmedy/airmedy/frontend/src/stores/device.test.ts:53:5
    at runTest (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/@vitest+runner@1.6.1/node_modules/@vitest/runner/dist/index.js:781:11)
    at runSuite (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/@vitest+runner@1.6.1/node_modules/@vitest/runner/dist/index.js:909:15)
    at runSuite (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/@vitest+runner@1.6.1/node_modules/@vitest/runner/dist/index.js:909:15)
    at runFiles (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/@vitest+runner@1.6.1/node_modules/@vitest/runner/dist/index.js:958:5)
    at startTests (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/@vitest+runner@1.6.1/node_modules/@vitest/runner/dist/index.js:967:3)
    at file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/vitest@1.6.1_@types+node@25.6.0_jsdom@24.1.3_lightningcss@1.32.0/node_modules/vitest/dist/chunks/runtime-runBaseTests.oAvMKtQC.js:116:7
    at withEnv (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/vitest@1.6.1_@types+node@25.6.0_jsdom@24.1.3_lightningcss@1.32.0/node_modules/vitest/dist/chunks/runtime-runBaseTests.oAvMKtQC.js:83:5)
stderr | src/stores/device.test.ts > useDeviceStore > init identifies windows platform
Failed to check window state TypeError: __vite_ssr_import_2__.Window.IsMaximised is not a function
    at checkFullscreen (/home/runner/work/airmedy/airmedy/frontend/src/stores/device.ts:18:34)
    at Proxy.init (/home/runner/work/airmedy/airmedy/frontend/src/stores/device.ts:32:7)
    at /home/runner/work/airmedy/airmedy/frontend/src/stores/device.test.ts:62:5
    at runTest (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/@vitest+runner@1.6.1/node_modules/@vitest/runner/dist/index.js:781:11)
    at runSuite (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/@vitest+runner@1.6.1/node_modules/@vitest/runner/dist/index.js:909:15)
    at runSuite (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/@vitest+runner@1.6.1/node_modules/@vitest/runner/dist/index.js:909:15)
    at runFiles (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/@vitest+runner@1.6.1/node_modules/@vitest/runner/dist/index.js:958:5)
    at startTests (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/@vitest+runner@1.6.1/node_modules/@vitest/runner/dist/index.js:967:3)
    at file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/vitest@1.6.1_@types+node@25.6.0_jsdom@24.1.3_lightningcss@1.32.0/node_modules/vitest/dist/chunks/runtime-runBaseTests.oAvMKtQC.js:116:7
    at withEnv (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/vitest@1.6.1_@types+node@25.6.0_jsdom@24.1.3_lightningcss@1.32.0/node_modules/vitest/dist/chunks/runtime-runBaseTests.oAvMKtQC.js:83:5)
stderr | src/stores/device.test.ts > useDeviceStore > init identifies linux platform
Failed to check window state TypeError: __vite_ssr_import_2__.Window.IsMaximised is not a function
    at checkFullscreen (/home/runner/work/airmedy/airmedy/frontend/src/stores/device.ts:18:34)
    at Proxy.init (/home/runner/work/airmedy/airmedy/frontend/src/stores/device.ts:32:7)
    at /home/runner/work/airmedy/airmedy/frontend/src/stores/device.test.ts:71:5
    at runTest (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/@vitest+runner@1.6.1/node_modules/@vitest/runner/dist/index.js:781:11)
    at runSuite (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/@vitest+runner@1.6.1/node_modules/@vitest/runner/dist/index.js:909:15)
    at runSuite (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/@vitest+runner@1.6.1/node_modules/@vitest/runner/dist/index.js:909:15)
    at runFiles (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/@vitest+runner@1.6.1/node_modules/@vitest/runner/dist/index.js:958:5)
    at startTests (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/@vitest+runner@1.6.1/node_modules/@vitest/runner/dist/index.js:967:3)
    at file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/vitest@1.6.1_@types+node@25.6.0_jsdom@24.1.3_lightningcss@1.32.0/node_modules/vitest/dist/chunks/runtime-runBaseTests.oAvMKtQC.js:116:7
    at withEnv (file:///home/runner/work/airmedy/airmedy/frontend/node_modules/.pnpm/vitest@1.6.1_@types+node@25.6.0_jsdom@24.1.3_lightningcss@1.32.0/node_modules/vitest/dist/chunks/runtime-runBaseTests.oAvMKtQC.js:83:5)
[DeviceStore] Initializing...
stdout | src/stores/device.test.ts > useDeviceStore > init identifies windows platform
[DeviceStore] Initializing...
stdout | src/stores/device.test.ts > useDeviceStore > init identifies linux platform
[DeviceStore] Initializing...
 ✓ src/components/TrackCard.spec.ts  (2 tests) 36ms
⎯⎯⎯⎯⎯⎯ Failed Suites 2 ⎯⎯⎯⎯⎯⎯⎯
 FAIL  src/components/PlayerFooter.spec.ts [ src/components/PlayerFooter.spec.ts ]
 FAIL  src/components/QueueDrawer.spec.ts [ src/components/QueueDrawer.spec.ts ]
TypeError: Create.Map is not a function
 ❯ bindings/airmedy/internal/infra/wails/models.ts:184:32
    182| const $$createType10 = $Create.Nullable($$createType9);
    183| const $$createType11 = $Create.Array($$createType10);
    184| const $$createType12 = $Create.Map($Create.Any, $$createType2);
       |                                ^
    185| const $$createType13 = domain$0.Composer.createFrom;
    186| const $$createType14 = $Create.Nullable($$createType13);
 ❯ bindings/airmedy/internal/infra/wails/playlistservice.ts:3:31
⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[1/9]⎯
Error: AssertionError: expected 'library.edit_metadatacommon.changelib…' to contain 'metadata.edit_title'

- Expected
+ Received

- metadata.edit_title
+ library.edit_metadatacommon.changelibrary.titlelibrary.artistlibrary.albumlibrary.genrelibrary.composertrack_info.labeltrack_info.isrclibrary.yearlibrary.tracktrack_info.bpmlibrary.totallibrary.disclibrary.total_discscommon.cancelcommon.save

 ❯ src/components/MetadataEditDialog.spec.ts:58:22


Error: AssertionError: expected '' to be 'My Song' // Object.is equality

- Expected
+ Received

- My Song

 ❯ src/components/MetadataEditDialog.spec.ts:65:60


⎯⎯⎯⎯⎯⎯⎯ Failed Tests 7 ⎯⎯⎯⎯⎯⎯⎯
 FAIL  src/components/MetadataEditDialog.spec.ts > MetadataEditDialog > renders form when open
AssertionError: expected 'library.edit_metadatacommon.changelib…' to contain 'metadata.edit_title'
- Expected
+ Received
- metadata.edit_title
+ library.edit_metadatacommon.changelibrary.titlelibrary.artistlibrary.albumlibrary.genrelibrary.composertrack_info.labeltrack_info.isrclibrary.yearlibrary.tracktrack_info.bpmlibrary.totallibrary.disclibrary.total_discscommon.cancelcommon.save
 ❯ src/components/MetadataEditDialog.spec.ts:58:22
Error: AssertionError: expected "spy" to be called with arguments: [ 'track-1', Any<MetadataUpdate> ]

Received: 

  1st spy call:

  Array [
    "track-1",
-   Any<MetadataUpdate>,
+   Object {
+     "AlbumTitle": "My Album",
+     "Artist": "Artist One",
+     "ArtworkData": "",
+     "ArtworkMIME": "",
+     "BPM": 0,
+     "Composer": "Composer A",
+     "DiscNumber": 1,
+     "Genre": "Rock",
+     "ISRC": "",
+     "Label": "",
+     "Title": "My Song",
+     "TotalDiscs": 1,
+     "TotalTracks": 10,
+     "TrackNumber": 3,
+     "Year": 2024,
+   },
  ]


Number of calls: 1

 ❯ src/components/MetadataEditDialog.spec.ts:72:22


     56|   it('renders form when open', () => {
     57|     const w = mountDialog()
     58|     expect(w.text()).toContain('metadata.edit_title')
       |                      ^
     59|   })
     60| 
⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[2/9]⎯
 FAIL  src/components/MetadataEditDialog.spec.ts > MetadataEditDialog > initializes title input from track
AssertionError: expected '' to be 'My Song' // Object.is equality
- Expected
+ Received
- My Song
 ❯ src/components/MetadataEditDialog.spec.ts:65:60
Error: TypeError: Create.Map is not a function
 ❯ bindings/airmedy/internal/infra/wails/models.ts:184:32
 ❯ bindings/airmedy/internal/infra/wails/playlistservice.ts:3:31


     63|     const inputs = w.findAll('input')
     64|     const titleInput = inputs[0]
     65|     expect((titleInput.element as HTMLInputElement).value).toBe('My So…
       |                                                            ^
     66|   })
     67| 
Error: TypeError: Create.Map is not a function
 ❯ bindings/airmedy/internal/infra/wails/models.ts:184:32
 ❯ bindings/airmedy/internal/infra/wails/playlistservice.ts:3:31


⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[3/9]⎯
 FAIL  src/components/MetadataEditDialog.spec.ts > MetadataEditDialog > calls UpdateTrackMetadata on save
AssertionError: expected "spy" to be called with arguments: [ 'track-1', Any<MetadataUpdate> ]
Received: 
  1st spy call:
  Array [
    "track-1",
-   Any<MetadataUpdate>,
+   Object {
+     "AlbumTitle": "My Album",
+     "Artist": "Artist One",
+     "ArtworkData": "",
+     "ArtworkMIME": "",
+     "BPM": 0,
+     "Composer": "Composer A",
+     "DiscNumber": 1,
+     "Genre": "Rock",
+     "ISRC": "",
+     "Label": "",
+     "Title": "My Song",
+     "TotalDiscs": 1,
+     "TotalTracks": 10,
+     "TrackNumber": 3,
+     "Year": 2024,
+   },
  ]
Number of calls: 1

 ❯ src/components/MetadataEditDialog.spec.ts:72:22
     70|     const saveBtn = w.findAll('button').find(b => b.text() === 'common…
     71|     await saveBtn!.trigger('click')
     72|     expect(updateFn).toHaveBeenCalledWith('track-1', expect.any(Metada…
       |                      ^
     73|   })
     74| 
⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[4/9]⎯
Error: Error: [vitest] No "GetPlaylistsForTrack" export is defined on the "../../bindings/airmedy/internal/infra/wails/playlistservice" mock. Did you forget to return it from "vi.mock"?
If you need to partially mock a module, you can use "importOriginal" helper inside:

vi.mock("../../bindings/airmedy/internal/infra/wails/playlistservice", async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    // your mocked methods
  }
})

 ❯ buildMenuItems src/composables/useTrackContextMenu.ts:104:23
 ❯ src/composables/useTrackContextMenu.spec.ts:57:19


Error: Error: [vitest] No "GetPlaylistsForTrack" export is defined on the "../../bindings/airmedy/internal/infra/wails/playlistservice" mock. Did you forget to return it from "vi.mock"?
If you need to partially mock a module, you can use "importOriginal" helper inside:

vi.mock("../../bindings/airmedy/internal/infra/wails/playlistservice", async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    // your mocked methods
  }
})

 ❯ buildMenuItems src/composables/useTrackContextMenu.ts:104:23
 ❯ src/composables/useTrackContextMenu.spec.ts:71:19


 FAIL  src/composables/useTrackContextMenu.spec.ts > useTrackContextMenu > excludes "Play Next" if track is currently playing
Error: [vitest] No "GetPlaylistsForTrack" export is defined on the "../../bindings/airmedy/internal/infra/wails/playlistservice" mock. Did you forget to return it from "vi.mock"?
If you need to partially mock a module, you can use "importOriginal" helper inside:

vi.mock("../../bindings/airmedy/internal/infra/wails/playlistservice", async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    // your mocked methods
  }
})
 ❯ buildMenuItems src/composables/useTrackContextMenu.ts:104:23
    102| 
    103|       // Async check for playlists that already contain this track
    104|       PlaylistService.GetPlaylistsForTrack(track.id).then(playlistIds …
       |                       ^
    105|         if (!playlistIds || !playlistIds.length) return
    106| 
 ❯ src/composables/useTrackContextMenu.spec.ts:57:19
Error: Error: [vitest] No "GetPlaylistsForTrack" export is defined on the "../../bindings/airmedy/internal/infra/wails/playlistservice" mock. Did you forget to return it from "vi.mock"?
If you need to partially mock a module, you can use "importOriginal" helper inside:

vi.mock("../../bindings/airmedy/internal/infra/wails/playlistservice", async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    // your mocked methods
  }
})

 ❯ buildMenuItems src/composables/useTrackContextMenu.ts:104:23
 ❯ src/composables/useTrackContextMenu.spec.ts:86:19


⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[5/9]⎯
 FAIL  src/composables/useTrackContextMenu.spec.ts > useTrackContextMenu > includes "Play Next" if track is not currently playing
Error: [vitest] No "GetPlaylistsForTrack" export is defined on the "../../bindings/airmedy/internal/infra/wails/playlistservice" mock. Did you forget to return it from "vi.mock"?
If you need to partially mock a module, you can use "importOriginal" helper inside:

vi.mock("../../bindings/airmedy/internal/infra/wails/playlistservice", async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    // your mocked methods
  }
})
 ❯ buildMenuItems src/composables/useTrackContextMenu.ts:104:23
    102| 
    103|       // Async check for playlists that already contain this track
    104|       PlaylistService.GetPlaylistsForTrack(track.id).then(playlistIds …
       |                       ^
    105|         if (!playlistIds || !playlistIds.length) return
    106| 
 ❯ src/composables/useTrackContextMenu.spec.ts:71:19
⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[6/9]⎯
 FAIL  src/composables/useTrackContextMenu.spec.ts > useTrackContextMenu > excludes "Play Next" if excludePlayNext option is true
Error: [vitest] No "GetPlaylistsForTrack" export is defined on the "../../bindings/airmedy/internal/infra/wails/playlistservice" mock. Did you forget to return it from "vi.mock"?
If you need to partially mock a module, you can use "importOriginal" helper inside:

vi.mock("../../bindings/airmedy/internal/infra/wails/playlistservice", async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    // your mocked methods
  }
})
 ❯ buildMenuItems src/composables/useTrackContextMenu.ts:104:23
    102| 
    103|       // Async check for playlists that already contain this track
    104|       PlaylistService.GetPlaylistsForTrack(track.id).then(playlistIds …
       |                       ^
    105|         if (!playlistIds || !playlistIds.length) return
    106| 
 ❯ src/composables/useTrackContextMenu.spec.ts:86:19
⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[7/9]⎯
 FAIL  src/stores/player.test.ts > usePlayerStore > returns null artworkUrl when no currentTrack
AssertionError: expected undefined to be null
 ❯ src/stores/player.test.ts:99:30
Error: AssertionError: expected undefined to be null
 ❯ src/stores/player.test.ts:99:30


     97|   it('returns null artworkUrl when no currentTrack', () => {
     98|     const store = usePlayerStore()
     99|     expect(store.artworkUrl).toBeNull()
       |                              ^
    100|   })
    101| 
⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯[8/9]⎯
 Test Files  5 failed | 3 passed (8)
      Tests  7 failed | 26 passed (33)
   Start at  10:12:10
   Duration  3.58s (transform 960ms, setup 0ms, collect 1.87s, tests 387ms, environment 3.68s, prepare 805ms)
 ELIFECYCLE  Test failed. See above for more details.
  ERROR   task: Failed to run task "test:ui": exit status 1
Error: Process completed with exit code 1.
