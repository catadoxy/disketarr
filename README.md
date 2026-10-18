# Disketarr

Native Android client for the self-hosted **Floppy** media tracker.  
Built with Kotlin and Jetpack Compose against the Floppy REST API.

## Features

- Setup flow for Floppy base URL + API token (HTTP/HTTPS, LAN/Tailscale friendly)
- Search across movies, TV, anime, manga, comics, games, books, music, podcasts, board games
- List and grid result layouts with pagination
- Media details:
  - Track / untrack
  - Mark watched / unmark
  - Rating (0.0–10.0 slider)
  - Season & episode progress for shows
  - Per-series reminder bell
- Browse tabs: Discover, Planned, In progress, History, Upcoming, Watched
- History loads 30 items initially for faster first paint
- Saved server profiles for quick switching
- Offline cache (Room) for previously viewed pages
- Daily upcoming-episode reminders (WorkManager) with configurable lead time
- Deep links: `disketarr://details/{source}/{type}/{id}`
- Home-screen widget with upcoming episodes (scrollable list)
- Statistics split by Shows/Series and Movies
- Categories filter (Movies/TV) to show/hide bottom tabs
- Search recents + debounced typing
- Multiple themes (Blue, Purple, Ocean, Forest, Light) following system dark mode

## Screenshots

| Shows | Movies | Statistics |
| --- | --- | --- |
| ![Shows](docs/screenshots/shows.jpg) | ![Movies](docs/screenshots/movies.jpg) | ![Statistics](docs/screenshots/statistics.jpg) |

| Details | Settings |
| --- | --- |
| ![Details](docs/screenshots/details.jpg) | ![Settings](docs/screenshots/settings.jpg) |

## Tech Stack

- Kotlin + Jetpack Compose (Material 3)
- Hilt (DI), Navigation Compose
- Retrofit + OkHttp + Moshi
- Room (offline cache)
- Coil (images)
- WorkManager (reminders)
- EncryptedSharedPreferences (secure token storage)

## Requirements

- Android Studio (latest stable)
- Android SDK (compileSdk 35)
- Min SDK 26+
- A running Floppy instance with an App token (`watchlist:read/write`, `catalog:read`, `lists:write`)

## Getting Started

1. Open the project in Android Studio and sync Gradle.
2. Build & run on an emulator or device.
3. Enter your Floppy base URL and API token in **Settings → Login**.
4. Save & test the connection.

## Build

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

## Releases

Pushing a tag starting with `v` (e.g. `v0.3.0`) triggers the GitHub Actions release
workflow, which builds the release APK and attaches it to a GitHub Release.

## Project Structure

- `app/src/main/java/io/github/catadoxy/disketarr/ui` — screens, navigation, theme
- `app/src/main/java/io/github/catadoxy/disketarr/viewmodel` — ViewModels and UI state
- `app/src/main/java/io/github/catadoxy/disketarr/data` — models, API client, cache, settings
- `app/src/main/java/io/github/catadoxy/disketarr/di` — Hilt modules
- `app/src/main/java/io/github/catadoxy/disketarr/workers` — WorkManager tasks
- `app/src/test/java/...` — unit tests

## Notes

- Cleartext HTTP is enabled for self-hosted instances without TLS (see `network_security_config.xml`).
- Base URLs without a scheme default to `http://` for LAN/loopback addresses and `https://` otherwise.
- If an external auth layer (Authelia/SSO) fronts Floppy, ensure `/api/v1/*` is reachable by mobile clients.
- Settings includes saved server profiles, category filters, reminder lead time, and a permissions/diagnostics probe.