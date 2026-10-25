package app.cloven

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cloven.data.db.BlockKind
import app.cloven.data.db.BookEntity
import app.cloven.data.db.BookmarkEntity
import app.cloven.data.db.ClovenDatabase
import app.cloven.data.db.ExtractionStatus
import app.cloven.data.db.ReadingMode
import app.cloven.data.db.ReadingProgressEntity
import app.cloven.data.db.ReadingSessionEntity
import app.cloven.data.db.ReadingStatus
import app.cloven.data.db.TextBlockEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: ClovenDatabase

    @Before fun setUp() {
        db = ClovenDatabase.inMemory(ApplicationProvider.getApplicationContext())
    }

    @After fun tearDown() = db.close()

    private fun book(id: String) = BookEntity(
        id = id, title = "Book $id", author = null, description = null, fileName = "$id.pdf", coverFileName = null,
        fileSizeBytes = 10, pageCount = 3, importedAt = 1, lastOpenedAt = null, status = ReadingStatus.NOT_STARTED,
        isFavorite = false, extractionStatus = ExtractionStatus.READY, totalChars = 10, blockCount = 2, processingError = null,
    )

    private suspend fun seed(id: String) {
        db.bookDao().insertBookWithBlocks(
            book(id),
            listOf(
                TextBlockEntity(id, 0, 0, BlockKind.HEADING, 0, "Hello"),
                TextBlockEntity(id, 1, 1, BlockKind.PARAGRAPH, 5, "World"),
            ),
        )
        db.progressDao().upsert(ReadingProgressEntity(id, 5, 1, ReadingMode.TEXT, 50f, 2))
        db.bookmarkDao().insert(BookmarkEntity(bookId = id, mode = ReadingMode.TEXT, textCharOffset = 5, pdfPageIndex = 1, snippet = "World", note = null, createdAt = 3))
        db.sessionDao().upsert(ReadingSessionEntity("s-$id", id, 0, 1, 6 * 60_000L, "2026-03-10", "UTC"))
    }

    @Test fun deletingBookCascadesButKeepsReadingHistory() = runTest {
        seed("a")
        seed("b")
        db.bookDao().delete("a")

        assertNull(db.bookDao().getBook("a"))
        assertEquals(0, db.bookDao().countBlocks("a"))
        assertNull(db.progressDao().get("a"))
        assertEquals(0, db.bookmarkDao().countForBook("a"))
        val orphanedSession = db.sessionDao().get("s-a")!!
        assertNull(orphanedSession.bookId) // history retained, link cleared
        assertEquals(6 * 60_000L, orphanedSession.activeMs)

        // The unrelated book is untouched.
        assertEquals(2, db.bookDao().countBlocks("b"))
        assertEquals(1, db.bookmarkDao().countForBook("b"))
        assertEquals("b", db.progressDao().get("b")!!.bookId)
    }

    @Test fun dailyTotalsAggregateSessions() = runTest {
        seed("a")
        db.sessionDao().upsert(ReadingSessionEntity("x", "a", 0, 1, 60_000L, "2026-03-10", "UTC"))
        db.sessionDao().upsert(ReadingSessionEntity("y", null, 0, 1, 120_000L, "2026-03-11", "UTC"))
        // Upserting the same id replaces it rather than adding.
        db.sessionDao().upsert(ReadingSessionEntity("y", null, 0, 2, 180_000L, "2026-03-11", "UTC"))
        val totals = db.sessionDao().observeDailyTotals().first().associate { it.localDate to it.activeMs }
        assertEquals(mapOf("2026-03-10" to 7 * 60_000L, "2026-03-11" to 180_000L), totals)
    }

    @Test fun libraryJoinIncludesProgress() = runTest {
        seed("a")
        db.bookDao().insertBookWithBlocks(book("c"), emptyList())
        val library = db.bookDao().observeLibrary().first().associateBy { it.book.id }
        assertEquals(50f, library["a"]!!.progressPercent)
        assertNull(library["c"]!!.progressPercent)
    }

    @Test(expected = Exception::class)
    fun blocksForMissingBookAreRejected() = runTest {
        db.bookDao().insertBlocks(listOf(TextBlockEntity("ghost", 0, 0, BlockKind.PARAGRAPH, 0, "x")))
    }
}
