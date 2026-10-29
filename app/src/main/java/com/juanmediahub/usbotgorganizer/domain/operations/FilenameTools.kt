package com.juanmediahub.usbotgorganizer.domain.operations

/**
 * Pure filename helpers. Provider-specific restrictions are surfaced by the
 * document provider; these checks only reject names that are unsafe or
 * meaningless for every SAF provider.
 */
object FilenameTools {
    private val separatorChars = charArrayOf('/', '\\', '\u0000')

    fun validate(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed == "." || trimmed == "..") return false
        if (trimmed.any { it in separatorChars }) return false
        return true
    }

    fun normalize(name: String): String = name.trim()

    fun splitNameAndExtension(filename: String): Pair<String, String> {
        val dot = filename.lastIndexOf('.')
        return if (dot > 0 && dot < filename.length - 1) {
            filename.substring(0, dot) to filename.substring(dot)
        } else {
            filename to ""
        }
    }

    fun keepBothName(original: String, existingNames: Set<String>): String {
        if (original !in existingNames) return original
        val (base, ext) = splitNameAndExtension(original)
        var index = 1
        while (index < MAX_COPY_INDEX) {
            val candidate = "$base ($index)$ext"
            if (candidate !in existingNames) return candidate
            index++
        }
        return "$base (${System.currentTimeMillis()})$ext"
    }

    fun suggestedCopyName(original: String, existingNames: Set<String>): String {
        return keepBothName(original, existingNames)
    }

    private const val MAX_COPY_INDEX = 10_000
}
