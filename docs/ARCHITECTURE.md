# Cloven — Technical Blueprint & Architecture

Cloven is a personal, offline-first Android reader for text-based PDF e-books with a
local reading-habit tracker and a compact clover-and-streak home-screen widget.
Everything runs on the device: no account, no backend, no network permission.

## 1. Product requirements (condensed)

| Area | Requirement |
|---|---|
| Library | Import PDFs via the system picker, bookshelf grid, Continue Reading, search (title/author), filters (All / Reading / Finished / Favorites), details sheet, safe deletion |
| Reading | Reflowable text mode (typography, backgrounds, TOC, bookmarks) and original PDF mode (page rendering, zoom, page jump) |
| Progress | Stable logical position that survives typography / orientation changes; separate PDF page position; bookmarks for both modes |
| Tracking | Active reading time only while the reader is visible and in use; daily totals; current/longest streak; configurable daily goal |
| Widget | Clover + current streak only; reads the same Room data as the app |
| Privacy | No INTERNET permission, no analytics, no cloud backup of books or history |

## 2. Technology choices

| Concern | Choice | Why |
|---|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 (custom theme) | Required stack |
| Architecture | MVVM, repositories, `StateFlow` UI state, manual constructor DI (`AppContainer`) | App is a single module with ~6 repositories; Hilt would add build cost without benefit |
| Relational data | Room 2.8 (KSP) | Books, text blocks, progress, bookmarks, sessions with FKs and transactions |
| Simple prefs | DataStore Preferences | App theme, daily goal, reader typography |
| Navigation | Navigation Compose (string routes) | Three top-level destinations + reader |
| Original PDF rendering | `android.graphics.pdf.PdfRenderer` (framework, API 21+) | Zero dependencies, pdfium quality, bounded memory (one page at a time) |
| Text extraction | PdfBox-Android 2.0.27 (Apache-2.0) | Mature local parser with per-page `PDFTextStripper`, metadata, encryption detection. The framework renderer has no text API below API 35 |
| Widget | Classic `AppWidgetProvider` + `RemoteViews` | Renders synchronously inside the update broadcast. Glance 1.2 was implemented first but its WorkManager-backed render job sat in JobScheduler for minutes after a reboot (verified on the emulator), showing a spinner; it was replaced |
| Fonts | Literata (OFL), Tinos (OFL, metric-compatible with Times New Roman), Nunito (OFL, UI), plus system serif/sans | Licensed for redistribution; licenses in `app/src/main/assets/licenses/` |

**SDK levels:** `minSdk 26` (Android 8.0 — native `java.time`, variable fonts, adaptive icons;
covers the vast majority of active devices), `compileSdk 37`, `targetSdk 36`.

The four PDF concerns are separate components:

1. **Rendering** – `data/pdf/PdfPageRenderer.kt` (framework `PdfRenderer`, mutex-guarded, LRU bitmap cache in the UI layer).
2. **Extraction** – `data/pdf/PdfTextExtractor.kt` (PdfBox, page-by-page, temp-file memory mode).
3. **Structured representation** – `data/pdf/TextNormalizer.kt` → ordered `TextBlock`s (paragraph/heading, source page, char offset) stored in Room.
4. **Reflowable rendering** – `ui/reader/ReflowableReader.kt` (`LazyColumn` of blocks).

## 3. System architecture

```
ui (Compose screens + ViewModels)
 ├─ library/   LibraryScreen, BookDetailsSheet, LibraryViewModel
 ├─ reader/    ReaderScreen, ReflowableReader, PdfReader, sheets, ReaderViewModel
 ├─ activity/  ActivityScreen, ActivityViewModel
 └─ settings/  SettingsScreen, SettingsViewModel
        │  StateFlow<UiState>  ▲ events
        ▼
data (repositories)                         domain (pure Kotlin, unit-tested)
 ├─ LibraryRepository ── BookDao             ├─ StreakCalculator
 ├─ ImportManager ── PdfImporter             ├─ ActiveTimeAccumulator
 │     ├─ PdfTextExtractor (PdfBox)          ├─ ProgressMath (offset ↔ block)
 │     ├─ PdfPageRenderer (framework)        ├─ BookStatusRules
 │     ├─ TextNormalizer                     └─ PositionValidator
 │     └─ BookStorage (private files)
 ├─ ReadingRepository ── ProgressDao, BookmarkDao
 ├─ ActivityRepository ── SessionDao
 └─ PreferencesRepository ── DataStore
tracking/ ReadingSessionRecorder (lifecycle-aware, flushes sessions)
widget/   StreakWidget (RemoteViews) + StreakWidgetReceiver, TimeChangeReceiver, WidgetUpdater
```

