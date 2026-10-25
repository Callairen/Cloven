package app.cloven.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BookDao {
    @Query(
        """SELECT b.*, p.percent AS progressPercent FROM books b
           LEFT JOIN reading_progress p ON p.bookId = b.id
           ORDER BY b.importedAt DESC""",
    )
    abstract fun observeLibrary(): Flow<List<BookWithProgress>>

    @Query(
        """SELECT b.*, p.percent AS progressPercent FROM books b
           LEFT JOIN reading_progress p ON p.bookId = b.id WHERE b.id = :id""",
    )
    abstract fun observeBook(id: String): Flow<BookWithProgress?>

    @Query("SELECT * FROM books WHERE id = :id")
    abstract suspend fun getBook(id: String): BookEntity?

    @Insert
    abstract suspend fun insertBook(book: BookEntity)

    @Insert
    abstract suspend fun insertBlocks(blocks: List<TextBlockEntity>)

    /** Book row and all of its text blocks are written atomically. */
    @Transaction
    open suspend fun insertBookWithBlocks(book: BookEntity, blocks: List<TextBlockEntity>) {
        insertBook(book)
        blocks.chunked(500).forEach { insertBlocks(it) }
    }

    @Query("UPDATE books SET isFavorite = :favorite WHERE id = :id")
    abstract suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE books SET status = :status WHERE id = :id")
    abstract suspend fun setStatus(id: String, status: ReadingStatus)

    @Query("UPDATE books SET lastOpenedAt = :openedAt, status = :status WHERE id = :id")
    abstract suspend fun markOpened(id: String, openedAt: Long, status: ReadingStatus)

    @Query("DELETE FROM books WHERE id = :id")
    abstract suspend fun delete(id: String): Int

    @Query("SELECT * FROM text_blocks WHERE bookId = :bookId ORDER BY blockIndex")
    abstract suspend fun getBlocks(bookId: String): List<TextBlockEntity>

    @Query("SELECT COUNT(*) FROM text_blocks WHERE bookId = :bookId")
    abstract suspend fun countBlocks(bookId: String): Int

    @Query("SELECT fileName FROM books")
    abstract suspend fun allFileNames(): List<String>

    @Query("SELECT coverFileName FROM books WHERE coverFileName IS NOT NULL")
    abstract suspend fun allCoverFileNames(): List<String>

    @Query("SELECT COUNT(*) FROM books")
    abstract suspend fun count(): Int
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun get(bookId: String): ReadingProgressEntity?

    @Upsert
    suspend fun upsert(progress: ReadingProgressEntity)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeForBook(bookId: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE bookmarks SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?)

    @Query("SELECT COUNT(*) FROM bookmarks WHERE bookId = :bookId")
    suspend fun countForBook(bookId: String): Int
}

@Dao
interface SessionDao {
    @Upsert
    suspend fun upsert(session: ReadingSessionEntity)

    @Query("DELETE FROM reading_sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT localDate, SUM(activeMs) AS activeMs FROM reading_sessions GROUP BY localDate ORDER BY localDate")
    fun observeDailyTotals(): Flow<List<DailyTotalRow>>

    @Query("SELECT localDate, SUM(activeMs) AS activeMs FROM reading_sessions GROUP BY localDate ORDER BY localDate")
    suspend fun dailyTotals(): List<DailyTotalRow>

    @Query("SELECT COALESCE(SUM(activeMs), 0) FROM reading_sessions WHERE localDate = :localDate")
    suspend fun totalForDate(localDate: String): Long

    @Query("SELECT * FROM reading_sessions WHERE id = :id")
    suspend fun get(id: String): ReadingSessionEntity?

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt")
    suspend fun all(): List<ReadingSessionEntity>
}
