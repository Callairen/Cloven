package app.cloven.data.pdf

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import app.cloven.data.db.BookDao
import app.cloven.data.db.BookEntity
import app.cloven.data.db.ExtractionStatus
import app.cloven.data.db.ReadingStatus
import app.cloven.data.db.TextBlockEntity
import app.cloven.data.files.BookStorage
import app.cloven.domain.ClovenClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/** Import failures with a message suitable for showing to the user. */
class ImportException(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause)

data class ImportProgress(val stage: Stage, val done: Int = 0, val total: Int = 0) {
    enum class Stage { COPYING, CHECKING, READING_TEXT, SAVING }
}

/**
 * Validates, copies, extracts and stores one PDF.
 *
 * Guarantees: either a complete book row (with its blocks and private file) exists afterwards,
 * or nothing was written. A document whose text cannot be extracted is still imported for
 * original-PDF reading, with the reason recorded in [BookEntity.processingError].
 */
class PdfImporter(
    private val contentResolver: ContentResolver,
    private val storage: BookStorage,
    private val bookDao: BookDao,
    private val extractor: PdfTextExtractor,
    private val clock: ClovenClock,
) {

    suspend fun import(uri: Uri, onProgress: (ImportProgress) -> Unit): BookEntity = withContext(Dispatchers.IO) {
        val bookId = UUID.randomUUID().toString()
        val temp = storage.newTempFile()
        var committedFile: String? = null
        var coverName: String? = null
        try {
            onProgress(ImportProgress(ImportProgress.Stage.COPYING))
            val displayName = queryDisplayName(uri)
            copyToTemp(uri, temp)
            validateHeader(temp)

            onProgress(ImportProgress(ImportProgress.Stage.CHECKING))
            val probe = probe(temp)

            val extraction = try {
                extractor.extract(temp) { done, total ->
                    onProgress(ImportProgress(ImportProgress.Stage.READING_TEXT, done, total))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PdfTextExtractor.PasswordRequiredException) {
                throw ImportException("This PDF is password-protected. Cloven can't open protected PDFs yet.", e)
            } catch (e: Throwable) {
                // Extraction failure is not fatal: the original PDF is still readable.
                null
            }

            val blocks = extraction?.let { TextNormalizer.buildBlocks(it.pages) }.orEmpty()
            val totalChars = blocks.sumOf { it.text.length.toLong() }
            val (status, error) = classify(extraction, blocks.size, totalChars, probe.pageCount)

            onProgress(ImportProgress(ImportProgress.Stage.SAVING))
            ensureActive()
            committedFile = storage.commitBook(temp, bookId)
            coverName = probe.cover?.let { storage.writeCover(bookId, it) }
            probe.cover?.recycle()

            val book = BookEntity(
                id = bookId,
                title = TitleCleaner.clean(extraction?.metadata?.title, displayName),
                author = TitleCleaner.cleanAuthor(extraction?.metadata?.author),
                description = extraction?.metadata?.subject?.trim()?.takeIf { it.isNotEmpty() },
                fileName = committedFile,
                coverFileName = coverName,
                fileSizeBytes = storage.bookFile(committedFile).length(),
                pageCount = probe.pageCount,
                importedAt = clock.wallMillis(),
                lastOpenedAt = null,
                status = ReadingStatus.NOT_STARTED,
                isFavorite = false,
                extractionStatus = status,
                totalChars = if (status == ExtractionStatus.READY) totalChars else 0L,
                blockCount = if (status == ExtractionStatus.READY) blocks.size else 0,
                processingError = error,
            )
            val rows = if (status == ExtractionStatus.READY) toRows(bookId, blocks) else emptyList()
            bookDao.insertBookWithBlocks(book, rows)
            book
        } catch (t: Throwable) {
            temp.delete()
            storage.deleteBookFiles(committedFile, coverName)
            when (t) {
                is ImportException, is CancellationException -> throw t
                is OutOfMemoryError -> throw ImportException("This PDF is too large to process on this device.", t)
                else -> throw ImportException("Cloven couldn't import this file (${t.javaClass.simpleName}).", t)
            }
        }
    }

    private class Probe(val pageCount: Int, val cover: android.graphics.Bitmap?)

    private suspend fun probe(file: File): Probe {
        val renderer = try {
            PdfPageRenderer.open(file)
        } catch (e: SecurityException) {
            throw ImportException("This PDF is password-protected. Cloven can't open protected PDFs yet.", e)
        } catch (e: IOException) {
            throw ImportException("This file looks damaged or isn't a valid PDF.", e)
        }
        return renderer.use {
            if (it.pageCount <= 0) throw ImportException("This PDF has no pages.")
            Probe(it.pageCount, runCatching { it.render(0, COVER_WIDTH_PX) }.getOrNull())
        }
    }

    private fun classify(
        extraction: PdfTextExtractor.Result?,
        blockCount: Int,
        totalChars: Long,
        pageCount: Int,
    ): Pair<ExtractionStatus, String?> {
        if (extraction == null) {
            return ExtractionStatus.FAILED to "Text extraction failed. You can still read the original PDF."
        }
        if (extraction.failedPages == extraction.pages.size && extraction.pages.isNotEmpty()) {
            return ExtractionStatus.FAILED to "None of the pages could be read as text. You can still read the original PDF."
        }
        val textless = blockCount == 0 || totalChars < MIN_TEXT_CHARS ||
            (pageCount >= 5 && totalChars / pageCount < MIN_CHARS_PER_PAGE)
        if (textless) {
            return ExtractionStatus.NO_TEXT to
                "No selectable text was found (this may be a scanned book). Reflowable reading needs text; the original PDF is available."
        }
        val partial = extraction.failedPages.takeIf { it > 0 }?.let {
            "$it of ${extraction.pages.size} pages could not be read as text and are missing from reflowable mode."
        }
        return ExtractionStatus.READY to partial
    }

    private fun toRows(bookId: String, blocks: List<TextNormalizer.Block>): List<TextBlockEntity> {
        var offset = 0L
        return blocks.mapIndexed { index, block ->
            TextBlockEntity(bookId, index, block.pageIndex, block.kind, offset, block.text).also {
                offset += block.text.length
            }
        }
    }

    private suspend fun copyToTemp(uri: Uri, temp: File) {
        val input = try {
            contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            throw ImportException("Cloven couldn't open the selected file. It may have been moved or deleted.", e)
        } ?: throw ImportException("Cloven couldn't open the selected file.")
        try {
            input.use { source ->
                FileOutputStream(temp).use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            throw ImportException("Copying the file failed. Check that there is enough free storage.", e)
        }
        if (temp.length() == 0L) throw ImportException("The selected file is empty.")
    }

    private fun validateHeader(file: File) {
        val head = ByteArray(1024)
        val read = file.inputStream().use { it.read(head) }
        if (read <= 0 || !String(head, 0, read, Charsets.ISO_8859_1).contains("%PDF-")) {
            throw ImportException("The selected file isn't a PDF.")
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    companion object {
        private const val COVER_WIDTH_PX = 420
        private const val MIN_TEXT_CHARS = 16L
        private const val MIN_CHARS_PER_PAGE = 20L
    }
}
