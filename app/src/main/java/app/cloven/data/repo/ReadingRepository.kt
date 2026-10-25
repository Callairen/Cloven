package app.cloven.data.repo

import app.cloven.data.db.BlockKind
import app.cloven.data.db.BookmarkEntity
import app.cloven.data.db.ClovenDatabase
import app.cloven.data.db.ReadingMode
import app.cloven.data.db.ReadingProgressEntity
import app.cloven.data.db.ReadingStatus
import app.cloven.data.db.TextBlockEntity
import app.cloven.domain.BookStatusRules
import app.cloven.domain.ClovenClock
import app.cloven.domain.TextPositionIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** The reflowable representation of one book, loaded once per reader session. */
class ReaderDocument(val blocks: List<TextBlockEntity>) {
    val index = TextPositionIndex(
        LongArray(blocks.size) { blocks[it].charStart },
        IntArray(blocks.size) { blocks[it].text.length },
    )
    val totalChars: Long get() = index.totalChars

    /** (blockIndex, title) of detected headings, for the contents list. */
    val headings: List<Pair<Int, String>> =
        blocks.filter { it.kind == BlockKind.HEADING }.map { it.blockIndex to it.text }

    fun pageOf(offset: Long): Int = index.locate(offset)?.let { blocks[it.blockIndex].pageIndex } ?: 0

    /** Offset of the first block that starts on [page] or later (or the last block). */
    fun offsetForPage(page: Int): Long {
        val block = blocks.firstOrNull { it.pageIndex >= page } ?: blocks.lastOrNull() ?: return 0L
        return block.charStart
    }

    fun snippetAt(offset: Long, maxLength: Int = 90): String {
        val location = index.locate(offset) ?: return ""
        val text = blocks[location.blockIndex].text
        var start = (text.length * location.fraction).toInt().coerceIn(0, text.length)
        if (start > 0) {
            val space = text.lastIndexOf(' ', start).takeIf { it >= 0 } ?: 0
            start = if (space == 0) 0 else space + 1
        }
        val excerpt = text.substring(start).take(maxLength).trim()
        return if (text.length - start > maxLength) "$excerpt…" else excerpt
    }
}

class ReadingRepository(
    private val database: ClovenDatabase,
    private val clock: ClovenClock,
) {
    private val books = database.bookDao()
    private val progress = database.progressDao()
    private val bookmarks = database.bookmarkDao()

    suspend fun loadDocument(bookId: String): ReaderDocument = withContext(Dispatchers.IO) {
        ReaderDocument(books.getBlocks(bookId))
    }

    suspend fun getProgress(bookId: String): ReadingProgressEntity? = progress.get(bookId)

    /**
     * Saves both positions and applies automatic status transitions. [currentStatus] is
     * the status the caller last saw; reaching the end marks the book finished.
     */
    suspend fun saveProgress(
        bookId: String,
        textOffset: Long,
        pdfPage: Int,
        mode: ReadingMode,
        percent: Float,
        currentStatus: ReadingStatus,
    ): ReadingStatus {
        progress.upsert(
            ReadingProgressEntity(bookId, textOffset, pdfPage, mode, percent.coerceIn(0f, 100f), clock.wallMillis()),
        )
        val next = BookStatusRules.onProgress(currentStatus, percent)
        if (next != currentStatus) books.setStatus(bookId, next)
        return next
    }

    fun observeBookmarks(bookId: String): Flow<List<BookmarkEntity>> = bookmarks.observeForBook(bookId)

    suspend fun addBookmark(bookId: String, mode: ReadingMode, textOffset: Long?, pdfPage: Int?, snippet: String): Long =
        bookmarks.insert(
            BookmarkEntity(
                bookId = bookId,
                mode = mode,
                textCharOffset = textOffset,
                pdfPageIndex = pdfPage,
                snippet = snippet,
                note = null,
                createdAt = clock.wallMillis(),
            ),
        )

    suspend fun deleteBookmark(id: Long) = bookmarks.delete(id)

    suspend fun updateBookmarkNote(id: Long, note: String?) =
        bookmarks.updateNote(id, note?.trim()?.takeIf { it.isNotEmpty() })
}
