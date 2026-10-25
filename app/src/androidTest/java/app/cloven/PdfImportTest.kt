package app.cloven

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cloven.data.db.ClovenDatabase
import app.cloven.data.db.ExtractionStatus
import app.cloven.data.files.BookStorage
import app.cloven.data.pdf.ImportException
import app.cloven.data.pdf.PdfImporter
import app.cloven.data.pdf.PdfPageRenderer
import app.cloven.data.pdf.PdfTextExtractor
import app.cloven.data.repo.LibraryRepository
import app.cloven.data.repo.ReadingRepository
import app.cloven.domain.SystemClovenClock
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises the real import pipeline (copy → validate → framework renderer probe → PdfBox
 * extraction → normalization → Room) against PDFs generated on the device.
 */
@RunWith(AndroidJUnit4::class)
class PdfImportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var root: File
    private lateinit var fixtures: File
    private lateinit var db: ClovenDatabase
    private lateinit var storage: BookStorage
    private lateinit var importer: PdfImporter

    @Before fun setUp() {
        root = File(context.cacheDir, "import-test-root").apply { deleteRecursively(); mkdirs() }
        fixtures = File(context.cacheDir, "import-test-fixtures").apply { deleteRecursively(); mkdirs() }
        db = ClovenDatabase.inMemory(context)
        storage = BookStorage(root)
        importer = PdfImporter(context.contentResolver, storage, db.bookDao(), PdfTextExtractor(File(root, "scratch")), SystemClovenClock)
    }

    @After fun tearDown() {
        db.close()
        root.deleteRecursively()
        fixtures.deleteRecursively()
    }

    // ---- fixtures ---------------------------------------------------------------------------

    private fun textPdf(name: String, pages: List<List<String>>): File {
        val doc = PdfDocument()
        val paint = Paint().apply { color = Color.BLACK; textSize = 12f; isAntiAlias = true }
        pages.forEachIndexed { i, lines ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(420, 595, i + 1).create())
            lines.forEachIndexed { l, line -> page.canvas.drawText(line, 36f, 60f + l * 16f, paint) }
            doc.finishPage(page)
        }
        val file = File(fixtures, name)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun imagePdf(name: String, pageCount: Int): File {
        val doc = PdfDocument()
        val paint = Paint().apply { color = Color.rgb(88, 129, 87) }
        repeat(pageCount) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(420, 595, i + 1).create())
            page.canvas.drawCircle(210f, 300f, 120f, paint)
            doc.finishPage(page)
        }
        val file = File(fixtures, name)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private val storyLines = listOf(
        "Chapter 1",
        "The library was quiet in the early",
        "morning, and the reader settled in",
        "with a cup of tea and a long book.",
    )

    private fun import(file: File) = runBlocking { importer.import(Uri.fromFile(file)) {} }

    private fun expectFailure(file: File): String = try {
        import(file)
        fail("import should have failed")
        ""
    } catch (e: ImportException) {
        e.userMessage
    }

    private fun assertNothingStored() = runBlocking {
        assertEquals(0, db.bookDao().count())
        assertTrue(storage.booksDir.listFiles().isNullOrEmpty())
        assertTrue(storage.coversDir.listFiles().isNullOrEmpty())
        assertTrue(storage.tempDir.listFiles().isNullOrEmpty())
    }

    // ---- tests ------------------------------------------------------------------------------

    @Test fun normalTextPdfIsImportedWithBlocksAndCover() = runBlocking {
        val book = import(textPdf("normal.pdf", listOf(storyLines, storyLines.drop(1))))
        assertEquals(ExtractionStatus.READY, book.extractionStatus)
        assertEquals(2, book.pageCount)
        assertTrue(storage.bookFile(book.fileName).exists())
        assertNotNull(book.coverFileName)
        assertTrue(storage.coverFile(book.coverFileName!!).exists())

        val document = ReadingRepository(db, SystemClovenClock).loadDocument(book.id)
        val text = document.blocks.joinToString(" ") { it.text }
        assertTrue(text, text.contains("The library was quiet in the early morning"))
        assertEquals(book.totalChars, document.totalChars)
        assertEquals("Chapter 1", document.headings.firstOrNull()?.second)
        // Page references are preserved.
        assertEquals(1, document.blocks.last().pageIndex)
    }

    @Test fun missingMetadataFallsBackToAReadableTitle() {
        val book = import(textPdf("no-meta.pdf", listOf(storyLines)))
        assertTrue(book.title.isNotBlank())
        assertNull(book.author)
    }

    @Test fun longDocumentIsFullyExtracted() {
        val pages = (1..150).map { p -> (1..30).map { l -> "Page $p line $l of a long book with words." } }
        val book = import(textPdf("long.pdf", pages))
        assertEquals(ExtractionStatus.READY, book.extractionStatus)
        assertEquals(150, book.pageCount)
        val text = runBlocking { ReadingRepository(db, SystemClovenClock).loadDocument(book.id) }.blocks.joinToString(" ") { it.text }
        assertTrue(text.contains("Page 1 line 1"))
        assertTrue(text.contains("Page 150 line 30"))
    }

    @Test fun pdfWithoutTextIsKeptForOriginalModeOnly() = runBlocking {
        val book = import(imagePdf("scanned.pdf", 3))
        assertEquals(ExtractionStatus.NO_TEXT, book.extractionStatus)
        assertNotNull(book.processingError)
        assertEquals(0, db.bookDao().countBlocks(book.id))
        assertTrue(storage.bookFile(book.fileName).exists())
        // The original is still renderable.
        PdfPageRenderer.open(storage.bookFile(book.fileName)).use { assertNotNull(it.render(0, 200)) }
    }

    @Test fun emptyFileIsRejectedWithoutTraces() {
        val file = File(fixtures, "empty.pdf").apply { writeBytes(ByteArray(0)) }
        assertTrue(expectFailure(file).contains("empty"))
        assertNothingStored()
    }

    @Test fun nonPdfIsRejected() {
        val file = File(fixtures, "notes.pdf").apply { writeText("just some notes, not a pdf") }
        assertTrue(expectFailure(file).contains("isn't a PDF"))
        assertNothingStored()
    }

    @Test fun corruptedPdfIsRejected() {
        val file = File(fixtures, "broken.pdf").apply { writeBytes("%PDF-1.4\n".toByteArray() + ByteArray(4096) { (it * 31).toByte() }) }
        expectFailure(file)
        assertNothingStored()
    }

    @Test fun truncatedPdfIsRejectedOrImportedConsistently() = runBlocking {
        val good = textPdf("whole.pdf", listOf(storyLines, storyLines))
        val truncated = File(fixtures, "truncated.pdf").apply { writeBytes(good.readBytes().copyOf((good.length() * 0.6).toInt())) }
        try {
            val book = import(truncated)
            // If pdfium can repair it, the book must be complete and consistent.
            assertTrue(storage.bookFile(book.fileName).exists())
            assertEquals(1, db.bookDao().count())
        } catch (e: ImportException) {
            assertNothingStored()
        }
    }

    @Test fun passwordProtectedPdfIsRejected() {
        val plain = textPdf("plain.pdf", listOf(storyLines))
        val encrypted = File(fixtures, "locked.pdf")
        PDDocument.load(plain).use { doc ->
            doc.protect(StandardProtectionPolicy("owner-secret", "user-secret", AccessPermission()).apply { encryptionKeyLength = 128 })
            doc.save(encrypted)
        }
        assertTrue(expectFailure(encrypted).contains("password"))
        assertNothingStored()
    }

    @Test fun cancelledImportLeavesNothingBehind() = runBlocking {
        val pages = (1..400).map { p -> (1..30).map { l -> "Page $p line $l of a book that takes a while." } }
        val file = textPdf("cancel.pdf", pages)
        val reading = CompletableDeferred<Unit>()
        val job = async {
            importer.import(Uri.fromFile(file)) { progress ->
                if (progress.stage == app.cloven.data.pdf.ImportProgress.Stage.READING_TEXT) reading.complete(Unit)
            }
        }
        reading.await()
        job.cancelAndJoin()
        assertNothingStored()
    }

    @Test fun deletingABookRemovesItsFilesOnly() = runBlocking {
        val a = import(textPdf("a.pdf", listOf(storyLines)))
        val b = import(textPdf("b.pdf", listOf(storyLines)))
        val library = LibraryRepository(db, storage, context.contentResolver, SystemClovenClock)
        assertTrue(library.deleteBook(a.id))
        assertFalse(storage.bookFile(a.fileName).exists())
        assertFalse(storage.coverFile(a.coverFileName!!).exists())
        assertTrue(storage.bookFile(b.fileName).exists())
        assertEquals(1, db.bookDao().count())
    }

    @Test fun orphanSweepRemovesUnreferencedFiles() = runBlocking {
        val kept = import(textPdf("kept.pdf", listOf(storyLines)))
        storage.booksDir.mkdirs()
        val orphan = File(storage.booksDir, "orphan.pdf").apply { writeText("x"); setLastModified(0) }
        val library = LibraryRepository(db, storage, context.contentResolver, SystemClovenClock)
        library.sweepOrphans()
        assertFalse(orphan.exists())
        assertTrue(storage.bookFile(kept.fileName).exists())
    }
}
