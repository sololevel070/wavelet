# Wavelet: project context for AI coding agents

## What this is
Wavelet is an Android music player (Kotlin + Jetpack Compose), built from scratch as an
original app inspired by the *design* of Soundbound (soundbound.app): a core player plus
pluggable "sources" that can be listed in a repo index. No Soundbound code was used; it is
closed source and was not decompiled.

Owner preference: Spotify-style UI (dark-first, Home / Search / Your Library tabs, artwork-tinted
Now Playing). Keep Wavelet's own name and violet accent (#8B7CFF), not Spotify branding or green.

## Build & environment (Windows dev machine)
- JDK 17: `C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot` (set JAVA_HOME)
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (platform 35, build-tools 35.0.0); `local.properties` points to it
- Gradle 8.11.1 via wrapper; AGP 8.7.3; Kotlin 2.1.0; KSP 2.1.0-1.0.29
- Build: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- Tests: `./gradlew testDebugUnitTest` (JVM unit tests in `app/src/test`)
- compileSdk/targetSdk 35, minSdk 24, package `app.wavelet`, version 0.3.0
- Material3 experimental APIs fail the build unless opted in (`@OptIn(ExperimentalMaterial3Api::class)`)

## Key libraries
Compose BOM 2024.12.01 (Material3, extended icons), Navigation Compose 2.8.5, Media3 1.5.1
(ExoPlayer, HLS, Session), Room 2.6.1 (KSP), OkHttp 4.12, kotlinx-serialization 1.7.3,
Coroutines 1.9 (+guava), Coil 2.7, Palette 1.0.

## Architecture (app/src/main/java/app/wavelet)
- `WaveletApp.kt` – Application + `AppContainer` (manual DI): store, downloads, sources, repos,
  Room database, library repo, matcher, lyrics repo, `appVisible` flag. `updateState{}` saves
  settings and rebuilds sources.
- `data/AppStore.kt` – `AppState` (sources, repos, settings, recentSearches) persisted as
  `state.json`. `SourceConfig(instanceId, engine, name, enabled, options, fromRepo, version)`.
  `Settings` holds theme, colours, ListenBrainz token, history, offline, floating lyrics, resume.
- `data/db/Database.kt` – Room: tracks (cache), playlists, playlist_items (trackKey nullable for
  unmatched imports), likes, history; DAO incl. stats queries.
- `data/LibraryRepository.kt` – playlists, likes, history, month stats, backup/restore models.
- `net/Http.kt` – shared OkHttp client (generic User-Agent), JSON helpers `at/arr/str/long`.
- `source/Source.kt` – `Track` (key = "sourceId:id"), `MusicSource` (search, featured,
  resolveStream, resolveDownload), `LyricsSource`, `LinkSource`, `attempt{}` helper.
- `source/Engines.kt` – registry of built-in engines + option specs + factory.
- `source/SourceRegistry.kt`, `source/RepoManager.kt` (repo index parsing), `source/LyricsRepository.kt`.
- `source/impl/` – Local (MediaStore), Downloads, YouTube Music, Spotify, Jamendo, InternetArchive, RadioBrowser, Audius,
  Subsonic/Navidrome (salted token auth), Jellyfin, Plex, LrcLib, SongLink (iTunes Search +
  search links; Odesli's public API is closed).
- `io/PlaylistFormats.kt` – import Spotify and YouTube links, CSV (Exportify/TuneMyMusic), M3U/M3U8, XSPF, JSPF, Wavelet
  JSON; export JSON/M3U8/CSV/XSPF. Never export URLs carrying tokens (api_key, t=, token=).
- `io/TrackMatcher.kt` – matches imported title/artist to sources (score threshold 0.78).
- `integrations/` – `ListenBrainz.kt` (token validation, scrobbling, playlist import), `LastFm.kt` (mobile session auth, scrobbling, now playing).
- `download/DownloadRepository.kt` – saves to app music dir; index keyed by original track key.
- `player/PlaybackService.kt` – ExoPlayer + MediaSession service; drives widget, floating lyrics, crossfade,
  queue saving and playback resumption.
- `player/AudioFxController.kt` – in-app 5-band Equalizer, presets, Bass Boost, and Virtualizer.
- `player/PlayerConnection.kt` (MediaController wrapper, queue, repeat/shuffle/speed),
  `QueueStore.kt`, `PlayerWidget.kt` (AppWidget + updater), `LyricsOverlay.kt` (View-based overlay).
- `ui/` – `MainViewModel` (single VM for everything), `AppRoot` (NavHost + bottom bar + mini
  player + overlays), screens: Home, Search, Library, Collections (playlist/liked/downloads/
  device/album/artist/history), NowPlaying, Settings, Sources, Stats, Import; `Sheets.kt`
  (track options, add-to-playlist, fix-match, import progress, links); `Components.kt`; `Theme.kt`.

## Conventions
- Persist small state as JSON via `Http.json`; larger/relational data in Room.
- Every network call goes through `Http`; wrap risky calls with `attempt{}` (keeps cancellation).
- User-facing errors via `friendly(e)` and `vm.say(...)` snackbars.
- Add a source: implement the interface in `source/impl/`, register in `Engines.all` and
  `Engines.create`; options listed in `EngineInfo.options` get a settings form automatically.
- Repos only enable and configure built-in engines; the app does not download or run plugin code.
- Keep UI copy plain and short; sentence case.

## Status / not built yet
Not tested on a physical device yet. Not built: live sync with Spotify/YouTube Music/Apple Music
accounts, song recognition, drag-and-drop reordering (up/down buttons used).
Known limits: server passwords stored unencrypted in app-private storage; Internet Archive results
are whole items (plays first audio file).
