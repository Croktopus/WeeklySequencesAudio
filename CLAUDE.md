# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

```bash
./gradlew assembleDebug
```

Debug APK output: `app/build/outputs/apk/debug/`

Target SDK 36, min SDK 24, Java 11 compatibility. No API keys or authentication required — all endpoints are public LessWrong APIs.

## Architecture

Android app (`com.chris.wsa`) for playing audio versions of LessWrong Sequences Reading Group posts. Uses Jetpack Compose, Media3/ExoPlayer, and Navigation Compose. No DI framework — manual constructor injection throughout.

### Audio Resolution Pipeline

The core data flow is a chain of suspend functions:

1. **LSRGFinder** — scrapes the LSRG group HTML page to find the latest event URL
2. **WeeklyPostParser** — queries LessWrong GraphQL API to extract post links from an event
3. **AudioResolver** — resolves each post URL to an MP3, trying LessWrong podcast (Buzzsprout) first, then TYPE III Audio TTS as fallback
4. **PlaylistBuilder** — orchestrates AudioResolver across multiple posts sequentially

### Playback Layer

- `PlaybackService` (MediaSessionService) — foreground service owning ExoPlayer, MediaSession, and notifications. Saves playback position every 5 seconds and on pause/seek/disconnect.
- `PlaylistManager` — holds playlist state as `StateFlow<List<PlaylistItem>>` and current index as `StateFlow<Int>`.
- `PlaybackPositionManager` — SharedPreferences-based position persistence keyed by track URL.

### UI Layer

Four screens via NavHost in `AppNavigation`:
- `main_menu` — saved playlists list + Quick Add button
- `create_playlist` — URL input, event parsing, playlist building
- `playlist_detail/{playlistId}` — track list with play/resume controls
- `player` — full playback controls, speed selector, prev/next cards

`PlayerViewModel` polls player state every 100ms. `MainViewModel` handles playlist CRUD and Quick Add async flow.

### State Management

All reactive state uses Kotlin `StateFlow` collected via `collectAsState()` in Compose. Playlists and positions persist to SharedPreferences via Gson serialization (`PlaylistStorage`).

### Networking

Retrofit + OkHttp. Three API interfaces in `ApiService`. `ApiClient` provides singleton Retrofit instances. All traffic is HTTPS (cleartext disabled via network security config).

---

## File Index

Source root: `app/src/main/java/com/chris/wsa/`

### MainActivity.kt

`MainActivity : ComponentActivity` — Entry point. Binds to `PlaybackService`, handles share intents (`ACTION_SEND`) and view intents (`ACTION_VIEW` for lesswrong.com URLs), requests notification permission on API 33+. Sets Compose content with `AppNavigation`.

- `onCreate()` — starts/binds PlaybackService, extracts intent URLs, sets content
- `onDestroy()` — unbinds service

### API Layer (`api/`)

#### ApiModels.kt

Data classes for API responses:

**LessWrong GraphQL:** `LwGraphQLResponse` → `LwData` → `LwPost` → `LwPostResult(_id, title, slug, user, podcastEpisode, forceAllowType3Audio)`, `LwUser(displayName)`, `PodcastEpisode(externalEpisodeId)`

**TYPE III Audio:** `Type3Response(id, status, title, author, duration, mp3Url, sourceUrl)`

**Event parsing:** `EventResponse` → `EventData` → `EventPost` → `EventPostResult(title, postedAt, user, contents)`, `EventUser(displayName)`, `EventContents(html)`

#### ApiService.kt

Three Retrofit interfaces:

- `LessWrongApi` — `suspend queryPost(@Body): Response<LwGraphQLResponse>` — POST /graphql
- `Type3Api` — `suspend findNarration(@Query url, @Query request_source="embed"): Response<Type3Response>` — GET /narration/find
- `EventApi` — `suspend queryPost(@Body): Response<EventResponse>` — POST /graphql

#### ApiClient.kt

`object ApiClient` — Singleton Retrofit instances and API implementations.

- `okHttpClient` — 15s connect, 30s read, 15s write timeouts
- `lwRetrofit` — base URL `https://www.lesswrong.com/`
- `type3Retrofit` — base URL `https://api.type3.audio/`
- `lwApi: LessWrongApi`, `type3Api: Type3Api`, `eventApi: EventApi`

### Audio Resolution Layer (`audio/`)

#### LwParser.kt

`object LwParser`

- `extractPostId(url: String): String?` — extracts 17-char post ID from `/posts/{id}` or `/s/{seriesId}/p/{id}` patterns

#### AudioResolver.kt

`class AudioResolver(lwApi, type3Api)`

