# CLAUDE.md — Project Memory for Dailygraph

## What is this app?
Dailygraph is a personal daily journal app for Android targeting Play Store release. Built by Shounak (Angular developer by day, hobbyist Android developer). The app captures daily entries with rich text, auto location/weather, multiple journals, and version history.

## Tech Stack
- **Language**: Kotlin 100%
- **Architecture**: MVVM (ViewModel + LiveData + Room)
- **Database**: Room (SQLite), single `NoteDatabase` with `notes` and `profiles` tables
- **Rich Text**: WordPress Aztec library (`org.wordpress:aztec:v2.1.6`) — native EditText-based HTML editor
- **UI**: Material 3, ViewBinding, iOS-inspired design aesthetic (rounded cards, accent blue `#007AFF`)
- **Location**: Google Play Services FusedLocationProvider
- **Weather**: Open-Meteo API via OkHttp (no API key needed)
- **Build**: Gradle KTS, KSP for Room annotation processing, JVM toolchain 17

## Package Structure
- **applicationId**: `com.dailygraph.app` (Play Store identity)
- **namespace**: `com.diary.app` (internal R class, do NOT change — would break Room migrations)
- **Root**: `app/src/main/java/com/diary/app/`

## Key Architecture Decisions (DO NOT CHANGE)

