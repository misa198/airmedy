# Android Compose UI Catalog

This catalog documents the Android UI owned by `mobile/androidApp`. It does not
describe the desktop Vue UI or future iOS UI.

## App shell and navigation

- `MainActivity` is locked to portrait in the Android manifest. The mobile
  layouts and navigation transitions do not support landscape orientation.

- `App.kt` is the app shell: it owns the theme, shared Haze/list state, header,
  and placement of the navigation chrome. `ui/navigation/AppDestinationContent.kt`
  routes each destination's independent `AppStackPage` stack, and
  `ui/navigation/FloatingNavigationBar.kt` owns its visual and gesture behavior.
  Individual page content lives in `ui/screens/`. The navigation's shared
  selection pill is also an active-foreground mask: a primary-coloured duplicate
  of the icons and labels is clipped to the pill, so only the exact covered
  portions of an icon or label become active while it moves.
- The Android app shell follows unidirectional data flow: `MainViewModel`
  exposes immutable `AppUiState`, `App.kt` emits the sealed `AppIntent` through
  one callback, and the ViewModel reduces navigation/theme inputs. One-time
  host work is emitted as `AppEffect`; `MainActivity` collects it and performs
  Android-only actions such as opening an external URL. Feature state remains
  in Android ViewModels; `sharedLogic` does not contain UI state or effects.
- Popping a parameterized detail page also clears its selected entity ID from
  `MainViewModel`. A later open therefore creates the page from the newly
  supplied ID instead of retaining the page state from the popped instance.
- `MainViewModel` also increments a per-destination/page state generation when
  a page is removed from a stack. `App.kt` keys every hoisted `LazyListState`
  to that generation and explicitly scrolls it to item zero: changing
  bottom-navigation destinations preserves a still-open page's scroll
  position, while reopening a popped page begins at its initial scroll
  position even when Compose restores the list state.
- `ThemeModeStore` is the Android-local settings port consumed by the app
  shell. `ThemePreferences` implements it with DataStore, while tests use an
  in-memory implementation.
- Header title animation keys include both destination and current stack page.
  The shared `StackPageEntry` carries its actual stack index, so all pushes and
  pops—including a third-level Album Details page—use the correct direction.
  Stack changes retain the title slide while disabling size interpolation; the
  header always reserves one action slot, preventing a title-width reflow when
  controls disappear. Switching bottom-navigation destination stacks changes
  the title and content without animation; push/pop inside a destination stack
  animates content and header title in horizontal sync using directional slides,
  opaque page surfaces, and Z-index ordering to prevent ghosting/overlap.
- The Settings root shows one card-contained action list for Appearance, Sync,
  Playback, Integration, and About. Appearance opens `SettingsAppearance`, Sync
  opens `SettingsSync`, Playback opens `SettingsPlayback`, and About opens
  `SettingsAbout` in the Settings stack; Integration remains presentational.
- Playback Settings uses the `subwoofer` Material Symbol and presents a Song
  Transition action with the `masked_transitions` symbol. Song Transition owns
  the Crossfade switch and semantic 1–12 second slider; it defaults off,
  enables at four seconds for new users, and retains the last enabled duration
  while switched off. While enabled, the duration fades in as its own card below
  a reusable `LabeledCard` muted section label; its current value is a small
  top-left label and the 1–12 second bounds sit below the slider at its opposing
  ends. The slider itself has a 10dp inset from the card content edges, keeping
  its thumbs comfortably away from the edge. While enabled it also exposes a persisted Blend artwork and
  background switch, default on. Both pages apply the shared page inset outside their card, so the
  card surface does not extend edge-to-edge.
- About has an informational hero card using the desktop-derived
  `airmedy_about_app_icon` drawable, app name and description, followed by an
  iconless, card-contained action list. Its version is static build metadata;
  GitHub and GPL-3.0 license rows delegate opening their URLs to the Android host.
- Appearance contains vertically arranged sections, each in its own `Card`.
  Its Theme section uses `Selection`, the reusable iOS-style dropdown row, to
  persist the System, Light, or Dark theme choice. An `ActionListDivider` with full-width style separates it from a
  persisted Reduce transparency switch row. It defaults off; when enabled it removes
  every Haze source/effect from the app shell and renders header, navigation,
  mini-player, and glass icon controls with opaque theme surfaces immediately.
- Sync opens `SettingsSync` in the Settings stack. `SyncViewModel` owns pairing
  state from the shared pairing use case: no device shows the empty hero and a
  glass Add-device header action; a pending request shows an approval hero; a
  paired device uses a HeroCard with a Lucide computer icon, bold QR-provided
  desktop name, and a green Online/red Offline MQTT session badge. Offline
  guidance says the desktop is ready to connect and instructs the user to start
  Broadcast on desktop; the screen connects automatically. While this screen is
  visible, and only while a trusted desktop is Offline, Android browses its
  mDNS broadcast; entering the scanner or leaving the screen stops browsing and
  releases the multicast lock. A saved QR route gets one connection attempt at
  app start, without a background retry loop; foreground discovery is the only
  source of reconnect attempts. The MQTT session remains connected after leaving
  the screen, but its transient discovery endpoint is never persisted or reused
  for discovery outside this lifecycle. Its destructive Revoke button sits
  in a bottom slot of the paired-device HeroCard and opens the shared mobile dialog. LAN host
  and port are used only for the temporary pairing transport and are never displayed.
  The action opens `SettingsSyncScanner`, with a centred rounded QR viewfinder,
  descriptive scan guidance, and an image-picker fallback decoded by ML Kit for
  devices whose camera is unavailable.
  Its session identifies itself to desktop as `airmedy-sync-<desktop-id>-<mobile-id>`;
  this is an internal transport detail, never shown in UI. Revoke is deliberately local-only: it clears the mobile binding and permits a
  new desktop scan, while the old desktop remains trusted until revoked there.
