package com.syncro.core.util

import java.util.Locale

object Format {
    fun bytes(value: Long): String {
        if (value < 0) return "—"
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = value.toDouble() / 1024
        var unit = 0
        while (v >= 1024 && unit < units.lastIndex) {
            v /= 1024
            unit++
        }
        val pattern = if (v >= 100) "%.0f %s" else if (v >= 10) "%.1f %s" else "%.2f %s"
        return String.format(Locale.US, pattern, v, units[unit])
    }

    fun speed(bytesPerSecond: Long): String = bytes(bytesPerSecond) + "/s"

    fun duration(seconds: Long): String = when {
        seconds < 0 -> ""
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