`AppContainer` (created in `ClovenApp`) owns the database, DataStore, repositories and an
application `CoroutineScope` used for imports, so an import continues if the user leaves the
library screen.

## 4. Database schema (Room, version 1, schema exported to `app/schemas/`)

| Table | Key | Columns (abridged) | Relations / indexes |
|---|---|---|---|
| `books` | `id` (UUID text) | title, author, description, fileName, coverFileName, fileSizeBytes, pageCount, importedAt, lastOpenedAt, status, isFavorite, extractionStatus, totalChars, blockCount, processingError | idx lastOpenedAt |
| `text_blocks` | (bookId, blockIndex) | pageIndex, kind (PARAGRAPH/HEADING), charStart, text | FK → books **CASCADE** |
| `reading_progress` | bookId | textCharOffset, pdfPageIndex, lastMode, percent, updatedAt | FK → books **CASCADE** |
| `bookmarks` | id (auto) | bookId, mode, textCharOffset?, pdfPageIndex?, snippet, note?, createdAt | FK → books **CASCADE**, idx bookId |
| `reading_sessions` | id (UUID text) | bookId?, startedAt, endedAt, activeMs, localDate (ISO), zoneId | FK → books **SET NULL**, idx localDate |

* `percent` in `reading_progress` is intentionally stored (not derived) because the library
  must show progress for whichever mode was used last, and PDF-only books have no text length.
* **Daily activity is not a separate table.** Daily totals are the SQL aggregate
  `SUM(activeMs) GROUP BY localDate` over `reading_sessions`, so there is exactly one source of
  truth and statistics can always be recomputed.
* **Retention decision:** deleting a book removes its blocks, progress and bookmarks, but keeps
  its reading sessions (bookId set to NULL) so past streaks are not rewritten by tidying the shelf.
* **Migrations:** explicit `Migration` objects only; `fallbackToDestructiveMigration` is never
  used, so an upgrade can never silently wipe the library.

## 5. Files & storage

```
filesDir/books/<bookId>.pdf     original PDF copy (never modified)
filesDir/covers/<bookId>.png    first-page thumbnail
filesDir/import-tmp/            in-flight imports (swept on start)
```
Only file *names* are stored in Room; paths are resolved against `filesDir` at runtime.
Deletion order: DB transaction first, then files. An orphan sweep at startup removes files in
`books/`, `covers/` and `import-tmp/` that no DB row references (older than 1 h).

## 6. Import pipeline (`PdfImporter`)

1. Copy the picked URI to `import-tmp/<uuid>.pdf` (cancellable; checks coroutine cancellation per 64 KB).
2. Reject empty files and files without a `%PDF-` header in the first 1 KB.
3. Probe with `PdfRenderer`: page count, password detection (`SecurityException`), corruption (`IOException`), render page 1 → cover.
4. Extract text with PdfBox page by page (progress reported per page). Failures here do **not** abort the import — the book is stored as PDF-only with `extractionStatus = FAILED` and the error message.
5. Normalize into blocks; `NO_TEXT` when there is essentially no extractable text (scanned PDFs).
6. Move the temp file to `books/<id>.pdf`, write cover, then insert book + blocks in one transaction.
7. Any failure before the transaction deletes all written files → no partial records.

## 7. Reading-position model

* **Text mode:** a global character offset into the normalized text (`textCharOffset`).
  `ProgressMath.locate()` binary-searches block `charStart`s to get *(blockIndex, fraction within block)*.
  On save, the fraction is the first visible block's scrolled-off pixel share; on restore the
  reader scrolls to the block and re-applies the fraction after measuring it. Because the anchor is
  text, not pixels, it survives font size, spacing, margins, orientation and screen changes.
  Percent = offset / totalChars, clamped to 0–100; at the end of the list it is 100.
* **PDF mode:** zero-based page index.
* **Mode switching:** text → PDF uses the block's source page; PDF → text uses the first block on
  or after that page. Both positions are stored separately so neither mode loses its place.
* **Persistence:** debounced (scroll idle ≥ 0.8 s) and throttled DB writes, plus a flush on
  `ON_STOP` and in `onCleared`.
