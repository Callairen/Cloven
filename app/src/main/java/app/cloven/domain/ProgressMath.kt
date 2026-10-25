package app.cloven.domain

/**
 * Converts between the persisted logical text position (a character offset into the
 * normalized book text) and a (block, fraction-within-block) location used by the UI.
 *
 * [blockStarts] must be ascending; [blockLengths] has the same size.
 */
class TextPositionIndex(
    private val blockStarts: LongArray,
    private val blockLengths: IntArray,
) {
    init {
        require(blockStarts.size == blockLengths.size) { "starts and lengths differ in size" }
    }

    val blockCount: Int get() = blockStarts.size
    val totalChars: Long =
        if (blockStarts.isEmpty()) 0L else blockStarts.last() + blockLengths.last()

    data class Location(val blockIndex: Int, val fraction: Float)

    /** Locates [offset], clamping invalid values. Returns null for an empty document. */
    fun locate(offset: Long): Location? {
        if (blockStarts.isEmpty()) return null
        val clamped = offset.coerceIn(0L, totalChars)
        var low = 0
        var high = blockStarts.size - 1
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (blockStarts[mid] <= clamped) low = mid else high = mid - 1
        }
        val length = blockLengths[low]
        val fraction = if (length <= 0) 0f else ((clamped - blockStarts[low]).toFloat() / length).coerceIn(0f, 1f)
        return Location(low, fraction)
    }

    fun offsetOf(blockIndex: Int, fraction: Float): Long {
        if (blockStarts.isEmpty()) return 0L
        val index = blockIndex.coerceIn(0, blockStarts.size - 1)
        val within = (blockLengths[index] * fraction.coerceIn(0f, 1f)).toLong()
        return (blockStarts[index] + within).coerceIn(0L, totalChars)
    }

    fun startOf(blockIndex: Int): Long = offsetOf(blockIndex, 0f)
}

object ProgressMath {
    /** Percentage of [offset] within [totalChars], clamped to 0–100. Empty documents are 0%. */
    fun textPercent(offset: Long, totalChars: Long): Float {
        if (totalChars <= 0L) return 0f
        return (offset.toDouble() / totalChars * 100.0).toFloat().coerceIn(0f, 100f)
    }

    /** "Page n of N" progress: viewing the last page means 100%. */
    fun pdfPercent(pageIndex: Int, pageCount: Int): Float {
        if (pageCount <= 0) return 0f
        return ((pageIndex.coerceIn(0, pageCount - 1) + 1).toFloat() / pageCount * 100f).coerceIn(0f, 100f)
    }
}
