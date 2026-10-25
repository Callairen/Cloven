# Cloven — Implementation checklist

Legend: ✅ implemented and verified (automated test and/or exercised on the emulator) ·
🟡 implemented, only partly verified · ⏸ deferred · ❌ not done

Verification environment: Android Emulator, API 35 (Google APIs, x86_64, 1080×2400), WHPX.
**No physical device was tested.**

## Library
| Feature | Status | Evidence |
|---|---|---|
| Import via system picker, private copy | ✅ | Emulator (debug + release APK); `PdfImportTest` |
| Reject empty / non-PDF / corrupted / password-protected, no traces left | ✅ | `PdfImportTest` (6 failure cases) |
| Truncated PDF handled consistently | ✅ | `PdfImportTest.truncatedPdf…` |
| Cancel mid-extraction leaves nothing behind | ✅ | `PdfImportTest.cancelledImportLeavesNothingBehind` |
| Import progress / success / error card | ✅ | Emulator |
| Metadata title/author, filename fallback | ✅ | Emulator; `TitleCleanerTest`, `PdfImportTest` |
| Cover from first page + clearly generated fallback | 🟡 | Real cover verified on emulator; fallback only seen in code/preview path |
| Continue Reading, bookshelf grid, progress | ✅ | Emulator |
| Search (partial, case/accent-insensitive), filters | ✅ | `LibraryQueryTest`; UI present on emulator |
| Details sheet: status, favorite, open, save a copy, remove | 🟡 | Sheet verified on emulator; delete/cleanup verified by `PdfImportTest`; "Save a copy" (SAF export) not exercised end to end |
| Storage-aware delete confirmation, cascade, orphan sweep | ✅ | `DatabaseTest`, `PdfImportTest` |

## Reading
| Feature | Status | Evidence |
|---|---|---|
| Reflowable text (paragraphs, headings, de-hyphenation) | ✅ | Emulator; `TextNormalizerTest` (16) |
| Typeface / size / bold / spacing / margins / alignment | ✅ | Emulator (size + background changed live); preferences persisted (`ActivityRepositoryTest`) |
| Backgrounds: Warm paper, Cream, Sepia, Espresso, Night | ✅ | Emulator (Espresso) |
| Position survives typography change | ✅ | Emulator: same paragraph kept (≈1 line drift) after 19→22 sp |
| Position restore after leaving / reopening | ✅ | Emulator (13 % and 8 % restored exactly); `ProgressMathTest` |
| Original PDF mode (render, scroll, page slider) | ✅ | Emulator (debug + release) |
| Pinch / double-tap zoom in PDF mode | 🟡 | Implemented; not exercised (no multi-touch in the adb-driven run) |
| Mode switching with page mapping | ✅ | Emulator (text 8 % → PDF page 1) |
| Bookmarks: add, list, persist, note, delete | 🟡 | Add + persisted list verified on emulator; note/delete UI not exercised; `DatabaseTest` covers persistence |
| Contents (detected headings) | 🟡 | Headings detected (`PdfImportTest`); TOC jump not exercised on emulator |
| Rotation / screen-size change | 🟡 | Same anchoring code path as typography change; not exercised on emulator |

## Tracking & widget
| Feature | Status | Evidence |
|---|---|---|
| Active time only while reader visible; idle cutoff; sheets excluded | ✅ | `ReadingSessionRecorderTest`, `ActiveTimeAccumulatorTest`; emulator recorded 193 s for ~3 min of real reading |
| Midnight split, periodic flush, no double counting | ✅ | `ReadingSessionRecorderTest` |
| Current / longest streak, qualifying days, reset, time-zone anchor | ✅ | `StreakCalculatorTest` (15) |
| Daily goal configurable, separate from 5-min threshold | ✅ | `ActivityRepositoryTest`; Settings UI on emulator |
| Activity screen with 7-day history | ✅ | Emulator (0 → 2 → 3 days with seeded history) |
| Widget: clover + streak from Room, nothing else | ✅ | `StreakWidgetTest` (4); emulator |
| Widget refreshes when today qualifies | ✅ | Emulator: changed 2 → 3 after crossing 5 min |
| Widget after process death + device reboot | ✅ | Emulator: showed 3 after reboot without opening the app |
| Widget tap opens Activity | ✅ | Emulator |

## Design & platform
| Feature | Status | Evidence |
|---|---|---|
| Light theme / designed dark theme / system | ✅ | Emulator |
| Reader background independent of app theme | ✅ | Emulator |
| No permissions (no INTERNET) | ✅ | `aapt2 dump permissions` on release APK |
| Cloud backup disabled (device transfer allowed) | 🟡 | Configured; backup behaviour not exercised |
| Release build with R8 | ✅ | Built, installed, import + both reading modes verified on emulator |

## Deferred / out of scope
* ⏸ OCR for scanned PDFs (books without text open in original PDF mode with an explanation).
* ⏸ Layout-based pagination (scroll-based reading is used).
* ⏸ Configurable streak threshold (fixed at 5 minutes, documented in-app).
* ⏸ Encrypted storage, export/import of reading history.
* ❌ **Official Cloven logo — not supplied.** A placeholder clover is used everywhere (docs/LOGO.md).

## Known limitations
* Text extraction follows the PDF's content order: complex multi-column layouts, tables, footnotes
  and running headers may appear in odd places in reflowed mode. Original PDF mode is always available.
* A page that ends mid-sentence is merged with the next page; a page ending with an unpunctuated
  line that is *not* mid-sentence (e.g. a list item) will be joined to the next paragraph too.
* Heading detection is conservative ("Chapter 3", "PROLOGUE", short ALL-CAPS lines); books with
  other heading styles show no contents list.
* The intra-paragraph position is a fraction of the paragraph, so restores after a typography
  change can be off by roughly a line.
* Widget periodic refresh is ≥30 min and may be delayed by Android; a missed day can take that
  long to appear on the widget if Cloven isn't opened.
* Flying east across the date line can skip a calendar date and break a streak.
* Uninstalling deletes the library and history (cloud backup intentionally off).
* The release APK is signed with the local debug key (personal sideloading only).

## Verification log (2026-10-22)
1. `./gradlew assembleDebug` — first compile of all sources succeeded.
2. `./gradlew testDebugUnitTest` — **79 / 79 passed** (final run, after all code changes).
3. `./gradlew connectedDebugAndroidTest` on the API 35 emulator — **22 / 22 passed** (Room 4,
   PDF import 12, preferences/streak persistence 2, widget rendering 4). Run before the final
   UI-only change (clover tint); that change was verified visually.
4. Manual emulator session: import via system picker, read, restyle, leave/reopen, bookmark,
   switch to PDF, activity screen, widget placement/refresh/reboot, dark theme, release APK smoke test.
5. Issues found and fixed during verification: reader controls hidden on open; text under status bar;
   low-contrast nav label; clipped card text; empty-state button visible during import; page-boundary
   split before capitalised words; clover too dark in forced dark theme; Glance widget stuck on a
   loading spinner after reboot (replaced with RemoteViews).
6. Observed once, not reproduced: the library's Continue Reading card showed 0 % after the very first
   reading session although the position had been saved. A clean first-open retest showed the correct
   value immediately. Left documented, not claimed fixed.
