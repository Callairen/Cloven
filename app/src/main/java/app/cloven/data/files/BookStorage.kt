package app.cloven.data.files

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Application-private file layout. Only file names are persisted; directories are resolved
 * from [root] (Context.filesDir) at runtime.
 */
class BookStorage(private val root: File) {

    val booksDir: File get() = File(root, "books")
    val coversDir: File get() = File(root, "covers")
    val tempDir: File get() = File(root, "import-tmp")

    fun bookFile(fileName: String): File = File(booksDir, fileName)
    fun coverFile(fileName: String): File = File(coversDir, fileName)

    fun newTempFile(): File {
        tempDir.mkdirs()
        return File(tempDir, "${UUID.randomUUID()}.pdf")
    }

    /** Moves a validated temp file to its permanent name. Returns the stored file name. */
    fun commitBook(temp: File, bookId: String): String {
        booksDir.mkdirs()
        val name = "$bookId.pdf"
        val destination = File(booksDir, name)
        if (!temp.renameTo(destination)) {
            temp.copyTo(destination, overwrite = true)
            temp.delete()
        }
        return name
    }

    fun writeCover(bookId: String, bitmap: Bitmap): String? {
        coversDir.mkdirs()
        val name = "$bookId.jpg"
        val file = File(coversDir, name)
        return runCatching {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            name
        }.getOrElse {
            file.delete()
            null
        }
    }

    fun deleteBookFiles(fileName: String?, coverFileName: String?) {
        fileName?.let { bookFile(it).delete() }
        coverFileName?.let { coverFile(it).delete() }
    }

    /**
     * Deletes files that no book row references. Files younger than [minAgeMs] are kept so an
     * import that is still in progress is never touched.
     */
    fun sweepOrphans(knownBooks: Set<String>, knownCovers: Set<String>, now: Long, minAgeMs: Long = 60 * 60_000L): Int {
        var removed = 0
        fun sweep(dir: File, keep: Set<String>) {
            dir.listFiles()?.forEach { file ->
                if (file.name !in keep && now - file.lastModified() > minAgeMs && file.delete()) removed++
            }
        }
        sweep(booksDir, knownBooks)
        sweep(coversDir, knownCovers)
        sweep(tempDir, emptySet())
        return removed
    }

    fun librarySizeBytes(): Long =
        listOf(booksDir, coversDir).sumOf { dir -> dir.listFiles()?.sumOf { it.length() } ?: 0L }
}
