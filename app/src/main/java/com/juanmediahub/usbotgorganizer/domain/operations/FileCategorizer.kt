package com.juanmediahub.usbotgorganizer.domain.operations

import com.juanmediahub.usbotgorganizer.domain.model.FileCategory
import java.util.Locale

object FileCategorizer {
    private val imageExt = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg",
        "tif", "tiff", "raw", "cr2", "nef", "arw", "dng"
    )
    private val videoExt = setOf(
        "mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "wmv", "flv",
        "mpeg", "mpg", "ts", "ogv"
    )
    private val audioExt = setOf(
        "mp3", "wav", "aac", "flac", "ogg", "m4a", "wma", "opus", "amr",
        "aiff", "aif", "mid", "midi"
    )
    private val documentExt = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf",
        "odt", "ods", "odp", "csv", "html", "htm", "md", "json", "xml",
        "epub", "log", "tex"
    )
    private val archiveExt = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "iso", "cab",
        "lz", "lzma", "zst"
    )
    private val apkExt = setOf("apk", "xapk", "apks", "aab")

    fun category(mimeType: String?, filename: String): FileCategory {
        val mime = mimeType?.lowercase(Locale.ROOT)?.trim().orEmpty()
        if (mime.isNotEmpty() && mime != "application/octet-stream") {
            when {
                mime.startsWith("image/") -> return FileCategory.IMAGES
                mime.startsWith("video/") -> return FileCategory.VIDEOS
                mime.startsWith("audio/") -> return FileCategory.AUDIO
                mime == "application/vnd.android.package-archive" -> return FileCategory.APKS
                mime == "application/zip" ||
                    mime == "application/x-zip-compressed" ||
                    mime == "application/vnd.rar" ||
                    mime == "application/x-rar-compressed" ||
                    mime == "application/x-7z-compressed" ||
                    mime == "application/gzip" ||
                    mime == "application/x-gzip" ||
                    mime == "application/x-tar" ||
                    mime == "application/x-bzip2" ||
                    mime == "application/x-xz" ||
                    mime == "application/zstd" ||
                    mime == "application/x-iso9660-image" -> return FileCategory.ARCHIVES
                mime == "application/pdf" ||
                    mime.startsWith("text/") ||
                    mime == "application/rtf" ||
                    mime.contains("msword") ||
                    mime.contains("officedocument") ||
                    mime.contains("opendocument") ||
                    mime.contains("ms-excel") ||
                    mime.contains("ms-powerpoint") ||
                    mime == "application/json" ||
                    mime == "application/xml" ||
                    mime == "application/epub+zip" -> return FileCategory.DOCUMENTS
            }
        }
        return when (extensionOf(filename)) {
            in imageExt -> FileCategory.IMAGES
            in videoExt -> FileCategory.VIDEOS
            in audioExt -> FileCategory.AUDIO
            in documentExt -> FileCategory.DOCUMENTS
            in archiveExt -> FileCategory.ARCHIVES
            in apkExt -> FileCategory.APKS
            else -> FileCategory.OTHER
        }
    }

    fun folderName(category: FileCategory): String = when (category) {
        FileCategory.IMAGES -> "Images"
        FileCategory.VIDEOS -> "Videos"
        FileCategory.AUDIO -> "Audio"
        FileCategory.DOCUMENTS -> "Documents"
        FileCategory.ARCHIVES -> "Archives"
        FileCategory.APKS -> "APKs"
        FileCategory.OTHER -> "Other"
    }

    fun extensionOf(filename: String): String {
        val dot = filename.lastIndexOf('.')
        if (dot <= 0 || dot == filename.length - 1) return ""
        return filename.substring(dot + 1).lowercase(Locale.ROOT)
    }
}