- `suspend getAudioUrl(postUrl): AudioResult` — tries LW podcast first, TYPE III fallback
- `sealed class AudioResult` — `Success(mp3Url, title, author, source)` | `Error(message)`

#### LSRGFinder.kt

`class LSRGFinder(client: OkHttpClient)`

- `suspend findLatestEvent(): String?` — scrapes `https://www.lesswrong.com/groups/LK6GNnKp8PDCkqcxx`, returns first `/events/{id}/{slug}` URL found

#### WeeklyPostParser.kt

`class WeeklyPostParser(api: EventApi)`

- `suspend extractPostLinks(eventUrl): ParsedEvent?` — queries event HTML via GraphQL, extracts post links
- `data class ParsedEvent(title, shortTitle, author, postLinks, postedAt)`
- `private generateShortTitle(title, author): String` — e.g. "LSRG66 1/21 • Author"

#### PlaylistBuilder.kt

`class PlaylistBuilder(resolver: AudioResolver)`

- `suspend buildFromPostUrls(postUrls): BuildResult` — resolves posts sequentially
- `data class BuildResult(items: List<PlaylistItem>, failedUrls: List<String>)`

### Data Layer (`data/`)

#### PlaylistData.kt

- `data class PlaylistItem(url, title, author, mp3Url, source)` — single audio track
- `data class SavedPlaylist(id, name, items, createdAt, eventUrl?, postedAt?)` — persisted playlist

#### PlaylistStorage.kt

`class PlaylistStorage(context: Context)` — Gson-backed SharedPreferences store (prefs name: `"playlists"`, key: `"all_playlists"`).

- `getAllPlaylists(): List<SavedPlaylist>` — sorted by createdAt descending
- `savePlaylist(name, items, eventUrl?, postedAt?): SavedPlaylist` — generates UUID id
- `deletePlaylist(id)`, `getPlaylist(id): SavedPlaylist?`, `clearAll()`

#### PlaybackPositionManager.kt

`class PlaybackPositionManager(context: Context)` — SharedPreferences store (prefs name: `"playback_positions"`), keyed by track MP3 URL.

- `savePosition(trackUrl, positionMs)`, `getPosition(trackUrl): Long`, `clearPosition(trackUrl)`, `clearAll()`

### Playback Layer (`playback/`)

#### PlaylistManager.kt

`class PlaylistManager` — Playlist state holder.

- `playlist: StateFlow<List<PlaylistItem>>` — current playlist
- `currentIndex: StateFlow<Int>` — current track index
- `addItem(item)`, `removeItem(index)`, `moveItem(from, to)`, `getCurrentItem(): PlaylistItem?`
- `next()`, `previous()`, `selectItem(index)`, `clear()`

#### PlaybackService.kt

`class PlaybackService : MediaSessionService` — Foreground service owning ExoPlayer and MediaSession.

- `playlistManager: PlaylistManager` — public
- `POSITION_SAVE_INTERVAL_MS = 5000L` — companion constant
- `getPlayer(): ExoPlayer`, `getPositionManager(): PlaybackPositionManager`
- `loadAndPlayTrack(item, resumePosition=true)` — loads MP3, resumes saved position
- `startForegroundNotification()` — starts foreground with notification
- `onStartCommand()` — handles PLAY, PAUSE, STOP, PREVIOUS, NEXT, REWIND, FORWARD actions
- Inner class `PlaybackBinder : Binder` — `getService(): PlaybackService`
- Listens for `ACTION_AUDIO_BECOMING_NOISY` and `ACTION_ACL_DISCONNECTED` to auto-pause
- Auto-advances to next track on completion; clears saved position when track finishes

#### NotificationHelper.kt

`class NotificationHelper(service: PlaybackService)`

- `NOTIFICATION_ID = 1`, `CHANNEL_ID = "playback_channel"`
- `createNotificationChannel()` — creates channel for API 26+
- `createNotification(player, playlistManager): Notification` — 5 actions: Previous, Rewind 15s, Play/Pause, Forward 15s, Next (compact view: Play/Pause only)
- `startForegroundNotification(player, playlistManager)`, `updateNotification(player, playlistManager)`

### ViewModels (`viewmodel/`)

#### PlayerViewModel.kt

`class PlayerViewModel : ViewModel`

- `data class PlayerUiState(isPlaying, currentPosition, duration, isBuffering, playbackSpeed)`
- `playerState: StateFlow<PlayerUiState>` — polled every 100ms
- `attachPlayer(player, service)` — starts polling loop
- `playPause()`, `seekTo(position)`, `setSpeed(speed)`, `playTrack(item, resumePosition=true)`
- `previous()`, `next()`, `selectAndPlay(index)`, `startPlaylist(items, startIndex=0)`

