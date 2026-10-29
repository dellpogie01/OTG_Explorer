package com.juanmediahub.usbotgorganizer.domain.operations

import com.juanmediahub.usbotgorganizer.domain.model.SortDirection
import com.juanmediahub.usbotgorganizer.domain.model.SortField
import com.juanmediahub.usbotgorganizer.domain.model.SortSpec
import com.juanmediahub.usbotgorganizer.domain.model.StorageItem
import java.util.Locale

object FileSorter {
    fun sort(
        items: List<StorageItem>,
        spec: SortSpec,
        query: String = "",
        showHidden: Boolean = true
    ): List<StorageItem> {
        val needle = query.trim().lowercase(Locale.ROOT)
        return items.asSequence()
            .filter { showHidden || !it.name.startsWith('.') }
            .filter { needle.isEmpty() || it.name.lowercase(Locale.ROOT).contains(needle) }
            .sortedWith(comparator(spec))
            .toList()
    }

    fun comparator(spec: SortSpec): Comparator<StorageItem> {
        val direction = if (spec.direction == SortDirection.ASC) 1 else -1
        return Comparator { a, b ->
            if (spec.foldersFirst && a.isDirectory != b.isDirectory) {
                return@Comparator if (a.isDirectory) -1 else 1
            }
            val primary = when (spec.field) {
                SortField.NAME -> compareNames(a.name, b.name) * direction
                SortField.SIZE -> compareUnknownLast(a.sizeBytes, b.sizeBytes, direction)
                SortField.MODIFIED -> compareUnknownLast(a.modifiedEpochMs, b.modifiedEpochMs, direction)
                SortField.TYPE -> compareNames(typeKey(a), typeKey(b)) * direction
            }
            if (primary != 0) primary else compareNames(a.name, b.name)
        }
    }

    private fun typeKey(item: StorageItem): String {
        return if (item.isDirectory) {
            "0_folder"
        } else {
            val mime = item.mimeType?.lowercase(Locale.ROOT).orEmpty()
            val ext = item.name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
            "1_${mime}_$ext"
        }
    }

    private fun compareNames(a: String, b: String): Int = a.compareTo(b, ignoreCase = true)

    /**
     * Unknown metadata is always listed after known metadata, independent of
     * ascending/descending order, so placeholder values cannot masquerade as
     * real zeroes or dates.
     */
    private fun compareUnknownLast(a: Long?, b: Long?, direction: Int): Int {
        return when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            else -> a.compareTo(b) * direction
        }
    }
}
