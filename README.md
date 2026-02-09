# WeeklySequencesAudio

An Android audio player for the [LessWrong Sequences Reading Group (LSRG)](https://www.lesswrong.com/groups/LK6GNnKp8PDCkqcxx). Automatically finds the latest weekly event, resolves audio versions of the assigned posts, and plays them back.

## Features

- **Quick Add** — one tap to fetch the latest LSRG event and build a playlist from its posts
- **Custom playlists** — create, save, and manage multiple playlists
- **Background playback** — Media3/ExoPlayer service with media session and notification controls
- **Playback position persistence** — resume where you left off, even after the app is killed
- **Adjustable playback speed** — 0.5x to 3x
- **Bluetooth disconnect handling** — auto-pauses when headphones disconnect

## Building

Prerequisites: Android Studio and the Android SDK.

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/`.

## How it works

1. **LSRGFinder** scrapes the LSRG group page to find the latest event
2. **WeeklyPostParser** queries the LessWrong GraphQL API to extract post links from the event
3. **AudioResolver** resolves each post URL to its audio MP3 URL
4. **PlaybackService** plays the resulting playlist via ExoPlayer with full media session integration

All endpoints are public LessWrong APIs — no keys or authentication required.

## License

[MIT](LICENSE)
