package app.cloven.domain

import app.cloven.data.db.ReadingMode
import app.cloven.data.db.ReadingStatus

/** Automatic reading-status transitions. Manual changes from the details sheet always win. */
object BookStatusRules {
    const val FINISHED_PERCENT = 99.5f

    fun onOpened(current: ReadingStatus): ReadingStatus =
        if (current == ReadingStatus.NOT_STARTED) ReadingStatus.READING else current

    /** Reaching the end marks a book finished; re-reading a finished book keeps it finished. */
    fun onProgress(current: ReadingStatus, percent: Float): ReadingStatus = when {
        percent >= FINISHED_PERCENT -> ReadingStatus.FINISHED
        current == ReadingStatus.NOT_STARTED -> ReadingStatus.READING
        else -> current
    }
}

/** Guards against stored positions that no longer fit the document (e.g. regenerated text). */
object PositionValidator {
    fun clampTextOffset(offset: Long?, totalChars: Long): Long? =
        if (offset == null || totalChars <= 0L) null else offset.coerceIn(0L, totalChars)

    fun clampPage(page: Int?, pageCount: Int): Int? =
        if (page == null || pageCount <= 0) null else page.coerceIn(0, pageCount - 1)

    fun isValidBookmark(mode: ReadingMode, textOffset: Long?, pdfPage: Int?, totalChars: Long, pageCount: Int): Boolean =
        when (mode) {
            ReadingMode.TEXT -> textOffset != null && totalChars > 0 && textOffset in 0..totalChars
            ReadingMode.PDF -> pdfPage != null && pageCount > 0 && pdfPage in 0 until pageCount
        }
}
