package app.cloven.data.pdf

import app.cloven.data.db.BlockKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    private val mark = TextNormalizer.PARAGRAPH_MARK

    @Test fun `wrapped lines join into one paragraph`() {
        val page = "The morning was quiet and the\nlibrary smelled of old paper and\ntea leaves left to steep.$mark"
        assertEquals(
            listOf("The morning was quiet and the library smelled of old paper and tea leaves left to steep."),
            TextNormalizer.paragraphsOf(page),
        )
    }

    @Test fun `end of line hyphenation is removed only before lowercase`() {
        assertEquals("example", TextNormalizer.joinLines("exam-", "ple"))
        assertEquals("well- Known", TextNormalizer.joinLines("well-", "Known"))
        assertEquals("A - b", TextNormalizer.joinLines("A -", "b"))
    }

    @Test fun `soft hyphen at line end joins words`() =
        assertEquals("together", TextNormalizer.joinLines("to­", "gether"))

    @Test fun `blank lines and paragraph markers split paragraphs`() {
        val page = "First paragraph here.\n\nSecond paragraph here.${mark}Third one."
        assertEquals(listOf("First paragraph here.", "Second paragraph here.", "Third one."), TextNormalizer.paragraphsOf(page))
    }

    @Test fun `short sentence-final line ends a paragraph even without markers`() {
        val long = "This is a fairly long line of body text that wraps across the page"
        val page = "$long\nand finishes here.\n$long\nagain and again."
        val paragraphs = TextNormalizer.paragraphsOf(page)
        assertEquals(2, paragraphs.size)
    }

    @Test fun `excess whitespace is collapsed`() =
        assertEquals(listOf("Too many spaces here."), TextNormalizer.paragraphsOf("Too    many \t spaces   here.  "))

    @Test fun `page numbers at top and bottom are removed but body numbers kept`() {
        val page = "12\nShe counted 42 lanterns along the river.\n\nPage 13 of 200"
        assertEquals(listOf("She counted 42 lanterns along the river."), TextNormalizer.paragraphsOf(page))
    }

    @Test fun `headings are detected conservatively`() {
        assertTrue(TextNormalizer.isHeading("Chapter 3"))
        assertTrue(TextNormalizer.isHeading("CHAPTER IV: The Garden"))
        assertTrue(TextNormalizer.isHeading("Prologue"))
        assertTrue(TextNormalizer.isHeading("THE LONG WINTER"))
        assertFalse(TextNormalizer.isHeading("Part of the reason she left was simple"))
        assertFalse(TextNormalizer.isHeading("It was a dark and stormy night."))
        assertFalse(TextNormalizer.isHeading("OK."))
    }

    @Test fun `heading line is separated from following text`() {
        val page = "CHAPTER ONE\nIt began, as these things do, with a letter."
        val blocks = TextNormalizer.buildBlocks(listOf(page))
        assertEquals(BlockKind.HEADING, blocks[0].kind)
        assertEquals("CHAPTER ONE", blocks[0].text)
        assertEquals(BlockKind.PARAGRAPH, blocks[1].kind)
    }

    @Test fun `paragraph continuing across pages is merged and keeps its start page`() {
        val pages = listOf("She opened the door and saw that the", "garden had grown wild over the winter.")
        val blocks = TextNormalizer.buildBlocks(pages)
        assertEquals(1, blocks.size)
        assertEquals("She opened the door and saw that the garden had grown wild over the winter.", blocks[0].text)
        assertEquals(0, blocks[0].pageIndex)
    }

    @Test fun `sentence continuing across pages with a capitalised word is merged`() {
        val blocks = TextNormalizer.buildBlocks(listOf("The library at the end of Willow", "Lane opened its doors at eight."))
        assertEquals("The library at the end of Willow Lane opened its doors at eight.", blocks.single().text)
    }

    @Test fun `hyphenated word across pages is joined`() {
        val blocks = TextNormalizer.buildBlocks(listOf("It was quite remark-", "able, really."))
        assertEquals("It was quite remarkable, really.", blocks.single().text)
    }

    @Test fun `new sentence on next page stays a separate paragraph`() {
        val blocks = TextNormalizer.buildBlocks(listOf("The end of a thought.", "A new thought begins."))
        assertEquals(2, blocks.size)
        assertEquals(1, blocks[1].pageIndex)
    }

    @Test fun `empty pages produce no blocks and do not break page references`() {
        val blocks = TextNormalizer.buildBlocks(listOf("", "   \n  ", "Real text on page three."))
        assertEquals(1, blocks.size)
        assertEquals(2, blocks[0].pageIndex)
    }

    @Test fun `no words are lost across many pages`() {
        val words = (1..400).map { "word$it" }
        val pages = words.chunked(100).map { it.joinToString("\n") }
        val actual = TextNormalizer.buildBlocks(pages).flatMap { it.text.split(' ') }
        assertEquals(words, actual)
    }

    @Test fun `very long paragraphs are split into bounded blocks`() {
        val sentence = "This sentence is part of a very long paragraph. "
        val page = sentence.repeat(200)
        val blocks = TextNormalizer.buildBlocks(listOf(page))
        assertTrue(blocks.size > 1)
        assertTrue(blocks.all { it.text.length <= TextNormalizer.MAX_BLOCK_CHARS })
        assertEquals(page.trim().length - (blocks.size - 1), blocks.sumOf { it.text.length })
    }
}

class TitleCleanerTest {
    @Test fun `prefers metadata title`() = assertEquals("Little Women", TitleCleaner.clean("Little Women", "lw.pdf"))

    @Test fun `falls back to file name without extension`() =
        assertEquals("my favourite book", TitleCleaner.clean(null, "my_favourite_book.pdf"))

    @Test fun `ignores junk metadata`() = assertEquals("Garden Notes", TitleCleaner.clean("untitled", "Garden Notes.pdf"))

    @Test fun `strips word processor prefixes and extensions`() =
        assertEquals("Thesis Final", TitleCleaner.clean("Microsoft Word - Thesis Final.docx", null))

    @Test fun `never returns blank`() = assertEquals("Untitled book", TitleCleaner.clean("  ", "  "))

    @Test fun `blank author becomes null`() {
        assertEquals(null, TitleCleaner.cleanAuthor("   "))
        assertEquals("L. M. Alcott", TitleCleaner.cleanAuthor(" L. M. Alcott "))
    }
}
