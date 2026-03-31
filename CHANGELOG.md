# Changelog — Dailygraph

All notable changes to Dailygraph are documented here.
Format: `[version] — date — description`

---

## [1.0.1] — 2026-03-30

### Changed
- Bumped `versionCode` 1 → 2, `versionName` "1.0" → "1.0.1"
- Updated `compileSdk` and `targetSdk` from 34 → 35 (Android 15, required by Google Play from 2026)
- Fixed status bar and navigation bar overlap caused by Android 15's enforced edge-to-edge mode
  (`android:windowOptOutEdgeToEdgeEnforcement = true` in both light/dark themes)
- Added release signing config in `app/build.gradle.kts` (keystore at `../release-keystore.jks`)

### Distribution
- Submitted to Google Play Store (closed testing / Alpha track) — package `com.dailygraph.app`
- Privacy policy published at `https://raw.githubusercontent.com/amishounak/Dailygraph/main/PRIVACY_POLICY.md`
- GitHub release created with sideload APK

### Known Limitation
- **Signing conflict**: Users who have the Play Store version installed cannot directly install the sideload APK over it. Google Play App Signing uses a different key than the sideload APK. Users must export journals → uninstall → install APK → import journals.

### Play Store Status (as of 2026-04-01)
- Track: Closed Testing (Alpha) — **Active**
- Release: 2 (1.0.1) — approved and live
- Countries: 177
- Testers: Shounak email list (5 emails as of 2026-04-01)
- 14-day countdown starts once 12+ testers opt in

---

## [1.0.0] — 2026-03-29

### Added
- Initial release
- Daily journal entries with rich text (WordPress Aztec HTML editor)
- Multiple journals (profiles) with double-tap to set default
- Auto-capture of location (FusedLocationProvider) and weather (Open-Meteo API) on entry creation
- Version history — every edit saves a new version; browse, view, or delete old versions
- Full-text search within current journal
- Calendar date filter (Sunday start, no future dates) with day-by-day arrow navigation
- Monthly section headers in entry listing
- JSON export/import (format v3) with multi-profile support and duplicate detection
- Multi-select with long-press dual-toolbar pattern
- Light / Dark / Auto theme (true black dark background)
- iOS-inspired Material 3 design (accent #007AFF / #0A84FF)
- Room database with v1→v2 migration (added profiles table)
- About page with version, tagline, and feature cards
- MIT License

### Distribution
- GitHub release with sideload APK