* **Invalid positions** (regenerated content, shorter text) are clamped by `PositionValidator`.

## 8. Reading sessions & streaks

* `ReadingSessionRecorder` starts a session when the reader is `RESUMED`, stops on `PAUSED`
  (screen lock, home, another app) and while sheets/dialogs inside the reader are open.
* Elapsed time uses `SystemClock.elapsedRealtime()` (monotonic). Interaction (scroll/tap) keeps the
  session alive; after **5 minutes without interaction**, time stops accruing.
* The session row is written every 30 s, so process death loses at most 30 s; rows are upserted by
  id so nothing is double-counted.
* Each session belongs to the local calendar date (and zone) at which it was recorded. If the date
  changes mid-session (midnight), the session is closed and a new one starts for the new day.
* **Qualifying day:** ≥ 5 minutes of active reading (documented default). **Daily goal:** default
  20 minutes, configurable 5–120. They are independent.
* **Current streak:** consecutive qualifying days ending today, or ending yesterday if today has not
  qualified yet (so the streak doesn't show 0 every morning). It becomes 0 once a full day is missed.
* **Time zones:** dates are stored as the local date where the reading happened. Flying west can
  produce a qualifying date one day "ahead" of the new local today; the calculator accepts an
  anchor of today + 1 for that case. Flying east across the date line can skip a calendar date and
  break a streak — documented limitation.
* `StreakCalculator` is a pure function of `(dailyTotals, today, thresholdMs)`, unit-tested with fixed dates.

## 9. Widget

* `StreakWidgetReceiver` (`AppWidgetProvider`) reads `ActivityRepository.currentStreak()` — the same
  session aggregate as the Activity screen — on a background coroutine under `goAsync()` and pushes
  `RemoteViews`: the clover (`logo.png`), a fixed two-line label ("Your" / "Reading Strike") and the
  streak number. On Android 12+ a size-mapped `RemoteViews` lets the launcher choose between that
  layout and a compact clover-and-number layout for instances narrower than 170 dp (e.g. 1×1 widgets
  placed by an earlier version). Older Android versions choose from the reported widget width.
* Update triggers: the system update broadcast (placement, boot, `updatePeriodMillis = 30 min`),
  `TIMEZONE_CHANGED` / `TIME_SET`, and — while the app process is alive — every emission of the
  live streak `Flow` (session flushes, a day first qualifying, midnight roll-over), coalesced to at
  most one update per 2 s by `WidgetUpdater`. No background service, no WorkManager.
* A missed day is reflected within ~30 min after midnight even if Cloven is not opened; Android may
  delay periodic updates further (documented limitation).
* Tap opens Cloven on the Reading Activity screen.

## 10. Privacy / backup

* The merged manifest requests **no** permissions (verified with `aapt2 dump permissions`); the only
  entry is AndroidX's internal signature-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
* `android:allowBackup="false"` and data-extraction rules that exclude every domain from **cloud**
  backup: Auto Backup would upload books and reading history, contradicting the local-first goal.
  Direct device-to-device transfer (cable/local migration to a new phone) is left allowed.
  Consequence: uninstalling Cloven deletes the library. Users can export a PDF copy from the details sheet.
* No logging of book content or full paths.

## 11. Testing strategy

* **JVM unit tests:** `StreakCalculator`, `ActiveTimeAccumulator`, `ProgressMath`,
  `TextNormalizer`, `BookStatusRules`, `PositionValidator`, title cleaning.
* **Instrumented tests:** Room DAOs (cascade/set-null), import pipeline with generated PDFs
  (normal, long, no metadata, empty, corrupted, truncated, encrypted, text-less, cancelled),
  file cleanup/orphan sweep, persisted streak + preferences, widget RemoteViews rendering.

## 12. Major risks

| Risk | Mitigation |
|---|---|
| PDF extraction quality (reading order, hyphenation, headers) | Content-stream order (no position sort), dehyphenation, page-number stripping, conservative heading heuristics; original PDF mode always available |
| Memory with large PDFs | PdfBox temp-file mode, page-at-a-time extraction, bounded bitmap cache, capped render width |
| Position drift across typography changes | Offset-based anchor + post-measure fraction correction |
| Widget staleness | Shared Room source + explicit refresh triggers + 30 min periodic |
| Data loss on uninstall | Documented; PDF export available |
