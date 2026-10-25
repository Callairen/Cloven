package app.cloven.data.repo

import android.content.ContentResolver
import android.net.Uri
import app.cloven.data.db.BookEntity
import app.cloven.data.db.BookWithProgress
import app.cloven.data.db.ClovenDatabase
import app.cloven.data.db.ReadingStatus
import app.cloven.data.files.BookStorage
import app.cloven.domain.BookStatusRules
import app.cloven.domain.ClovenClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

data class StorageInfo(val bookCount: Int, val bytes: Long)

class LibraryRepository(
    private val database: ClovenDatabase,
    private val storage: BookStorage,
    private val contentResolver: ContentResolver,
    private val clock: ClovenClock,
) {
    private val books = database.bookDao()

    fun observeLibrary(): Flow<List<BookWithProgress>> = books.observeLibrary()

    fun observeBook(id: String): Flow<BookWithProgress?> = books.observeBook(id)

    suspend fun getBook(id: String): BookEntity? = books.getBook(id)

    fun bookFile(book: BookEntity): File = storage.bookFile(book.fileName)

    fun coverFile(book: BookEntity): File? = book.coverFileName?.let(storage::coverFile)

    suspend fun setFavorite(id: String, favorite: Boolean) = books.setFavorite(id, favorite)

    suspend fun setStatus(id: String, status: ReadingStatus) = books.setStatus(id, status)

    suspend fun markOpened(book: BookEntity) =
        books.markOpened(book.id, clock.wallMillis(), BookStatusRules.onOpened(book.status))

    /**
     * Removes the book row (cascading to blocks, progress and bookmarks), then its private
     * files. If file deletion fails the startup orphan sweep removes them later.
     */
    suspend fun deleteBook(id: String): Boolean = withContext(Dispatchers.IO) {
        val book = books.getBook(id) ?: return@withContext false
        val deleted = books.delete(id) > 0
        if (deleted) storage.deleteBookFiles(book.fileName, book.coverFileName)
        deleted
    }

    /** Copies the stored PDF to a user-chosen document (Storage Access Framework). */
    suspend fun exportCopy(id: String, destination: Uri): Boolean = withContext(Dispatchers.IO) {
        val book = books.getBook(id) ?: return@withContext false
        runCatching {
            contentResolver.openOutputStream(destination)?.use { out ->
                storage.bookFile(book.fileName).inputStream().use { it.copyTo(out) }
            } ?: error("no output stream")
        }.isSuccess
    }

    suspend fun sweepOrphans(): Int = withContext(Dispatchers.IO) {
        storage.sweepOrphans(
            knownBooks = books.allFileNames().toSet(),
            knownCovers = books.allCoverFileNames().toSet(),
            now = clock.wallMillis(),
        )
    }

    suspend fun storageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        StorageInfo(books.count(), storage.librarySizeBytes())
    }
}
