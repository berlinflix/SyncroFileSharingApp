package com.syncro.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.syncro.core.transfer.SendItem
import com.syncro.core.transfer.TextSendItem
import com.syncro.core.util.FileNames
import java.io.File
import java.io.IOException
import java.io.InputStream

/** A file chosen from the picker, gallery or another app's share sheet. */
class UriSendItem(
    private val context: Context,
    val uri: Uri,
    override val name: String,
    override val size: Long,
    override val mimeType: String?,
) : SendItem {
    override fun open(): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IOException("Can't open $name")

    companion object {
        /** Resolves name, size and type. Streams without a known size are copied to cache first. Call off the main thread. */
        fun resolve(context: Context, uri: Uri): SendItem? {
            val resolver = context.contentResolver
            var name: String? = null
            var size = -1L
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0) name = cursor.getString(nameIndex)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }
            }
            if (uri.scheme == "file") {
                val file = File(uri.path ?: return null)
                name = name ?: file.name
                if (size < 0) size = file.length()
            }
            if (size < 0) {
                size = runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
            }
            val mime = resolver.getType(uri)
            val displayName = FileNames.sanitize(name ?: uri.lastPathSegment ?: "file")
            val finalName = if (FileNames.extension(displayName).isEmpty() && mime != null) {
                android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { "$displayName.$it" } ?: displayName
            } else {
                displayName
            }
            if (size >= 0) return UriSendItem(context, uri, finalName, size, mime)

            // Unknown length (some cloud providers): buffer to cache so the receiver can approve an exact size.
            return runCatching {
                val dir = File(context.cacheDir, "outgoing").apply { mkdirs() }
                val copy = File(dir, "${System.nanoTime()}-$finalName")
                resolver.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it, 256 * 1024) } } ?: return null
                CachedFileItem(copy, finalName, mime)
            }.getOrNull()
        }

        fun text(text: String): SendItem = TextSendItem(text)
    }
}

private class CachedFileItem(private val file: File, override val name: String, override val mimeType: String?) : SendItem {
    override val size: Long = file.length()
    override fun open(): InputStream = file.inputStream()
}
