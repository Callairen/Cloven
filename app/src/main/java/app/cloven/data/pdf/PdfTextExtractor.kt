package app.cloven.data.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local text extraction with PdfBox-Android, one page at a time.
 *
 * Text is taken in content-stream order (no position sorting), which preserves reading
 * order for typical e-books and single-column layouts. PdfBox's paragraph detection is
 * surfaced via [TextNormalizer.PARAGRAPH_MARK]. The document is opened in temp-file memory
 * mode so large books do not need to fit in the Java heap.
 */
class PdfTextExtractor(private val scratchDir: File) {

    data class Metadata(val title: String?, val author: String?, val subject: String?)

    class Result(
        val metadata: Metadata,
        /** Raw text per page; failed pages are empty strings. */
        val pages: List<String>,
        val failedPages: Int,
    )

    /** Thrown when the PDF needs a user password. */
    class PasswordRequiredException : Exception("password required")

    suspend fun extract(file: File, onPage: (done: Int, total: Int) -> Unit): Result = withContext(Dispatchers.IO) {
        scratchDir.mkdirs()
        val document = try {
            PDDocument.load(file, "", MemoryUsageSetting.setupTempFileOnly().setTempDir(scratchDir))
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw PasswordRequiredException()
        }
        document.use { doc ->
            val info = doc.documentInformation
            val metadata = Metadata(info?.title, info?.author, info?.subject)
            val stripper = PDFTextStripper().apply {
                sortByPosition = false
                paragraphStart = ""
                paragraphEnd = TextNormalizer.PARAGRAPH_MARK.toString()
                lineSeparator = "\n"
                pageEnd = ""
            }
            val total = doc.numberOfPages
            val pages = ArrayList<String>(total)
            var failed = 0
            for (page in 1..total) {
                ensureActive()
                stripper.startPage = page
                stripper.endPage = page
                pages += try {
                    stripper.getText(doc)
                } catch (e: Exception) {
                    // One unreadable page must not lose the rest of the book; it is reported.
                    failed++
                    ""
                }
                onPage(page, total)
            }
            Result(metadata, pages, failed)
        }
    }
}
