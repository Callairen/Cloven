package app.cloven.domain

import app.cloven.data.db.BookWithProgress
import app.cloven.data.db.ReadingStatus
import java.text.Normalizer

enum class LibraryFilter(val label: String) {
    ALL("All books"),
    READING("Reading"),
    FINISHED("Finished"),
    FAVORITES("Favorites"),
}

/** Local, case- and accent-insensitive search plus shelf filters. */
object LibraryQuery {

    fun apply(books: List<BookWithProgress>, query: String, filter: LibraryFilter): List<BookWithProgress> {
        val needle = fold(query)
        return books.filter { item ->
            val book = item.book
            val passesFilter = when (filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.READING -> book.status == ReadingStatus.READING
                LibraryFilter.FINISHED -> book.status == ReadingStatus.FINISHED
                LibraryFilter.FAVORITES -> book.isFavorite
            }
            passesFilter && (needle.isEmpty() || fold(book.title).contains(needle) || fold(book.author.orEmpty()).contains(needle))
        }
    }

    /** Most recently opened book that is not finished. */
    fun continueReading(books: List<BookWithProgress>): BookWithProgress? =
        books.filter { it.book.lastOpenedAt != null && it.book.status != ReadingStatus.FINISHED }
            .maxByOrNull { it.book.lastOpenedAt ?: 0L }

    private val MARKS = Regex("\\p{Mn}+")

    fun fold(text: String): String =
        MARKS.replace(Normalizer.normalize(text.trim(), Normalizer.Form.NFD), "").lowercase()
}
