package com.syncro.core.util

import java.io.File

object FileNames {
    private val RESERVED = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )

    /** Makes a received name safe on every file system: no paths, control chars, reserved names or overlong names. */
    fun sanitize(raw: String): String {
        var name = raw.substringAfterLast('/').substringAfterLast('\\')
            .map { c -> if (c.isISOControl() || c in "<>:\"|?*") '_' else c }
            .joinToString("")
            .trim()
            .trimEnd('.', ' ')
        if (name.isEmpty() || name == "." || name == "..") name = "file"
        val base = name.substringBefore('.')
        if (base.uppercase() in RESERVED) name = "_$name"
        if (name.length > 180) {
            val ext = extension(name).take(16)
            name = name.take(180 - ext.length - 1).trimEnd('.', ' ') + if (ext.isNotEmpty()) ".$ext" else ""
        }
        return name
    }

    fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot in 1 until name.length - 1) name.substring(dot + 1) else ""
    }

    /** "photo.jpg" -> "photo (1).jpg", "photo (2).jpg", ... until [exists] returns false. */
    fun unique(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val ext = extension(name)
        val stem = if (ext.isEmpty()) name else name.dropLast(ext.length + 1)
        var i = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$stem ($i)" else "$stem ($i).$ext"
            if (!exists(candidate)) return candidate
            i++
        }
    }

    fun uniqueFile(directory: File, name: String): File = File(directory, unique(sanitize(name)) { File(directory, it).exists() })

    fun mimeFromName(name: String): String? = when (extension(name).lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "ogg" -> "audio/ogg"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "rar" -> "application/vnd.rar"
        "7z" -> "application/x-7z-compressed"
        "apk" -> "application/vnd.android.package-archive"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "html", "htm" -> "text/html"
        "json" -> "application/json"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        else -> null
    }
}
