package app.cloven.data.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Original-layout page rendering with the framework [PdfRenderer] (pdfium).
 *
 * PdfRenderer allows one open page at a time and is not thread-safe, so every call is
 * serialized with a mutex. [close] is safe to call while a render is in flight: the
 * renderer is released as soon as the current page finishes.
 */
class PdfPageRenderer private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    private val mutex = Mutex()
    @Volatile private var closed = false
    private var released = false

    val pageCount: Int = renderer.pageCount

    /** Page sizes in PDF points, in page order. */
    suspend fun pageSizes(): List<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            (0 until pageCount).map { index ->
                if (closed) return@withLock emptyList()
                renderer.openPage(index).use { it.width to it.height }
            }
        }.also { releaseIfClosed() }
    }

    /** Renders [index] at [targetWidth] pixels wide on a white background. */
    suspend fun render(index: Int, targetWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (closed || index !in 0 until pageCount) return@withLock null
                renderer.openPage(index).use { page ->
                    val width = targetWidth.coerceIn(64, MAX_RENDER_WIDTH)
                    val height = max(1, (page.height.toFloat() * width / page.width).roundToInt())
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            } finally {
                releaseLocked()
            }
        }
    }

    override fun close() {
        closed = true
        if (mutex.tryLock()) {
            try {
                releaseLocked()
            } finally {
                mutex.unlock()
            }
        }
    }

    private suspend fun releaseIfClosed() = mutex.withLock { releaseLocked() }

    /** Must hold [mutex]. */
    private fun releaseLocked() {
        if (closed && !released) {
            released = true
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
        }
    }

    companion object {
        const val MAX_RENDER_WIDTH = 2200

        /**
         * Opens [file]. Throws [SecurityException] for password-protected documents and
         * [java.io.IOException] for corrupt or unsupported files.
         */
        fun open(file: File): PdfPageRenderer {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return try {
                PdfPageRenderer(descriptor, PdfRenderer(descriptor))
            } catch (t: Throwable) {
                descriptor.close()
                throw t
            }
        }
    }
}