#### MainViewModel.kt

`class MainViewModel(application) : AndroidViewModel`

- `sealed class QuickAddState` — `Idle` | `Loading(status)` | `Success(playlistId)` | `Error(message)`
- `playlists: StateFlow<List<SavedPlaylist>>` — all saved playlists
- `quickAddState: StateFlow<QuickAddState>`
- `refreshPlaylists()`, `savePlaylist(name, items, eventUrl?, postedAt?)`, `deletePlaylist(id)`
- `quickAddLatest()` — full pipeline: LSRGFinder → WeeklyPostParser → PlaylistBuilder → save (deduplicates by name)
- `dismissQuickAddStatus()` — resets to Idle

### UI Components (`ui/component/`)

#### MiniPlayer.kt

`MiniPlayer(player, playlistManager, onClick)` — compact bar with track title, author, play/pause. Polls player every 100ms.

#### PlayerControls.kt

`PlayerControls(currentPosition, duration, isPlaying, isBuffering, playbackSpeed, currentIndex, playlistSize, onSeek, onPlayPause, onPrevious, onNext, onShowSpeedDialog)` — slider, Previous/Rewind 15s/Play|Pause/Forward 15s/Next buttons, speed button.

#### TrackInfoCard.kt

`TrackInfoCard(item, onClick?)` — displays track title, author, source.

#### SpeedDialog.kt

`SpeedDialog(playbackSpeed, onSpeedChange, onReset, onDismiss)` — slider 0.5x–3.0x, reset to 1.0x.

### UI Screens (`ui/screen/`)

#### MainMenuScreen.kt

`MainMenuScreen(playlists, onCreateNew, onOpenPlaylist, onQuickAddLatest, onDeletePlaylist)` — playlist list with Quick Add, Create New, delete with confirmation dialog. Shows formatted posted date via `formatDate(timestamp)`.

#### CreatePlaylistScreen.kt

`CreatePlaylistScreen(initialUrl?, onPlaylistCreated, onCancel)` — URL input, "Add Single Post" and "Load Event" buttons, temp playlist display, save button.

#### PlaylistDetailScreen.kt

`PlaylistDetailScreen(playlist, player, playlistManager, positionManager, onBack, onPlayAll, onPlayTrack)` — playlist header (clickable eventUrl), Play All button, track list with progress info and current-track highlight.

#### PlayerScreen.kt

`PlayerScreen(player, playlistManager, playbackService, playerViewModel, onBack)` — current track info (clickable URL), prev/next track cards with resume position, PlayerControls, speed dialog.

### UI Navigation (`ui/navigation/`)

#### AppNavigation.kt

`AppNavigation(player, playlistManager, playbackService, initialUrl?)` — NavHost with routes:

| Route | Screen | Notes |
|---|---|---|
| `main_menu` | MainMenuScreen | Start destination (no initialUrl) |
| `create_playlist` | CreatePlaylistScreen | Start destination (with initialUrl) |
| `playlist_detail/{playlistId}` | PlaylistDetailScreen | Arg: playlistId string |
| `player` | PlayerScreen | |

MiniPlayer shown on all screens except `player`. Quick Add success auto-navigates to playlist detail.

### UI Theme (`ui/theme/`)

- **Color.kt** — Purple/PurpleGrey/Pink in 80 (dark) and 40 (light) variants
- **Theme.kt** — `WeeklySequencesAudioTheme`, dynamic color on Android 12+, static fallback
- **Type.kt** — Material3 typography, custom bodyLarge (16sp, 24sp line height)

---

## Manifest Summary

**Permissions:** `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `BLUETOOTH` (≤30), `BLUETOOTH_CONNECT` (≤32)

**Components:**
- `MainActivity` — singleTask, handles `MAIN/LAUNCHER`, `SEND` (text/plain), `VIEW` (lesswrong.com URLs)
- `PlaybackService` — foreground mediaPlayback, `MediaSessionService` intent filter

**Config:** `networkSecurityConfig` disables cleartext; SharedPreferences excluded from cloud backup, included in device transfer.

---

## Dependencies

From `gradle/libs.versions.toml`:

| Library | Version |
|---|---|
| AGP | 9.0.0 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.09.00 |
| Media3 (ExoPlayer) | 1.2.0 |
| OkHttp | 4.12.0 |
| Retrofit | 2.9.0 |
| Navigation Compose | 2.7.6 |
| Coroutines | 1.7.3 |
