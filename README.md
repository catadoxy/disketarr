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
- Deep links: `disketarr://details/{source}/{type}/{id}[?library=anime]`
- Home-screen widget with upcoming episodes (scrollable list)
- Statistics split by Shows/Series and Movies
- Categories filter (Movies/TV/Anime) to show/hide bottom tabs
- Library aware: a title tracked in Floppy's TV library shows in the Shows tab, one tracked in its Anime library in the Anime tab - the app asks Floppy which library a title is in rather than guessing
- The Anime tab follows Floppy's *anime library mode*: when the anime are filed in the TV library, there is no Anime library to show and the tab disappears
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
- A running Floppy instance and an App token (see below)

## API token permissions

The app talks to Floppy's REST API, so the token needs a few scopes. Create one
under **Floppy → Settings → Integrations → App tokens**, and tick exactly these:

| Scope | What the app uses it for |
| --- | --- |
| `watchlist:read` | The library: the Shows/Movies/Anime tabs, tracked-media lists, details, seasons and episodes, history, and the calendar behind the Upcoming tab |
| `watchlist:write` | Tracking and untracking a title, marking an episode watched or unwatched, and rating |
| `catalog:read` | Search and the Discover rows |
| `lists:read` | The list of your Floppy lists, for "add to list" |
| `lists:write` | Adding a title to one of your lists |
| `statistics:read` | The Statistics tab |
| `user:read` | Reading Floppy's *anime library mode*, which decides whether the app shows an Anime tab |

Floppy's default **Tracking** preset only covers `watchlist:read/write` and
`catalog:read`. The other four have to be ticked as well, or the matching part
of the app stays empty - no lists to add to, no statistics, and the Anime tab
may not match Floppy's setting.

Two things to know:

- A missing scope shows up as **HTTP 403**, not as a connection error.
  **Settings → Permissions & Diagnostics** probes every route the app uses and
  prints the scope it needs, so a 403 there points at the scope to add.
- `user:read` is the one that is easy to forget. Without it the app cannot see
  whether your anime live in the TV or in the Anime library, and it falls back to
  what it last knew.

The token is sent as `Authorization: Bearer <token>` and `X-API-Key: <token>`, and
is stored in EncryptedSharedPreferences on the device.

## Getting Started

1. Open the project in Android Studio and sync Gradle.
2. Build & run on an emulator or device.
3. Enter your Floppy base URL and API token in **Settings → Login**.
4. Save & test the connection.
5. If something is empty, open **Settings → Permissions & Diagnostics** and look
   for a 403 - it names the scope the token is missing.

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
- Floppy files a tracked title in one *library* (`tv`, `anime`, `movie` or `season`), and the same title can be in more than one of them. The app addresses a title by its library, so a title tracked in the TV library shows up in the Shows tab and the same title in the Anime library in the Anime tab. Which library the anime belong to is Floppy's `anime_library_mode` setting, and the app follows it.
- The widget's *Shows* list is the TV library, matching the Shows tab. Marking an episode watched from the widget writes into the library the row came from.