- A valid desktop library-sync request starts Android's foreground transfer
  service. Sync Settings renders its preparing/progress/completed/failed state inside a dedicated `Card` featuring a top gap, text-coloured `LinearProgressIndicator` progress bar with no fill/track gap, status text, and percentage indicator; it never exposes an asset count. The foreground-service notification uses an indeterminate bar while connecting and a native 0–100% determinate bar during transfer, with the percentage also shown in its text; it has no cancellation action. Revoke is disabled while a transfer is running (and `SyncViewModel` rejects any concurrent unpair request), then becomes available once sync completes or fails; it stops the transfer before deleting all mirrored library data.
- Library root page displays a plain `ActionList` (`ActionListContainerStyle.Plain`) with actions for Artists,
  Albums, Tracks, Genres, and Composers. Below the action list, it renders a 2-column grid of up to 50 recently added tracks sorted by creation date descending using `DiscCard`. Tapping Artists opens `LibraryArtists`, Albums opens `LibraryAlbums`,
  Tracks opens `LibraryTracks`, Genres opens `LibraryGenres`, and Composers opens `LibraryComposers` on the Library stack; tapping a recent track card starts playback from a queue built in the displayed recently-added order, rather than the separate Tracks page's selected sort order.
- `LibraryArtists` derives its rows from the normalized artist objects in the active sync manifest, grouped by
  desktop artist ID so collaborations retain the desktop delimiter behavior. It renders a virtualized, divided
  list with circular artist artwork, a trailing navigation chevron, and a Name/Date added ASC/DESC header sort. Ordering uses manifest
  `sort_name` (not the display name) for every name comparison and date tie-breaker. Tapping an artist or its chevron pushes
  `ArtistDetails` on the Library stack.
- `ArtistDetails` uses the shared artwork-derived `DetailHero` with 144dp circular artist artwork, the artist name,
  a localized album-and-track count, and Play/Shuffle actions. Its unique albums are derived from canonical
  track-artist IDs and sorted case-insensitively by album `sort_title` and artist `sort_name`. Artist playback uses that album order, then
  each matching artist track's disc number, track number, and manifest order. It renders the existing album-row
  treatment with 1dp themed inset dividers above and below every album row; adjacent album rows share their divider; tapping an album pushes `AlbumDetails`.
- `LibraryTracks` renders a virtualized `LazyColumn` of tracks with sorting controls (Name, Artist, Play count, Date added; ASC/DESC). All title/artist comparisons and title tie-breakers use manifest `sort_title`/`sort_name`, while titles remain display-only. Track titles use semibold typography, matching the mini player; a subtle theme-border divider runs between rows across the artwork and metadata area. Tapping a track delegates its ID to `LibraryTracksViewModel`, which starts playback from a queue built from the visible sorted order; the overflow action remains independent.
- `LibraryAlbums` derives unique, valid albums from the active sync manifest, using album ID/title/artwork and album artists. When the current desktop manifest omits album artists, it falls back to the track artist; only albums with neither display Unknown artist. It renders a virtualized, divided list with Name/Artist/Date added ASC/DESC header sorting using `sort_title`/`sort_name`; display labels are never sort keys. Album rows show a rounded 48dp artwork thumbnail, title, artist, and a trailing navigation chevron that invokes the same album-opening action as the row.
- `AirmedyTextField` is the shared Android capsule input, independent of Material fields. Its neutral inset surface has no border or focus ring, uses a themed cursor and Done IME action, and supports `Small` (40dp), `Medium` (48dp), and `Large` (56dp) sizes. Playlist creation uses `Medium`; callers retain validation and domain behavior.
- `AirmedyBottomSheet` is the reusable non-Material Compose sheet: it provides a glass, Apple-style header with centered title, a default leading glass × dismiss button, optional trailing action, a dark themed scrim, and Back dismissal. `CreatePlaylistBottomSheet` uses it for the × / Create header actions, optional Photo Picker artwork preview, and playlist-name entry. Selected artwork is normalized to private JPEG staging; creation writes ordered UUID `CREATE` then `SET_ARTWORK` mutations and retains the staging file until the desktop snapshot replaces the local projection.
- `TrackContextMenu` is the reusable track overflow `AnchoredPopupMenu`, positioned at the row’s `…` action. Anchored popups retain a 16dp viewport inset and flip above their anchor when they would overflow below. The `…` button and holding the row/card open the same menu for Library Tracks, Album Details, and Library Home’s Recently Added grid. Its host controls action availability through `TrackContextMenuActions` and receives playback, favorite, navigation, and an optional local Haze source. Mobile omits unavailable actions rather than rendering disabled controls: `PlaybackQueueSnapshot` hides both Play next and Add to queue for the current item and hides Add to queue for an item already in the active queue. Album and artist navigation actions are likewise omitted when their respective track data is absent. The anchor is always kept composed while a local detail sheet is open, preventing its lazy-list row from collapsing. `LibraryTracks`, `AlbumDetails`, and the current-track More control in `FullScreenPlayer` integrate it with the Android playback controller, favorite mutation, and artist navigation; the fullscreen host passes its own Haze source so the menu blurs the player artwork rather than the underlying page. AlbumDetails sets `goToAlbum = false` because that navigation would be redundant. `TrackContextBottomSheetRequest` is the common sheet contract for Track Info, Add to Playlist, and collaboration artist selection. The fullscreen host defers every such request to the App shell until fullscreen has completely dismissed, so each sheet survives independently of the player. Track Info and Add to Playlist currently open explicit placeholder sheets and do not mutate data.
  Track-context sheets deliberately use a fresh `Modifier`: an anchor modifier may contain item parent data such as `RowScope.weight`, which must never be passed into the Dialog or it can incorrectly align the sheet at the top of the window.
