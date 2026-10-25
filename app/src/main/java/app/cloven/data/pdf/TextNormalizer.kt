package app.cloven.data.pdf

import app.cloven.data.db.BlockKind

/**
 * Turns raw per-page extracted text into ordered reading blocks.
 *
 * The normalizer never reorders text and only drops lines that are recognisably page
 * numbers at the very top/bottom of a page; everything else is kept. It handles:
 * - joining wrapped lines into paragraphs and removing end-of-line hyphenation,
 * - paragraph breaks from blank lines, PdfBox paragraph markers, or short sentence-final lines,
 * - paragraphs that continue across a page boundary,
 * - conservative heading detection ("Chapter 3", "PROLOGUE", short ALL-CAPS lines),
 * - splitting very long paragraphs so individual blocks stay small.
 */
object TextNormalizer {

    /** Marker emitted by [PdfTextExtractor] where PdfBox detects a paragraph end. */
    const val PARAGRAPH_MARK = ' '
    const val MAX_BLOCK_CHARS = 2500

    data class Block(val pageIndex: Int, val kind: BlockKind, val text: String)

    fun buildBlocks(pages: List<String>): List<Block> {
        val out = ArrayList<Block>()
        pages.forEachIndexed { pageIndex, raw ->
            paragraphsOf(raw).forEachIndexed { i, paragraph ->
                val kind = if (isHeading(paragraph)) BlockKind.HEADING else BlockKind.PARAGRAPH
                val previous = out.lastOrNull()
                if (i == 0 && kind == BlockKind.PARAGRAPH && previous != null &&
                    previous.kind == BlockKind.PARAGRAPH && previous.pageIndex < pageIndex &&
                    continuesAcrossPage(previous.text, paragraph)
                ) {
                    out[out.lastIndex] = previous.copy(text = joinLines(previous.text, paragraph))
                } else {
                    out += Block(pageIndex, kind, paragraph)
                }
            }
        }
        return out.flatMap(::splitLong)
    }

    fun paragraphsOf(raw: String): List<String> {
        val text = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u000C', '\n')
            .replace(PARAGRAPH_MARK.toString(), "\n\n")
        val lines = stripPageNumbers(text.split('\n').map { it.replace('\t', ' ').trimEnd() })
        val typical = typicalLineLength(lines.filter { it.isNotBlank() })

        val paragraphs = ArrayList<String>()
        var current = ""
        fun flush() {
            val p = collapseWhitespace(current)
            if (p.isNotEmpty()) paragraphs += p
            current = ""
        }
        for (rawLine in lines) {
            if (rawLine.isBlank()) {
                flush()
                continue
            }
            val line = rawLine.trim()
            if (isHeading(line)) {
                flush()
                current = line
                flush()
                continue
            }
            current = if (current.isEmpty()) line else joinLines(current, line)
            if (typical >= 30 && line.length < typical * 0.7 && endsSentence(line)) flush()
        }
        flush()
        return paragraphs
    }

    fun joinLines(first: String, second: String): String {
        val a = first.trimEnd()
        val b = second.trimStart()
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val startsLower = b.first().isLowerCase()
        return when {
            a.endsWith('­') -> a.dropLast(1) + b
            a.endsWith('-') && a.length > 1 && a[a.length - 2].isLetter() && startsLower -> a.dropLast(1) + b
            else -> "$a $b"
        }
    }

    fun isHeading(text: String): Boolean {
        val t = text.trim()
        if (t.length !in 2..80) return false
        if (t.last() == ',' || t.last() == ';' || t.last() == '-') return false
        if (NUMBERED_HEADING.matches(t) || SINGLE_WORD_HEADING.matches(t)) return true
        val words = t.split(' ').filter { it.isNotBlank() }
        val letters = t.filter { it.isLetter() }
        return letters.length >= 3 && words.size <= 8 && letters.all { it.isUpperCase() } && !t.endsWith(".")
    }

    private fun continuesAcrossPage(previous: String, next: String): Boolean {
        val last = previous.trimEnd().lastOrNull() ?: return false
        val first = next.trimStart().firstOrNull() ?: return false
        if (last == '-' || last == '­') return true
        // A page that ends mid-sentence continues on the next page, even when the next word
        // is capitalised (a name, "I", ...). Headings never reach here.
        return last !in SENTENCE_END && first.isLetterOrDigit()
    }

