package app.cloven.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ReadingStatus { NOT_STARTED, READING, FINISHED }

/** Whether the reflowable text representation is available. */
enum class ExtractionStatus { READY, NO_TEXT, FAILED }

enum class ReadingMode { TEXT, PDF }

enum class BlockKind { PARAGRAPH, HEADING }

@Entity(tableName = "books", indices = [Index("lastOpenedAt")])
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val description: String?,
    /** File name inside filesDir/books — never an absolute path. */
    val fileName: String,
    /** File name inside filesDir/covers, if a cover could be rendered. */
    val coverFileName: String?,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val importedAt: Long,
    val lastOpenedAt: Long?,
    val status: ReadingStatus,
    val isFavorite: Boolean,
    val extractionStatus: ExtractionStatus,
    val totalChars: Long,
    val blockCount: Int,
    /** User-readable explanation when extraction failed or was partial. */
    val processingError: String?,
)

@Entity(
    tableName = "text_blocks",
    primaryKeys = ["bookId", "blockIndex"],
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
)
data class TextBlockEntity(
    val bookId: String,
    val blockIndex: Int,
    /** Zero-based source page in the original PDF. */
    val pageIndex: Int,
    val kind: BlockKind,
    /** Offset of this block's first character in the book's normalized text. */
    val charStart: Long,
    val text: String,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
)
data class ReadingProgressEntity(
    @PrimaryKey val bookId: String,
    val textCharOffset: Long,
    val pdfPageIndex: Int,
    val lastMode: ReadingMode,
    /** Progress in the last-used mode; stored so the shelf can show it for PDF-only books too. */
    val percent: Float,
    val updatedAt: Long,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId")],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val mode: ReadingMode,
    val textCharOffset: Long?,
    val pdfPageIndex: Int?,
    /** Short excerpt or "Page n" label shown in the bookmark list. */
    val snippet: String,
    val note: String?,
    val createdAt: Long,
)

/**
 * One continuous stretch of active reading. Sessions are the single source of truth for
 * daily totals and streaks. bookId becomes NULL when the book is deleted so reading
 * history (and streaks) survive tidying the shelf.
 */
@Entity(
    tableName = "reading_sessions",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("bookId"), Index("localDate")],
)
data class ReadingSessionEntity(
    @PrimaryKey val id: String,
    val bookId: String?,
    val startedAt: Long,
    val endedAt: Long,
    val activeMs: Long,
    /** ISO-8601 local date (yyyy-MM-dd) in the zone where the reading happened. */
    val localDate: String,
    val zoneId: String,
)

data class BookWithProgress(
    @Embedded val book: BookEntity,
    val progressPercent: Float?,
)

data class DailyTotalRow(val localDate: String, val activeMs: Long)
