package com.juanmediahub.usbotgorganizer.domain.operations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilenameToolsTest {
    @Test
    fun `validate rejects empty and whitespace only names`() {
        assertFalse(FilenameTools.validate(""))
        assertFalse(FilenameTools.validate("   "))
    }

    @Test
    fun `validate rejects path separators and relative markers`() {
        assertFalse(FilenameTools.validate("a/b"))
        assertFalse(FilenameTools.validate("a\\b"))
        assertFalse(FilenameTools.validate("."))
        assertFalse(FilenameTools.validate(".."))
        assertFalse(FilenameTools.validate("a\u0000b"))
    }

    @Test
    fun `validate accepts ordinary names and normalizes whitespace`() {
        assertTrue(FilenameTools.validate("Report.pdf"))
        assertTrue(FilenameTools.validate("Photos 2026"))
        assertEquals("Report.pdf", FilenameTools.normalize("  Report.pdf  "))
    }

    @Test
    fun `keep both adds an index before the extension`() {
        val existing = setOf("Report.pdf")
        assertEquals("Report (1).pdf", FilenameTools.keepBothName("Report.pdf", existing))
    }

    @Test
    fun `keep both finds the next free index`() {
        val existing = setOf("Report.pdf", "Report (1).pdf", "Report (2).pdf")
        assertEquals("Report (3).pdf", FilenameTools.keepBothName("Report.pdf", existing))
    }

    @Test
    fun `keep both handles names without an extension`() {
        val existing = setOf("notes")
        assertEquals("notes (1)", FilenameTools.keepBothName("notes", existing))
    }

    @Test
    fun `keep both returns the original when there is no conflict`() {
        assertEquals("Report.pdf", FilenameTools.keepBothName("Report.pdf", emptySet()))
    }

    @Test
    fun `split name keeps a leading dot as part of the name`() {
        assertEquals(".env" to "", FilenameTools.splitNameAndExtension(".env"))
        assertEquals("archive.tar" to ".gz", FilenameTools.splitNameAndExtension("archive.tar.gz"))
    }
}
