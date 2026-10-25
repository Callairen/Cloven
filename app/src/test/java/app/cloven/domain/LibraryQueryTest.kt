package app.cloven.domain

import app.cloven.data.db.BookEntity
import app.cloven.data.db.BookWithProgress
import app.cloven.data.db.ExtractionStatus
import app.cloven.data.db.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryQueryTest {

    private fun book(id: String, title: String, author: String?, status: ReadingStatus = ReadingStatus.NOT_STARTED, favorite: Boolean = false, opened: Long? = null) =
        BookWithProgress(
            BookEntity(
                id = id, title = title, author = author, description = null, fileName = "$id.pdf", coverFileName = null,
                fileSizeBytes = 1, pageCount = 1, importedAt = 0, lastOpenedAt = opened, status = status, isFavorite = favorite,
                extractionStatus = ExtractionStatus.READY, totalChars = 1, blockCount = 1, processingError = null,
            ),
            progressPercent = null,
        )

    private val books = listOf(
        book("1", "Little Women", "Louisa May Alcott", ReadingStatus.READING, opened = 10),
        book("2", "Émile", "Jean-Jacques Rousseau", ReadingStatus.FINISHED, favorite = true, opened = 30),
        book("3", "The Secret Garden", "Frances Hodgson Burnett", opened = null),
        book("4", "Middlemarch", null, ReadingStatus.READING, favorite = true, opened = 20),
    )

    private fun ids(list: List<BookWithProgress>) = list.map { it.book.id }

    @Test fun `empty query returns everything`() = assertEquals(listOf("1", "2", "3", "4"), ids(LibraryQuery.apply(books, "  ", LibraryFilter.ALL)))

    @Test fun `search is case insensitive and partial`() = assertEquals(listOf("3"), ids(LibraryQuery.apply(books, "sECRet gar", LibraryFilter.ALL)))

    @Test fun `search matches authors`() = assertEquals(listOf("1"), ids(LibraryQuery.apply(books, "alcott", LibraryFilter.ALL)))

    @Test fun `search ignores accents`() = assertEquals(listOf("2"), ids(LibraryQuery.apply(books, "emile", LibraryFilter.ALL)))

    @Test fun `books without author are searchable by title`() = assertEquals(listOf("4"), ids(LibraryQuery.apply(books, "middle", LibraryFilter.ALL)))

    @Test fun `no match returns empty`() = assertEquals(emptyList<String>(), ids(LibraryQuery.apply(books, "zzz", LibraryFilter.ALL)))

    @Test fun `filters`() {
        assertEquals(listOf("1", "4"), ids(LibraryQuery.apply(books, "", LibraryFilter.READING)))
        assertEquals(listOf("2"), ids(LibraryQuery.apply(books, "", LibraryFilter.FINISHED)))
        assertEquals(listOf("2", "4"), ids(LibraryQuery.apply(books, "", LibraryFilter.FAVORITES)))
        assertEquals(listOf("4"), ids(LibraryQuery.apply(books, "march", LibraryFilter.FAVORITES)))
    }

    @Test fun `continue reading is the most recently opened unfinished book`() {
        assertEquals("4", LibraryQuery.continueReading(books)?.book?.id)
        assertNull(LibraryQuery.continueReading(books.filter { it.book.lastOpenedAt == null }))
    }
}