- `LibraryPlaylists` is opened from the Library root’s Playlists action. It always starts with the localized system Favorites row—even if the current sync manifest omits it—followed by synced playlists. Its virtualized divided rows are 126dp high with 110dp artwork, playlist name, and a chevron that opens `PlaylistDetails`; the glass Add action opens the create-playlist sheet. Create trims a non-empty name, stores an optimistic local playlist plus durable `CREATE` mutation (and optional `SET_ARTWORK`), then opens its details; the local projection remains until an authoritative desktop snapshot contains its ID. Favorites is virtual: its tracks, playback order, and artwork derive from mirrored tracks whose `is_favorite` metadata is true, both in the list and details. Artwork uses a custom playlist cover when synced, otherwise it follows the ordered playlist tracks and renders the first cover for one to three unique covers or a 2×2 mosaic for four. Missing artwork falls back to the Favorites heart or playlist symbol.
- `PlaylistDetails` mirrors `AlbumDetails` with a centered hero and Play/Shuffle/More controls, but uses the standard artwork-based `TrackRow` rather than numbered album rows. It has no artist, year, or copyright; its only metadata line is localized track count plus the total of each mirrored TrackDTO `duration`, formatted like desktop (days/hours, hours/minutes, minutes, or seconds). It preserves synced playlist track order while omitting unavailable local IDs; Favorites is supported even without a manifest record. Hero artwork follows the playlist’s manual-cover/mosaic/fallback rules, and a mosaic derives its backdrop colour from the first selected cover. Each track row supports the shared context menu from overflow and long press; standard playlists append a primary-red `Remove from playlist` action under a full-width divider, which queues a durable `REMOVE_TRACK` mutation and removes the row optimistically while that mutation is pending. Favorites omits this action because its favorite toggle is the removal mechanism for that virtual playlist.
- The Fullscreen Player heart is an optimistic favorite control: it uses the filled glyph when the current mirrored track is favorite, queues the desired state durably when tapped, and updates the virtual Favorites playlist without waiting for desktop Sync. Adding a favorite plays one restrained 120ms scale-up to 114%, then a 180ms settle to normal size; removal does not pulse. Its pressed ripple is intentionally suppressed so the glass control does not flash a dark halo.
- Tapping an album row pushes `AlbumDetails` on the Library stack. It has a centered reusable `DetailHero` (large square album artwork, title, artist, then a separate optional published-year, localized-track-count, and aggregate track-duration metadata line, glass Shuffle/More controls, and theme-inverted Play pill) followed by virtualized numbered album tracks. The aggregate duration sums non-negative mirrored TrackDTO `duration` values and uses the same desktop-style days/hours, hours/minutes, minutes, or seconds format as Playlist Details. Every track row has a 1dp themed divider above and below it; adjacent rows share their border. A non-empty album copyright from the sync manifest appears below the track list at the lower left in small muted text. Play and Shuffle replace the Android queue with the ordered album tracks; overflow controls remain presentational. Track ordering is disc number, track number, then manifest order; missing numbers use the visible fallback index.
- `LibraryGenres` derives unique genres from the active sync manifest. It renders a virtualized, divided list with Name/Date added ASC/DESC header sorting using the manifest `normalization_key` retained as Android `sortName`, never the display name, and `GenreRow` (artwork-free semibold title with trailing navigation chevron).
- `GenreDetails` mirrors `ArtistDetails` without artwork: its `DetailHero` shows the genre name, localized album-and-track count, and Play/Shuffle actions. It matches all supported genre fields from track metadata to the selected normalized genre ID, orders unique albums by canonical album sort title/artist, and orders playback by album, disc number, track number, then manifest order. Its album rows have 1dp themed inset dividers above and below them, shared by adjacent rows, and open `AlbumDetails`.
- `LibraryComposers` derives unique composers from the active sync manifest. It renders a virtualized, divided list with Name/Date added ASC/DESC header sorting using the manifest `normalization_key` retained as Android `sortName`, never the display name, and `ComposerRow` (artwork-free semibold title with trailing navigation chevron).
- `ComposerDetails` mirrors `GenreDetails` without artwork: its `DetailHero` shows the composer name, localized album-and-track count, and Play/Shuffle actions. It matches all supported composer fields from track metadata to the selected normalized composer ID, orders unique albums by canonical album sort title/artist, and orders playback by album, disc number, track number, then manifest order. Its album rows have 1dp themed inset dividers above and below them, shared by adjacent rows, and open `AlbumDetails`.
- Home content is supplied by `HomeDemoContent`. A forward action calls the
  callback provided by the app shell, which pushes `HomeSampleDetail`; Android
  Back pops that destination stack while the floating navigation remains shown.
