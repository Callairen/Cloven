# Cloven

A personal, offline-first Android reader for text-based PDF e-books, with a quiet reading-habit
tracker and a compact clover-and-streak home-screen widget.

* No account, backend, cloud, analytics or ads — the app has **no INTERNET permission**.
* Import PDFs with the system file picker; Cloven keeps a private copy.
* Read in **reflowed text** (your typeface, size, spacing, margins, backgrounds) or the **original PDF** (exact layout, zoom).
* Progress, bookmarks and reading statistics are stored locally and survive restarts.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the technical design,
[docs/CHECKLIST.md](docs/CHECKLIST.md) for feature/test status and known limitations, and
[docs/LOGO.md](docs/LOGO.md) for the logo assets and how they are packaged.

## Requirements

| | Version |
|---|---|
| minSdk | 26 (Android 8.0) |
| targetSdk | 36 |
| compileSdk | 37 |
| JDK for Gradle | 17–25 (built with the JetBrains Runtime 25 bundled in Android Studio) |
| Gradle | 9.8.2 (wrapper included) |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.0.21 + KSP 2.0.21-1.0.28 |

Key libraries: Compose BOM 2026.09.00 (Material 3 1.4), Room 2.8.5, DataStore 1.3.0,
Navigation Compose 2.10.2, PdfBox-Android 2.0.27.0. The widget uses plain `RemoteViews`. Versions live in
[gradle/libs.versions.toml](gradle/libs.versions.toml).

## Build

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew connectedDebugAndroidTest
```

```bash
./gradlew assembleRelease
```

On Windows use `gradlew.bat`. `JAVA_HOME` must point to a JDK 17–25 (for example
`C:\Program Files\Android\Android Studio\jbr`). `local.properties` must contain `sdk.dir`.

The release build is minified and, for personal sideloading, signed with the local debug key
(see `app/build.gradle.kts`). Replace the signing config before distributing it.

Outputs:

* `app/build/outputs/apk/debug/app-debug.apk`
* `app/build/outputs/apk/release/app-release.apk`

Prebuilt copies from the verified build are in `dist/` (`cloven-1.0.0-release.apk`, 9.5 MB, recommended; `cloven-1.0.0-debug.apk`).

## Install the APK

With a phone connected over USB (Developer options → USB debugging enabled):

```bash
adb install -r dist/cloven-1.0.0-release.apk
```

Or copy the APK to the phone and open it; Android will ask you to allow installs from that
source. Debug and release builds share the same application id and signing key, so either
can update the other.

### Adding the widget

Long-press the home screen → *Widgets* → *Cloven* → drag **Cloven streak** onto the home
screen (3×1 by default). It shows the clover, the label "Your / Reading Strike" and your current
streak; tapping it opens Reading Activity. If it is resized narrower than about 170 dp, it shows
just the clover and the number.

## Using Cloven

1. **Import** — tap *Import PDF* and pick a file. Text is extracted on the device; long books
   show page-by-page progress and can be cancelled.
2. **Read** — tap a book. Tap the page to show/hide controls. *Style* changes typography and
   background; *Original PDF* switches to the page view (pinch or double-tap to zoom);
   *Contents* lists detected chapters and your bookmarks.
3. **Manage** — long-press a book (or its ⋮ button) for details: status, favorite, *Save a copy*, *Remove*.
4. **Activity** — today's reading time, daily goal, current and longest streak, last 7 days.
5. **Settings** — theme, daily goal, default reading style, storage and privacy information.

## Project layout

```
app/src/main/java/app/cloven/
  domain/      pure Kotlin rules: streaks, active time, positions, status, search
  data/db/     Room entities, DAOs, database
  data/pdf/    PDF import, PdfBox text extraction, normalizer, framework page renderer
  data/files/  private file storage
  data/prefs/  DataStore preferences
  data/repo/   repositories
  tracking/    reading-session recorder
  widget/      RemoteViews streak widget
  ui/          Compose screens (library, reader, activity, settings), theme, components
app/src/test/         JVM unit tests
app/src/androidTest/  instrumented tests (Room, PDF import, preferences, widget)
app/schemas/          exported Room schema (for migrations)
branding/source/      original logo artwork (unmodified)
tools/                generate_brand_assets.py (packaged icons/logos from branding/source)
```
