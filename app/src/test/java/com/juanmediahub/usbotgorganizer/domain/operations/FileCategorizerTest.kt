package com.juanmediahub.usbotgorganizer.domain.operations

import com.juanmediahub.usbotgorganizer.domain.model.FileCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class FileCategorizerTest {
    @Test
    fun `mime types are preferred when available`() {
        assertEquals(FileCategory.IMAGES, FileCategorizer.category("image/jpeg", "weird.bin"))
        assertEquals(FileCategory.VIDEOS, FileCategorizer.category("video/mp4", "weird.bin"))
        assertEquals(FileCategory.AUDIO, FileCategorizer.category("audio/mpeg", "weird.bin"))
    }

    @Test
    fun `generic mime types fall back to the extension`() {
        assertEquals(
            FileCategory.IMAGES,
            FileCategorizer.category("application/octet-stream", "photo.HEIC")
        )
        assertEquals(
            FileCategory.ARCHIVES,
            FileCategorizer.category("application/octet-stream", "backup.7z")
        )
    }

    @Test
    fun `apk mime and extension are categorized as apks`() {
        assertEquals(
            FileCategory.APKS,
            FileCategorizer.category("application/vnd.android.package-archive", "app")
        )
        assertEquals(FileCategory.APKS, FileCategorizer.category(null, "app.apk"))
    }

    @Test
    fun `documents and unknown files are categorized`() {
        assertEquals(FileCategory.DOCUMENTS, FileCategorizer.category(null, "report.PDF"))
        assertEquals(FileCategory.OTHER, FileCategorizer.category(null, "notes.unknownext"))
        assertEquals(FileCategory.OTHER, FileCategorizer.category(null, "README"))
    }

    @Test
    fun `folder names are stable and human readable`() {
        assertEquals("Images", FileCategorizer.folderName(FileCategory.IMAGES))
        assertEquals("APKs", FileCategorizer.folderName(FileCategory.APKS))
        assertEquals("Other", FileCategorizer.folderName(FileCategory.OTHER))
    }
}
