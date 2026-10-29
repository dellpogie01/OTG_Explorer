package com.juanmediahub.usbotgorganizer.ui.components

import android.content.Context
import com.juanmediahub.usbotgorganizer.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object Formatters {
    fun size(context: Context, bytes: Long?): String {
        if (bytes == null || bytes < 0) return context.getString(R.string.metadata_unavailable)
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(units.size)
        val value = bytes / 1024.0.pow(exp.toDouble())
        return String.format(Locale.getDefault(), "%.1f %s", value, units[exp - 1])
    }

    fun date(context: Context, epochMs: Long?): String {
        if (epochMs == null || epochMs <= 0L) return context.getString(R.string.metadata_unavailable)
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
    }
}