### Single AztecText for View + Edit Mode
The app uses ONE `AztecText` instance for both viewing and editing entries. This was settled after multiple failed attempts:
- ❌ Separate `TextView` for view mode → breaks bullet/list/heading rendering (Android's `Html.fromHtml()` can't handle Aztec's HTML)
- ❌ `RichEditText` custom class → span boundary drift, format leaking between selections
- ❌ `wasabeef/richeditor-android` → WebView-based, not native feel
- ✅ Single `AztecText` with mode toggling:
  - **View mode**: `showSoftInputOnFocus = false`, `isCursorVisible = false`, `customSelectionActionModeCallback` blocks Cut/Paste (allows Copy/Select All only), `onFocusChangeListener` hides keyboard
  - **Edit mode**: All above reversed, `customSelectionActionModeCallback = null`, toolbar visible

### Aztec Toolbar Styling Constraints
Aztec's toolbar (`AztecToolbar`) manages its own internal button backgrounds/states. **NEVER** set `child.background` or `child.setPadding()` on Aztec's internal buttons — this destroys their rendering (shows dark filled rectangles). Safe operations:
- `setColorFilter()` on ImageView icons ✅
- `setBackgroundColor()` on the toolbar itself ✅ (must be in `post{}` because Aztec overrides XML attributes)
- `visibility = GONE` on media/html buttons ✅

### Listing Page Preview Rendering
`RichTextHelper.loadForPreview()` strips HTML to plain text via regex — no `Html.fromHtml()`. This avoids the recurring bullet/list rendering bugs. Ordered lists are converted to "1. item 2. item" and unordered to "• item" before tag stripping. Full rich rendering only happens in AztecText.

### Default Journal System
- `Profile.isDefault` is the source of truth (persisted in Room)
- On startup, `loadCurrentProfile()` tries: saved profile → isDefault profile → first available → create new "Dailygraph"
- If no profile has `isDefault = true`, the current one is auto-marked
- `setDefaultProfile()` does `clearAllDefaults()` then marks the chosen one
- Double-tap detection in ProfileAdapter uses `System.currentTimeMillis()` timing (400ms), NOT GestureDetector (unreliable in RecyclerView)

## File Reference

### Data Layer
- `data/Note.kt` — Entry entity: id, profileId, noteGroupId, version, isLatest, date, time, temperature, location, content (HTML), timestamp, editedAt
- `data/Profile.kt` — Journal entity: id, name, isDefault, createdAt
- `data/NoteDao.kt` — Room DAO with all queries including `clearAllDefaults()`
- `data/NoteDatabase.kt` — Room DB with migration from v1→v2 (added profiles table). Pre-populates "My Journal" for migrating users
- `repository/NoteRepository.kt` — Thin wrapper over DAO + `setDefaultProfile()` transaction

### UI Layer
- `ui/MainActivity.kt` — Entry listing, search, calendar filter (Sunday start, no future dates), date nav arrows, profile spinner, multi-select, import/export
- `ui/NoteDetailActivity.kt` — Entry view/edit with Aztec, `setupViewMode()` / `enterEditMode()` / `exitEditMode()`, location/weather fetch, version indicator
- `ui/NoteAdapter.kt` — RecyclerView.Adapter (NOT ListAdapter) with sealed class `ListItem` (SectionHeader + NoteEntry). Monthly section headers for past months.
- `ui/ProfileManagerActivity.kt` — Journal CRUD, double-tap default
- `ui/ProfileAdapter.kt` — Journal list with isDefault badge, double-tap detection
- `ui/VersionHistoryActivity.kt` — Edit history with multi-select delete
- `ui/VersionAdapter.kt` — Version list
- `ui/AboutActivity.kt` — App info page with icon, version, feature cards

### Utils
- `utils/RichTextHelper.kt` — `isHtml()`, `loadForPreview()` (plain text), `loadForDisplay()` (unused but kept), `cleanContent()` (strips old `<bg>`/`<sz>` custom tags)
- `utils/ThemeHelper.kt` — Light/Dark/Auto via AppCompatDelegate
- `utils/LocationWeatherHelper.kt` — FusedLocation + Open-Meteo
- `utils/ImportExportHelper.kt` — JSON export v3 (multi-profile), import with duplicate detection
- `utils/ProfilePreferences.kt` — SharedPreferences for current profile ID
- `utils/IOSStyleDialog.kt` — Rounded dialog helper

### Key Layouts
- `layout/activity_note_detail.xml` — Single AztecText + hidden AztecSource + AztecToolbar in toolbarContainer
- `layout/activity_main.xml` — Spinner, search bar, date nav bar, RecyclerView, FAB
- `layout/activity_about.xml` — App icon, version, tagline, feature cards in ScrollView
- `layout/item_section_header.xml` — Monthly divider in listing
- `layout/item_profile.xml` — Journal card with "Default" chip (`chipCurrent`)

### Theming
- `values/themes.xml` — Theme.Dailygraph, IOSDialogTheme, RoundedPopupMenu, RoundedDropDown, SpinnerDropDownItem
- `values-night/themes.xml` — Dark overrides (windowLightStatusBar=false, popup styles)
- `values/colors.xml` — accent #007AFF, toolbar_icon #555555, toolbar_icon_active #007AFF
- `values-night/colors.xml` — accent #0A84FF, surface #1C1C1E, background #000000, toolbar_icon #AAAAAA

### Dependencies (build.gradle.kts)
- Aztec: `org.wordpress:aztec:v2.1.6` (Maven Central, fallback JitPack commented)
- Repositories: google(), mavenCentral(), WordPress S3 (`a8c-libs.s3.amazonaws.com`), jitpack.io

## Known Issues / Gotchas
1. **Old entries with corrupted HTML**: Entries from pre-Aztec custom editor may contain `<bg...>`, `<sz...>` tags or double-encoded HTML (`&lt;strong&gt;`). `cleanContent()` strips bg/sz but doesn't fix double-encoding.
2. **`toHtml()` parameter**: Some Aztec versions need `toHtml(false)` instead of `toHtml()`. If compile error, add `false`.
3. **Database migration**: NoteDatabase has migration from v1→v2. The INSERT in migration still says "My Journal" — this is correct for existing users migrating. New installs go through `createDefaultProfile()` which uses "Dailygraph".
4. **Spinner dropdown offset**: `dropDownVerticalOffset="40dp"` to push below toolbar. If toolbar height changes, this may need adjusting.
5. **Aztec toolbar background**: Must be set programmatically in `post{}` — Aztec overrides XML `android:background` during inflation.

## Conventions
- All dialogs use `R.style.IOSDialogTheme` with rounded corners
- Popup menus use `popup_rounded_bg.xml` (14dp corners, surface color, subtle stroke)
- Keyboard auto-opens in dialogs with EditText via `setOnShowListener` + `showSoftInput` with 150ms delay
- Entry dates are stored as formatted strings: "EEEE, MMMM d, yyyy" (e.g., "Monday, March 15, 2026")
- Times stored as "h:mm a" format
- Version system: each edit creates a new Note row with incremented version, previous row gets `isLatest = false`
- Export format: JSON v3 with `profiles[]` and `notes[]` arrays
- Main menu items: Manage Journals, Appearance, Export Notes, Import Notes, About
- About page: `AboutActivity` with `activity_about.xml` — shows app icon, version (read from PackageInfo), tagline, and feature cards in surface-colored CardViews

## Build Instructions

### Building the APK (from bash/MSYS terminal)
JAVA_HOME is not set globally — must point to Android Studio's bundled JDK:
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### local.properties (not in git)
Must exist at project root with SDK path. Write via Node to avoid bash backslash issues:
```bash
node -e "require('fs').writeFileSync('local.properties', 'sdk.dir=C\\\\:\\\\\\\\Users\\\\\\\\shoun\\\\\\\\AppData\\\\\\\\Local\\\\\\\\Android\\\\\\\\Sdk\n')"
```

### GitHub Repository
- Remote: https://github.com/amishounak/Dailygraph (public)
- Branch: main

## Known Fixes Applied

### API 35 Edge-to-Edge (fixed 2026-03-23)
Android 15 enforces edge-to-edge by default, causing toolbar to overlap the status bar and the Aztec formatting toolbar to hide behind the navigation bar.
**Fix**: Added `<item name="android:windowOptOutEdgeToEdgeEnforcement">true</item>` to `Theme.Dailygraph` in both `values/themes.xml` and `values-night/themes.xml`.

## Planned Features (not yet implemented)
- PIN / Biometric Lock
- Photo Attachments
- Daily Reminder Notification
- Entry Templates
- Mood Tracker
- Word/Entry Streak Counter
- Tags/Labels
- PDF Export
- Calendar Heatmap View
- End-to-End Encryption
- Widgets
- Cloud Backup
