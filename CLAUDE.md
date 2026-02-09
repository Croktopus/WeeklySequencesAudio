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
4. **PlaylistBuilder** — orchestrates AudioResolver across multiple posts in parallel

### Playback Layer

- `PlaybackService` (MediaSessionService) — foreground service owning ExoPlayer, MediaSession, and notifications. Saves playback position every 5 seconds and on pause/seek/disconnect.
- `PlaylistManager` — holds playlist state as `StateFlow<List<PlaylistItem>>` and current index as `StateFlow<Int>`.
- `PlaybackPositionManager` — SharedPreferences-based position persistence keyed by track URL.

### UI Layer

Three screens via NavHost in `AppNavigation`:
- `main_menu` — saved playlists list + Quick Add button
- `create_playlist` — URL input, event parsing, playlist building
- `player` — full playback controls, speed selector, playlist view

`PlayerViewModel` polls player state every 100ms. `MainViewModel` handles playlist CRUD and Quick Add async flow.

### State Management

All reactive state uses Kotlin `StateFlow` collected via `collectAsState()` in Compose. Playlists and positions persist to SharedPreferences via Gson serialization (`PlaylistStorage`).

### Networking

Retrofit + OkHttp. Three API interfaces in `ApiService`. `ApiClient` provides singleton Retrofit instances. All traffic is HTTPS (cleartext disabled via network security config).