    private fun endsSentence(line: String): Boolean = line.trimEnd().lastOrNull()?.let { it in SENTENCE_END } ?: false

    private fun stripPageNumbers(lines: List<String>): List<String> {
        val result = lines.toMutableList()
        val first = result.indexOfFirst { it.isNotBlank() }
        val last = result.indexOfLast { it.isNotBlank() }
        if (last >= 0 && (PAGE_NUMBER.matches(result[last].trim()) || ROMAN_PAGE.matches(result[last].trim()))) {
            result[last] = ""
        }
        if (first >= 0 && first != last && PAGE_NUMBER.matches(result[first].trim())) {
            result[first] = ""
        }
        return result
    }

    private fun typicalLineLength(lines: List<String>): Int {
        if (lines.isEmpty()) return 0
        val sorted = lines.map { it.trim().length }.sorted()
        return sorted[sorted.size / 2]
    }

    private fun collapseWhitespace(text: String): String = WHITESPACE.replace(text, " ").trim()

    private fun splitLong(block: Block): List<Block> {
        if (block.text.length <= MAX_BLOCK_CHARS) return listOf(block)
        val parts = ArrayList<Block>()
        var rest = block.text
        while (rest.length > MAX_BLOCK_CHARS) {
            val window = rest.substring(0, MAX_BLOCK_CHARS)
            var cut = maxOf(window.lastIndexOf(". "), window.lastIndexOf("? "), window.lastIndexOf("! "))
            cut = if (cut > MAX_BLOCK_CHARS / 2) cut + 1 else window.lastIndexOf(' ').takeIf { it > 0 } ?: MAX_BLOCK_CHARS
            parts += block.copy(text = rest.substring(0, cut).trim())
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) parts += block.copy(text = rest)
        return parts
    }

    private val SENTENCE_END = setOf('.', '!', '?', ':', '"', '”', '’', ')', '…')
    private val WHITESPACE = Regex("\\s+")
    private val PAGE_NUMBER = Regex("""(?i)(page\s+)?\d{1,4}(\s*(of|/)\s*\d{1,4})?""")
    private val ROMAN_PAGE = Regex("""(?i)[ivxlcdm]{1,7}""")
    private val NUMBERED_HEADING = Regex(
        """(?i)(chapter|part|book|section)\s+(\d{1,3}|[ivxlcdm]{1,7}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty)\b[.:]?(\s+\S.{0,60})?""",
    )
    private val SINGLE_WORD_HEADING = Regex(
        """(?i)(prologue|epilogue|preface|introduction|foreword|afterword|acknowledg(e)?ments|contents|table of contents|appendix(\s+[a-z0-9]{1,3})?|conclusion|glossary|bibliography|notes|index)[.:]?""",
    )
}

/** Picks a readable title from PDF metadata, falling back to the file name. */
object TitleCleaner {
    private val FILE_LIKE = Regex("""(?i)^(.+?)\.(pdf|docx?|odt|rtf|indd|tex|dvi|qxd|pages|epub)$""")
    private val APP_PREFIX = Regex("""(?i)^(microsoft (word|powerpoint) - |untitled - )""")
    private val JUNK = setOf("untitled", "untitled document", "document", "title", "unknown", "none")

    fun clean(metadataTitle: String?, displayName: String?): String {
        val fromMeta = metadataTitle?.let(::tidy)?.takeIf { it.isNotEmpty() && it.lowercase() !in JUNK }
        if (fromMeta != null) return fromMeta
        val fromFile = displayName?.let(::tidy)?.replace('_', ' ')?.let { Regex("\\s+").replace(it, " ").trim() }
        return fromFile?.takeIf { it.isNotEmpty() } ?: "Untitled book"
    }

    fun cleanAuthor(author: String?): String? = author?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() !in JUNK }

    private fun tidy(value: String): String {
        var v = value.trim().replace(APP_PREFIX, "")
        FILE_LIKE.matchEntire(v)?.let { v = it.groupValues[1] }
        return v.trim()
    }
}