- Tapping the selected navigation destination restores that destination stack
  to `Root`; on Home it also animates the root `LazyColumn` back to its first item.
  Tapping another destination continues to switch stacks without resetting it.
  On release, a navigation-pill drag chooses the slot containing the pill's centre,
  so a pill that is mostly over the next destination selects that destination.
  A drag that leaves the selected tab but ends with its centre back in that tab is
  not a reselection, so it preserves that stack and scroll position.
- `NavigationChrome` owns the floating navigation plus its optional mini player,
  so later animations can treat them as one persistent navigation unit. When
  playback first creates the mini player, the pill fades in while sliding up
  from behind the navigation over 280ms. The mini player is a 56dp glass pill
  positioned 8dp above the navigation and uses the same max width, border,
  blur, and safe-area placement. It appears for
  Preparing, Playing, and Paused playback, reserving matching page-bottom space;
  it is absent for Idle and Failed states. It presents 48dp square artwork with
  a 10dp radius with an 8dp left inset (or the Lucide music fallback), a semibold marquee title and artist label,
  and larger Previous and Next controls rendered as two softly rounded connected triangles, plus
  Play/Pause, with
  visually overlapping 48dp touch targets. Preparing disables its play/pause control. Its
  Play/Pause uses separate, lightly rounded Play and Pause glyphs: the current glyph shrinks and
  fades while the next glyph grows from roughly half-size at the shared centre over a restrained
  320ms transition.
  During a transient Preparing state while advancing tracks, it retains the last settled glyph so
  the transport icon never flashes Play before playback resumes; it also retains its normal tint
  while disabled for that hand-off.
  Pressing it
  contracts its lightly rounded glyph and shows a close, subtle halo; release expands and fades
  that halo while the glyph returns to its resting size.
  Previous and Next use a contained halo and a directional replacement: pressing smoothly contracts the
  double-triangle glyph; on release the current glyph fades while moving in the transport
  direction, and a replacement enters from the opposite side at half-size and grows into the
  shared centre over a soft 620ms transition with no spring or overshoot. The outgoing
  glyph fades gradually at rest while the incoming glyph is revealed; the incoming glyph then travels from
  farther behind, clipped to the icon bounds so its complete double-triangle shape remains intact. The rear
  pair uses equal triangles with both sharp ends softly rounded. Its halo is larger than the Play/Pause
  halo so the wider double-triangle glyph keeps the same visual breathing room.
  With Repeat off, Previous is disabled at the first active queue item and Next is disabled at the
  last active queue item; any repeat mode keeps both transport controls available at the boundaries.
  Metadata swipe gestures obey those same availability rules and do not dispatch or haptic at a
  disabled boundary. Manual Previous/Next preserves the current Playing or Paused state; only an
  automatic end-of-track transition starts the incoming track.
  title/artist area also accepts horizontal transport
  gestures: a left swipe advances to Next and a right swipe invokes Previous. The metadata
  follows the drag inside a clipped metadata viewport, so it cannot overlap artwork or
  controls; it snaps back after release and emits one confirmation haptic only when the swipe
  reaches the distance or velocity threshold and dispatches a transport command. Horizontal
  gestures are scoped to metadata so they do not conflict with the pill's vertical
  fullscreen/dismiss gestures or its control buttons. The mini player pill and its transport
  controls suppress press indications, so starting or reversing a drag never leaves a dark
  ripple-like surface behind. Tapping its non-control surface opens `FullScreenPlayer`.
  Pulling upward moves the mini player up by its full 56dp height while fading it out.
  The edge-to-edge overlay uses the first 48dp of an upward pull to accelerate
  from below the screen until its edge catches the finger, then follows at the
  same approximate pixel distance. Releasing after an upward drag whose remaining
  travel exceeds touch-slop completes the transition to the fullscreen player;
  a sub-slop remainder after reversing returns the mini player to rest. Once
  released, it uses a slower 760ms slide/fade only to complete the current
  partial transition. Pulling the mini player down by 36dp keeps it sliding
  from its exact release position off the bottom of the screen without rebounding,
  then stops playback and clears the
  persisted queue. Tap and vertical drag are separate consumed gesture paths,
  so a slow downward dismiss or a cancelled drag can never be reinterpreted as
  an open-fullscreen tap. Every pointer-down clears any interrupted fullscreen
  pull state before classifying the new gesture. Once a vertical gesture crosses
  touch-slop, its initial direction is locked: a downward drag can be reversed
  back to the mini player's resting position before release, but it never
  transitions to fullscreen within that same gesture.
  The pill remains visually below navigation while dismissing. Its drag receiver
  stays anchored at the initial mini-player position, so the same downward drag
  continues even after the pill passes behind the navigation.
  The fullscreen player retains that opening animation and can be pulled down by
  96dp or closed with system Back. Reversing an in-progress downward pull immediately reduces its dismissal offset, so it can be returned to the top before release. Artwork scales down to 60% over 500ms while paused and returns to full size on playback.
  While it is visible, the activity forces light status-bar content so it remains
  legible above the fullscreen artwork; opening and closing apply that appearance
  directly through the activity's window controller, without waiting for a
  playback-state or Compose update. Closing it restores the theme's status-bar
  appearance.
  Swiping left/right anywhere in the artwork/metadata block moves only the
  title/artist cluster with the finger while the artwork and actions remain still;
  it springs back after release and dispatches Next/Previous respectively after
  the same distance/velocity threshold and confirmation haptic used by mini-player
  metadata swipes.
  A 36dp-wide, semi-opaque white drag handle is
  centered with an 8dp gap below the status-bar safe area and a 20dp gap before the near-full-width rounded artwork,
  visually indicating the downward-dismiss gesture. Artwork, the title/artist metadata, and its
  Heart/More actions form one top block, with a 24dp artwork-to-metadata gap and a 12dp
  metadata-to-seek gap; the remaining screen height is a separate `SpaceEvenly`
  column of three groups: seek, primary transport, and volume/secondary actions. The primary transport controls are centered with a
  compact fixed gap with 34dp previous/next icons and a 40dp play/pause icon; its play/pause
  glyph uses the same restrained 320ms continuous Play/Pause morph as the mini player; bottom secondary-action icons render at 24dp in `foregroundSubtle` inside their standard touch targets.
  It renders near-full-width rounded artwork, with marquee white title text and
  marquee `foregroundSubtle` artist/duration text. Overlong marquee text travels
  to its end and reverses back rather than wrapping continuously. The metadata column keeps a
  12dp gap before the blurred glass Heart/More button pair (using a fullscreen-local Haze backdrop source and a subtle 6% glass tint),
  a dominant-colour gradient extracted from the artwork, animated over 280ms when artwork changes; during an automatic crossfade with Blend artwork and background enabled, outgoing and incoming covers plus their gradients use the audio-matched equal-power `cos/sin` curve for the actual fade duration. The visual is fullscreen-only; mini-player artwork switches immediately. The prior artwork remains visible while the replacement decodes to prevent a fallback-colour flash, with a
  restrained dark `playerBackdrop` overlay and fallback in every app theme, seek/duration, transport controls, Android music-stream volume (the system settings provider is observed recursively so hardware keys and route-specific system-volume events keep it current; hardware-key changes are also predicted immediately before that asynchronous observer confirms the route's actual volume).
  Fullscreen title/artist begin changing as the incoming source starts: incoming metadata fades and slides horizontally in from the right while the outgoing label exits left; this applies to both expanded and compact player layouts.
  Lyrics/Queue affordances follow. Selecting Lyrics or Queue compresses the
  artwork into a 96dp square, animating its anchor from centre to top-left when
  leaving the paused presentation. The expanded
  title/artist cluster slides slightly upward while fading out; a separate
  compact title/artist/More cluster then slides upward into the adjacent 96dp
  row while fading in 120ms after the artwork begins, with a 16dp
  gap and no right inset (Favorite is hidden in this compact state); the top block retains its
  expanded height, and its freed upper space displays a transparent placeholder
  that slides upward and fades in with the compact cluster after the same 120ms delay. The selected pane
  remains open across track changes. Tapping the compact artwork or selecting the same action closes it,
  while switching to the other action fades its panel in while sliding it upward. The active
  Lyrics or Queue action fills with `foregroundSubtle` over 220ms; its
  semi-transparent player-backdrop icon changes over the same duration so the
  fill remains visible through it without flashing. Fullscreen transport actions
  suppress ripple indications. The centred secondary action is a Lucide
  Cast button; on Android 14 (API 34) and later it delegates opening Android's
  system Media Output Switcher for the active Airmedy media session to the
  activity. Heart and More remain visual actions only. Lyrics and Queue open
  their respective fullscreen panes.
  The Queue pane is the exception: it renders the active playback order in a
  virtualized `LazyColumn`. Its header keeps Queue at the left and themed
  Shuffle/Repeat controls at the right. When the Queue panel is closed, its
  bottom control shows a small secondary-glass status badge: Shuffle takes
  precedence, otherwise Repeat/Repeat One is shown, and it is absent when both
  modes are off. Their inactive background/icon match the
  fullscreen Favorite action; their active background matches the fullscreen
  slider's resting fill colour and their icon matches active Lyrics.
  Shuffle toggles directly; Repeat cycles
  Off, All, One, then Off. Rows are 56dp with artwork, title/artist metadata,
  and a trailing Material Symbols Reorder icon aligned flush to the row's end, which
  uses the same muted token as the volume icons. The Queue panel itself reaches
  the fullscreen edges; its header and each queue row supply their own 20dp
  horizontal inset so their contents remain aligned with the player shell.
  Hovering a row reveals a faint full-width background using the same secondary
  glass tint as the fullscreen Favorite control, while its content remains
  inset. Touching the Reorder target does not show this preliminary state; the
  stronger full-width glass surface appears only after a drag has started. The
  current item's artwork has a `playerBackdrop` overlay
  with a centred three-bar primary `AirmedyPlayingIndicator`; the bars animate
  while playback is active and rest at a short height when paused. Tapping the
  row selects and starts that item. Holding a resolved queue row opens the shared
  track context menu anchored to that row. Remove from queue is its first action;
  Add to queue is omitted, and the current item also omits Play next. The trailing
  Reorder handle remains dedicated to long-press dragging. Opening Queue, including switching from
  Lyrics, immediately positions the current item in view without an animation.
  It then animates to a subsequent current-track change only while the prior
  current item remains visible; a user who has browsed elsewhere keeps their
  chosen viewport. Reordering never triggers current-track auto-follow, so it
  also preserves the chosen viewport. Long-press dragging the dedicated trailing Reorder handle
  reorders the active queue and preserves the current track, shuffle state,
  and repeat mode. Its 72dp-wide touch target keeps the glyph flush to the
  trailing edge while making long-press drag forgiving. Queue reorder uses
  `sh.calvin.reorderable:reorderable`, whose `ReorderableItem` owns the
  drag overlay, first-visible-item animation, and edge auto-scroll. The held
  row uses a square-cornered, strengthened variant of
  the Favorite control's translucent secondary glass fill and its themed border
  across the Queue panel's full width (not merely its inset content), and
  follows the finger directly. It updates the local order as items cross and
  dispatches the complete order only when the drag stops. While a reorder drag
  is active, the compact artwork/metadata and Queue header remain visible, but
  the seek, transport, volume, and Lyrics/Cast/Queue control cluster slides
  below the viewport. The Queue `LazyColumn` expands into that released safe
  area; ending or cancelling the drag, closing Queue, or dismissing fullscreen
  restores the normal player layout.
  `FullScreenPlayer.kt` remains the screen coordinator; the queue-specific UI,
  local drag state, and reorder dispatch live in `FullScreenPlayerQueuePanel.kt`.
  Keep this boundary when evolving either feature so queue interaction does not
  add more state to the fullscreen shell.
  The Lyrics pane is backed by the active sync plan's `sync_documents` lyric
  entry for the playing track; its raw synced `Lyric` JSON is decoded by
  `AndroidLibrarySyncStore` without adding a Room schema column. Timestamped
  LRC lines render in `FullScreenPlayerLyricsPanel.kt`, with the active line
  sharp and the surrounding lines progressively faded and blurred. Synced
  lyrics auto-scroll to the active line in focus mode. A manual drag enters
  browse mode: it pauses auto-follow and removes fade/blur so all lyric lines
  remain readable. Tapping a timestamped line smoothly moves that selected row
  into the active slot before seeking and restoring focus mode. If tightly timed
  lines cause playback to advance past the tapped line, the pane resumes
  auto-follow once it reaches that line or any later line. The initial
  selection animation suppresses browse-mode detection, so a tap (or its
  programmatic repositioning) cannot be misclassified as a manual lyric drag.
  Lyric rows use a 20dp tap-drift allowance, so a tap with minor finger motion
  still seeks; only a larger drag remains manual browsing.
  focus position is applied without animation; later tracked
  changes animate using the measured target offset so wrapped lyrics do not
  need a visible second correction. Blur effects are suspended while this
  programmatic scroll is in progress to keep it smooth, then restored at rest.
  Releasing the fullscreen progress slider also supplies its pending target
  position and a distinct seek request ID to the pane, which exits browse mode
  and smoothly follows the target line. For a distant target it jumps near the
  target, measures any wrapped preceding line, then runs one final focus-slot
  animation. Forward seeks retain three approaching rows and animate lyrics up;
  backward seeks start 72dp above the focus slot and animate lyrics down. This
  avoids both sluggish long-list animation and a visible second alignment
  correction. The request animation remains active
  after asynchronous playback-state confirmation clears its pending position,
  so every rapid seek—including repeated seeks—brings a distant target into view.
  Returning to the foreground clears browse mode and immediately repositions
  the active lyric line, including when playback advanced beyond the previously
  visible rows while the app was locked or backgrounded.
  Changing track always resets the lyrics viewport to its first row. The same
  reset occurs when repeat/replay restarts the active track near zero, even
  though its track ID and lyric content are unchanged.
  The pane has an 8dp top inset. Rows have a modest 20dp vertical gap and a right inset so
  the active line's subtle scale transform does not clip or change wrapping.
  The pane has no lyrics header or synced/plain toggle: valid timestamped LRC
  always renders in the synced view, while tracks without timestamps use the
  full plain-text list. Both modes split `^` or `/` bilingual text into primary
  and muted secondary lines. Tapping a timestamped synced line seeks Android
  playback to that line's timestamp; plain lines are read-only.
  Every new open clears any residual downward-dismiss offset before expanding,
  so consecutive opens always finish flush with the top of the screen.
- While the mini player is visible, the app shell observes user-driven nested
  scrolling from any page content. A content-upward delta switches the chrome
  to its 56dp compact row: a left-side circular button for the active destination
  and the mini player to its right. The compact player keeps artwork, title/artist,
  and Play/Pause plus Next; Previous fades out, then its vacated slot is reclaimed
  by the title/artist while Play/Pause and Next remain trailing controls. A downward delta, tapping
  the active-destination button, or dismissing the mini player restores the standard
  72dp four-tab navigation with the full mini player above it. Switching destination
  stacks or pages preserves the current compact/full chrome geometry. This is one
  shared chrome layout: navigation width/height, mini-player
  width, and its X/Y position animate together over 280ms, so the player shrinks
  and slides into the compact row rather than a replacement bar appearing. Its
  16dp horizontal content inset and 38dp artwork remain fixed while moving. Page
  bottom padding animates with these chrome layouts. During expansion, the compact
  active icon remains left-aligned until the navigation surface reaches 85% of its
  full width; its opacity cross-fades with the four icon/label targets over 160ms,
  while the full targets rise 8dp and scale from 96% to 100%. All chrome geometry
  uses one shared 420ms eased transition, preventing labels and icons from being
  compressed or appearing abruptly on slower GPUs. The compact target fills the
  navigation's padded content bounds rather than requesting its own fixed width,
  preventing end-of-transition clipping as the 56dp capsule settles. Its Haze background keeps a
  stable full-width render surface while the visible capsule is clipped horizontally,
  avoiding blur re-render work on every compact-transition frame; the mini player
  uses the same stable-surface approach while it narrows.
  Direction changes have a 24dp hysteresis threshold: a short reversal or a few
  pixels of incidental scroll never toggles the compact chrome. Once the chrome
  already matches the scroll direction, the reducer preserves its state instance
  so ordinary list scrolling does not allocate and discard an equivalent state
  object per pointer delta.
- Shared page chrome is in `ui/components/StackPageLayout.kt`; it owns safe-area
  content padding and the optional glass Back button.

## Shared components

- Settings > Playback links to a dedicated Volume Normalization page. Its switch, Track/Album mode and clipping control are Android-local preferences; they are disabled when the active synced library has no desktop analysis data, which also clears the saved master toggle.

| Component | Contract |
| --- | --- |
| `Card` | Standard 28dp, borderless, opaque themed card surface. It accepts slot content and optional padding; its title/description overload remains a tappable primary-action card. |
| `LabeledCard` | Reusable small, semibold muted section label (with a 4dp leading inset) above a standard `Card`. |
| `DiscCard` | Displays a vertical card featuring a 1:1 square rounded artwork thumbnail (or glass symbol fallback), semibold single-line title, and muted single-line subtitle. Usable for album or track cards. Clickable cards retain button semantics but suppress the press ripple. |
| `HeroCard` | A non-interactive informational card with a 40dp decorative icon, bold `titleLarge` title, optional content directly below its title, and muted description. Its optional bottom slot stays inside the card but outside the standard 24dp content padding. Sync uses these slots for its MQTT Online/Offline badge and in-card Revoke action. |
| `DetailHero` | Centered detail-page identity header with configurable square/circular artwork, title/subtitle, and Shuffle/Play/More callbacks. |
| `AlbumTrackRow` | Detail-page track row with ordinal, two-line title/artist metadata, and an independent trailing overflow action. |
| `TrackRow` | Displays a track row with 48dp rounded artwork (or fallback glass icon), semibold 2-line title/artist text display, and a trailing `...` overflow button. |
| `AlbumRow` | Displays a 48dp rounded album-artwork thumbnail (or themed album fallback), semibold title/album-artist text, and a trailing chevron that invokes the row action. |
| `ArtistRow` | Displays a 48dp circular artist artwork (or themed person fallback), semibold one-line artist name, and a trailing chevron that invokes the row action. |
| `GenreRow` | Displays a 48dp circular glass icon box with label glyph, semibold one-line genre name, and a trailing chevron that invokes the row action. |
| `ComposerRow` | Displays an artwork-free semibold one-line composer name and a trailing chevron that invokes the row action. |
| `LibraryVirtualList` | Shared `LazyColumn` treatment for Library entity pages: caller-provided stable keys and rows, themed inter-row dividers, content padding, and empty-state slot. |
| `AnchoredPopupMenu` | Shared app-level overlay primitive for a caller-provided anchor and arbitrary menu content, top-end aligned with a configurable offset and explicit width. `AnchoredPopupMenuHost` belongs at the app root: unlike Android `Popup`, it renders menus in the same Compose tree as the Haze source, so its clipped surface receives the same liquid-glass blur and themed border as navigation throughout its fade/scale animation. Tapping outside dismisses the active menu; changing its `dismissKey` closes any active menu, which the app wires to the visible stack page. |
| `LibrarySortHeaderButton` | Header sort control built on `AnchoredPopupMenu`. The caller supplies typed, resource-backed sort options plus selected option/order callbacks. |
| `ActionList` | Displays 56dp `ActionListItem` rows with optional leading Material Symbol, resource-backed label, optional trailing composable slot, and a chevron only for clickable rows without a supplied trailing slot. `FullWidth` and `InsetForLeadingIcon` divider styles are available. `Card` uses the shared `Card` surface; `Plain` has no enclosing surface. A row is clickable only when its item has `onClick`. |
| `Selection` | Renders an iOS-style dropdown row and custom elevated menu with a 28dp radius for mutually exclusive `SelectionOption` values, built on `AnchoredPopupMenu`. Its selected value uses the standard row typography with the muted action colour; each menu option is at least 44dp tall. Only the right-side action slot opens and anchors the right-aligned menu; the opaque, rounded menu expands and collapses vertically while fading and scaling over 220ms. The caller owns selected state and receives the selected value through `onValueSelected`. |
| `StackPageLayout` | Places content below the status/header region and above the persistent navigation; screens must use its supplied padding. Its header participates in pointer hit-testing across its full overlay, so scrolling content cannot receive taps through its transparent region. Root page headers use bold `headlineLarge` typography; stack pages with a Back button use normal-sized, bold `bodyLarge` title text. |
| `AirmedyGlassIconButton` | A 48dp circular blurred glass icon button with border and button semantics. Back and header actions use this shared primitive. |
| `AirmedyIconButton` | A 48dp icon action with `Ghost` and `Glass` variants. Glass uses the liquid-glass surface and border; both variants support optional `tint`, `glassColor`, `circleSize`, and `iconSize` overrides and provide an accessible label. |
| `AirmedyMarqueeText` | A single-line text treatment for constrained playback metadata. It start-aligns unbounded text, clips overflow, and uses pingpong keyframe animation with pauses at start/end matching desktop MarqueeText behavior. |
| `AirmedyTrackSlider` | Shared custom-drawn slider for fullscreen-player seek and Android music-stream volume. It preserves a 48dp touch target while rendering a stable `sliderInactive` translucent track. It intentionally does not apply Haze inside its thin clipped track: sampling a blurred artwork backdrop at 6dp high creates visible speckling in the unfilled segment on some Android GPUs. The filled portion rests at a midpoint between `foregroundSubtle` and `onPrimary`, then transitions to `onPrimary` during press/drag. Fullscreen time labels and the low/high-volume icons use this same shared fill tint in their matching slider state. It has no thumb or Material Slider terminal indicator. The full track expands from its centre over 220ms (3% horizontally, 10% vertically, and 3dp thicker). Its time labels sit 2dp closer to the track at rest; `onInteractionChange` offsets them 4dp down and outward while moving volume icons 4dp outward, without reflowing surrounding content. Touching it alone never changes the value; a horizontal drag is required. A local preview owns the displayed value throughout a drag so asynchronous playback or system-volume updates cannot override it; each drag snapshots that preview/current value at press time and applies only horizontal distance. The fill follows drag input without interpolation, then settles over 140ms to the rounded discrete system-volume step after release rather than snapping. Slider range semantics and drag seeking remain available to accessibility services. Its long-lived pointer handler dispatches through the newest callbacks after a crossfade changes track duration. The fullscreen seek control keeps its selected preview (and elapsed-time label) until the playback service publishes a position within 250ms of the requested target. The fullscreen volume row places low/high-volume icons at either end. |
| `AirmedyPlayingIndicator` | A decorative three-bar, white (`onPrimary`) playback indicator. It animates bar height while `isPlaying` and presents short resting bars otherwise; play/pause changes tween smoothly between these states. The fullscreen queue overlays it at the centre of the current track's artwork. |
| `AirmedyPillButton` | A borderless 52dp minimum-height capsule action. `Primary` and `Destructive` use the primary background with the explicit white `onPrimary` token in both light and dark themes; `Secondary` uses the stronger `buttonSecondary` theme surface with normal foreground text. When disabled, only its themed background fades while label colour stays unchanged. Its label supplies button semantics. |
| `AirmedyDialog` | A 36dp-radius, two-action mobile dialog. Its text content has 20dp horizontal inset; the button area has a thinner 16dp horizontal/bottom inset. It supports `Horizontal` and `Vertical` action layouts; the left/top action always dismisses with `Secondary`. |

## UI rules

- Use `AirmedyTheme` and `LocalAirmedyColors`; feature composables do not add raw colours. Reusable subtle foreground details use the low-opacity white `foregroundSubtle` token (46% opacity).
- Icons are Material Symbols Rounded font glyphs (`MaterialSymbol` composable loading `material_symbols_rounded.ttf`), paired with resource-backed strings. Decorative icons have no content description; actionable rows expose their label through Compose semantics.
- Keep interactive targets at least 48dp. Rows and cards delegate click behavior through callbacks rather than storing navigation state.
- Card surfaces are borderless unless a feature explicitly requires a border.

## Testing

- Android UI changes require Compose instrumentation coverage in
  `androidApp/src/androidTest` plus `:androidApp:assembleDebug`.
- Test component interaction and screen-level navigation separately. `ActionListTest`
  covers optional callbacks and iconless trailing slots; `AppNavigationTest` covers
  Home-to-detail and Settings-to-About navigation.
- `MiniPlayerTest` covers playback visibility, transport controls, metadata left/right swipe
  transport dispatch, and the upward fullscreen-opening gesture in both full and compact
  navigation-chrome layouts.
  `AppNavigationTest` covers fullscreen player content, tap-open, downward-dismiss,
  and system-Back behavior.
