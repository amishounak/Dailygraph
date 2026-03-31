# Dailygraph

A personal daily journal app for Android that helps you capture your thoughts, experiences, and ideas — beautifully and effortlessly. Every entry is enriched with your current location and weather, giving you a vivid snapshot of the moment.

Built with Kotlin, MVVM architecture, Material 3 design, and the WordPress Aztec rich text engine.

## Download

**Latest: v1.0.2** — See [CHANGELOG.md](CHANGELOG.md) for version history.

| Platform | Link |
|---|---|
| Sideload APK | [Dailygraph-v1.0.2.apk](https://github.com/amishounak/Dailygraph/releases/download/v1.0.2/Dailygraph-v1.0.2.apk) |
| Google Play (closed testing) | [Join the beta](https://play.google.com/apps/testing/com.dailygraph.app) |

> Enable **Install from unknown sources** in your Android settings before sideloading.

### ⚠️ Switching from Play Store to Sideload APK

If you have the **Play Store (closed testing) version** installed, you **cannot** install the sideload APK directly on top of it. Google Play re-signs apps with its own key — Android blocks installing a different-key APK over an existing app.

**Steps to switch:**
1. **Export your journals first** — Menu → Export Notes → save the JSON backup file
2. Uninstall Dailygraph from your device
3. Install `Dailygraph-v1.0.1.apk`
4. Restore — Menu → Import Notes → select your backup file

## Features

### Rich Text Editor
Write with full formatting — bold, italic, underline, strikethrough, ordered and unordered lists, headings, blockquotes, and links. Powered by WordPress Aztec, a battle-tested native HTML editor used by millions in the WordPress mobile app. The editor stores content as HTML and renders it natively through Android's EditText system — no WebViews, no lag, fully native feel. In view mode, text is selectable (Copy/Select All) but protected from accidental edits (Cut and Paste are disabled).

### Multiple Journals
Organize your life into separate journals — Personal, Work, Travel, Ideas, or anything you choose. Each journal keeps its entries completely isolated. Switch between journals instantly from the toolbar dropdown. Create, rename, or delete journals from the Manage Journals screen. Double-tap any journal card to set it as the default — the default journal loads automatically every time you open the app.

### Automatic Context
Every entry automatically captures the date, time, GPS location (city and country), and the current temperature at the moment of creation. This metadata is displayed in a header card above your entry, giving you a rich, contextual snapshot of when and where you wrote. Location uses Google Play Services FusedLocationProvider; weather data comes from the Open-Meteo API (no API key required).

### Version History
Never lose a word. Every time you edit an entry, the app saves the previous version and creates a new one with an incremented version number. You can browse all previous versions of any entry through the Edit History screen, view them in read-only mode, or delete old versions individually or in bulk using multi-select. A version badge (e.g., "v3") appears on entries that have been edited.

### Search & Date Navigation
Find any entry instantly with full-text search across the current journal. Filter entries by date using the Material calendar picker — the calendar starts on Sunday and blocks future dates. Once a date is selected, navigate forward and backward day-by-day using the arrow buttons (navigation stops at today). Entries in the listing are grouped by month with section headers — the current month shows no header for a clean look.

### Import & Export
Back up your entire journal collection to a single JSON file — all journals, all entries, all versions are included. Import on another device or restore from a backup with automatic duplicate detection that prevents creating duplicate entries. The export format (version 3) supports multi-profile data with full version history.

### Appearance
Choose between Light, Dark, or Auto (follows system) themes from the menu. The dark theme uses a true black background (`#000000`) with an iOS-inspired color palette — dark surface cards (`#1C1C1E`), accent blue (`#0A84FF`), and carefully tuned text contrast. Light mode uses clean whites with subtle gray backgrounds. All popup menus and dropdowns have rounded corners with a polished, modern feel.

### Batch Operations
Long-press any entry to enter multi-select mode. Select individual entries or use Select All, then delete them in one action with confirmation. The same multi-select pattern works in Edit History for managing old versions of an entry.

### About Page
Accessible from the menu, the About page presents a clean overview of the app with its icon, version number, tagline, and detailed feature cards explaining every capability.

## Tech Stack

| Component       | Technology                          |
|-----------------|-------------------------------------|
| Language        | Kotlin 100%                         |
| Architecture    | MVVM (ViewModel + LiveData + Room)  |
| Database        | Room (SQLite) with migrations       |
| Rich Text       | WordPress Aztec v2.1.6              |
| Async           | Kotlin Coroutines + LiveData        |
| Location        | Google Play Services FusedLocation  |
| Weather         | Open-Meteo API via OkHttp           |
| UI Framework    | Material 3 + ViewBinding            |
| Build System    | Gradle KTS with KSP                 |
| Min SDK         | 26 (Android 8.0 Oreo)              |
| Target SDK      | 35 (Android 15)                     |

## Project Structure

```
app/src/main/java/com/diary/app/
├── DailygraphApp.kt              # Application class, theme initialization
├── data/
│   ├── Note.kt                   # Entry entity (id, profileId, noteGroupId, version, content, etc.)
│   ├── NoteDao.kt                # Room DAO — all database queries
│   ├── NoteDatabase.kt           # Room database with v1→v2 migration
│   └── Profile.kt                # Journal entity (id, name, isDefault, createdAt)
├── repository/
│   └── NoteRepository.kt         # Data access layer, version management, default profile logic
├── viewmodel/
│   └── NoteViewModel.kt          # Business logic, profile switching, LiveData transformations
├── ui/
│   ├── MainActivity.kt           # Entry listing, search, calendar filter, date nav, import/export
│   ├── NoteDetailActivity.kt     # Entry view/edit with Aztec editor and toolbar
│   ├── NoteAdapter.kt            # Listing with monthly section headers and multi-select
│   ├── ProfileManagerActivity.kt # Journal CRUD, double-tap to set default
│   ├── ProfileAdapter.kt         # Journal list with Default badge and double-tap detection
│   ├── VersionHistoryActivity.kt # Edit history with multi-select delete
│   ├── VersionAdapter.kt         # Version list adapter
│   └── AboutActivity.kt          # App information and feature overview
└── utils/
    ├── ThemeHelper.kt            # Light/Dark/Auto theme via AppCompatDelegate
    ├── RichTextHelper.kt         # HTML-to-plain-text preview for listings
    ├── LocationWeatherHelper.kt  # FusedLocation + Open-Meteo weather
    ├── ImportExportHelper.kt     # JSON v3 export/import with duplicate detection
    ├── ProfilePreferences.kt     # SharedPreferences for current profile ID
    └── IOSStyleDialog.kt         # Rounded dialog helper
```

## Build & Run

1. Open in Android Studio (Hedgehog 2023.1.1 or later)
2. If prompted about Gradle JDK, select **Use Embedded JDK**
3. Sync Gradle — dependencies resolve from Maven Central and WordPress S3 repo
4. Build and run on a device or emulator running API 26+

If `org.wordpress:aztec:v2.1.6` fails to resolve, uncomment the JitPack fallback line in `app/build.gradle.kts`.

## Configuration

- **Package (Play Store)**: `com.dailygraph.app`
- **Internal namespace**: `com.diary.app` (do not change — would break Room migrations)
- **Repositories**: Google, Maven Central, WordPress S3 (`a8c-libs.s3.amazonaws.com`), JitPack (fallback)
- **Default journal name**: "Dailygraph" (for fresh installs)

## License

[MIT License](LICENSE) — Copyright (c) 2026 Shounak Datta

---

**Dailygraph** — Write your day, your way.
