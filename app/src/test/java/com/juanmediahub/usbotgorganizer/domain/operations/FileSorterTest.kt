package com.juanmediahub.usbotgorganizer.domain.operations

import com.juanmediahub.usbotgorganizer.domain.model.DocumentCapabilities
import com.juanmediahub.usbotgorganizer.domain.model.SortDirection
import com.juanmediahub.usbotgorganizer.domain.model.SortField
import com.juanmediahub.usbotgorganizer.domain.model.SortSpec
import com.juanmediahub.usbotgorganizer.domain.model.StorageItem
import com.juanmediahub.usbotgorganizer.domain.model.StorageRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSorterTest {
    @Test
    fun `folders stay first even when sorting descending`() {
        val folder = item("Zeta", isDirectory = true)
        val first = item("Alpha")
        val last = item("Omega")
        val sorted = FileSorter.sort(
            listOf(first, folder, last),
            SortSpec(SortField.NAME, SortDirection.DESC, foldersFirst = true)
        )
        assertEquals(listOf("Zeta", "Omega", "Alpha"), sorted.map { it.name })
    }

    @Test
    fun `missing sizes are always listed last`() {
        val known = item("known", size = 10)
        val missing = item("missing", size = null)
        val huge = item("huge", size = 50)

        val ascending = FileSorter.sort(
            listOf(missing, huge, known),
            SortSpec(SortField.SIZE, SortDirection.ASC, foldersFirst = false)
        )
        assertEquals(listOf("known", "huge", "missing"), ascending.map { it.name })

        val descending = FileSorter.sort(
            listOf(missing, huge, known),
            SortSpec(SortField.SIZE, SortDirection.DESC, foldersFirst = false)
        )
        assertEquals(listOf("huge", "known", "missing"), descending.map { it.name })
    }

    @Test
    fun `missing modified dates are always listed last`() {
        val older = item("older", modified = 100L)
        val newer = item("newer", modified = 200L)
        val missing = item("missing", modified = null)

        val sorted = FileSorter.sort(
            listOf(missing, newer, older),
            SortSpec(SortField.MODIFIED, SortDirection.DESC, foldersFirst = false)
        )
        assertEquals(listOf("newer", "older", "missing"), sorted.map { it.name })
    }

    @Test
    fun `search filters the current item list only`() {
        val items = listOf(item("holiday.jpg"), item("notes.txt"))
        val filtered = FileSorter.sort(items, SortSpec(), query = "hol", showHidden = true)
        assertEquals(listOf("holiday.jpg"), filtered.map { it.name })
    }

    @Test
    fun `hidden names can be filtered out`() {
        val items = listOf(item(".secret"), item("visible.txt"))
        val visible = FileSorter.sort(items, SortSpec(), showHidden = false)
        assertEquals(listOf("visible.txt"), visible.map { it.name })
        assertTrue(FileSorter.sort(items, SortSpec(), showHidden = true).any { it.name == ".secret" })
    }

    private fun item(
        name: String,
        isDirectory: Boolean = false,
        size: Long? = null,
        modified: Long? = null,
        mimeType: String? = null
    ): StorageItem = StorageItem(
        ref = StorageRef(treeUri = "content://fake/tree/root", documentId = name),
        name = name,
        mimeType = mimeType,
        isDirectory = isDirectory,
        sizeBytes = size,
        modifiedEpochMs = modified,
        capabilities = DocumentCapabilities()
    )
}
