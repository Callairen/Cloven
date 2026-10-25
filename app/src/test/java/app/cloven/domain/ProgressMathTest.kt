package app.cloven.domain

import app.cloven.data.db.ReadingMode
import app.cloven.data.db.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressMathTest {

    // Blocks of length 100, 50, 0, 200 starting at 0, 100, 150, 150.
    private val index = TextPositionIndex(longArrayOf(0, 100, 150, 150), intArrayOf(100, 50, 0, 200))

    @Test fun `total chars is end of last block`() = assertEquals(350L, index.totalChars)

    @Test fun `locate start of document`() = assertEquals(TextPositionIndex.Location(0, 0f), index.locate(0))

    @Test fun `locate within block`() = assertEquals(TextPositionIndex.Location(0, 0.5f), index.locate(50))

    @Test fun `locate exact block boundary picks the later block`() =
        assertEquals(TextPositionIndex.Location(1, 0f), index.locate(100))

    @Test fun `empty block is skipped in favour of following block at same offset`() =
        assertEquals(3, index.locate(150)!!.blockIndex)

    @Test fun `offset beyond end is clamped to end`() = assertEquals(TextPositionIndex.Location(3, 1f), index.locate(10_000))

    @Test fun `negative offset is clamped to start`() = assertEquals(TextPositionIndex.Location(0, 0f), index.locate(-5))

    @Test fun `empty document has no location`() = assertNull(TextPositionIndex(LongArray(0), IntArray(0)).locate(10))

    @Test fun `offsetOf is the inverse of locate`() {
        for (offset in listOf(0L, 10L, 99L, 100L, 149L, 151L, 349L, 350L)) {
            val loc = index.locate(offset)!!
            // Float fractions may round by at most one character.
            assertEquals(offset.toDouble(), index.offsetOf(loc.blockIndex, loc.fraction).toDouble(), 1.0)
        }
    }

    @Test fun `offsetOf clamps out of range block`() {
        assertEquals(50L, index.offsetOf(-3, 0.5f)) // clamped to block 0, half way through
        assertEquals(0L, index.offsetOf(-3, -1f))
        assertEquals(350L, index.offsetOf(99, 1f))
    }

    @Test fun `position survives typography changes because it is text based`() {
        // Whatever the rendered heights are, the saved offset maps back to the same block/fraction.
        val saved = index.offsetOf(3, 0.25f)
        assertEquals(TextPositionIndex.Location(3, 0.25f), index.locate(saved))
    }

    @Test fun `text percent is clamped and safe for empty documents`() {
        assertEquals(0f, ProgressMath.textPercent(10, 0), 0f)
        assertEquals(50f, ProgressMath.textPercent(50, 100), 0.001f)
        assertEquals(100f, ProgressMath.textPercent(500, 100), 0f)
        assertEquals(0f, ProgressMath.textPercent(-5, 100), 0f)
    }

    @Test fun `pdf percent counts the visible page`() {
        assertEquals(0f, ProgressMath.pdfPercent(0, 0), 0f)
        assertEquals(100f, ProgressMath.pdfPercent(0, 1), 0f)
        assertEquals(25f, ProgressMath.pdfPercent(0, 4), 0.001f)
        assertEquals(100f, ProgressMath.pdfPercent(10, 4), 0f)
    }
}

class RulesTest {

    @Test fun `opening a new book marks it reading`() {
        assertEquals(ReadingStatus.READING, BookStatusRules.onOpened(ReadingStatus.NOT_STARTED))
        assertEquals(ReadingStatus.FINISHED, BookStatusRules.onOpened(ReadingStatus.FINISHED))
    }

    @Test fun `reaching the end finishes the book`() {
        assertEquals(ReadingStatus.FINISHED, BookStatusRules.onProgress(ReadingStatus.READING, 100f))
        assertEquals(ReadingStatus.FINISHED, BookStatusRules.onProgress(ReadingStatus.READING, 99.6f))
        assertEquals(ReadingStatus.READING, BookStatusRules.onProgress(ReadingStatus.READING, 60f))
    }

    @Test fun `rereading a finished book keeps it finished`() =
        assertEquals(ReadingStatus.FINISHED, BookStatusRules.onProgress(ReadingStatus.FINISHED, 3f))

    @Test fun `progress on a new book moves it to reading`() =
        assertEquals(ReadingStatus.READING, BookStatusRules.onProgress(ReadingStatus.NOT_STARTED, 1f))

    @Test fun `invalid saved positions are clamped or dropped`() {
        assertEquals(100L, PositionValidator.clampTextOffset(5_000, 100))
        assertEquals(0L, PositionValidator.clampTextOffset(-1, 100))
        assertNull(PositionValidator.clampTextOffset(10, 0))
        assertNull(PositionValidator.clampTextOffset(null, 100))
        assertEquals(9, PositionValidator.clampPage(40, 10))
        assertNull(PositionValidator.clampPage(3, 0))
    }

    @Test fun `bookmark validation`() {
        assertTrue(PositionValidator.isValidBookmark(ReadingMode.TEXT, 50, null, 100, 3))
        assertFalse(PositionValidator.isValidBookmark(ReadingMode.TEXT, 150, null, 100, 3))
        assertFalse(PositionValidator.isValidBookmark(ReadingMode.TEXT, null, 2, 100, 3))
        assertTrue(PositionValidator.isValidBookmark(ReadingMode.PDF, null, 2, 0, 3))
        assertFalse(PositionValidator.isValidBookmark(ReadingMode.PDF, null, 3, 0, 3))
    }
}
