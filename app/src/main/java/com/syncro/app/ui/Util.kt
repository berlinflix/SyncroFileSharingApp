package com.syncro.app.ui

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.syncro.app.data.AndroidReceiveStorage
import com.syncro.core.util.FileNames

fun iconForFile(name: String, mimeType: String?, isText: Boolean = false): ImageVector {
    if (isText) return Icons.Rounded.Notes
    val mime = mimeType ?: FileNames.mimeFromName(name) ?: ""
    val ext = FileNames.extension(name).lowercase()
    return when {
        mime.startsWith("image/") -> Icons.Rounded.Image
        mime.startsWith("video/") -> Icons.Rounded.Movie
        mime.startsWith("audio/") -> Icons.Rounded.MusicNote
        mime == "application/pdf" -> Icons.Rounded.PictureAsPdf
        ext == "apk" -> Icons.Rounded.Android
        ext in setOf("zip", "rar", "7z", "tar", "gz") -> Icons.Rounded.FolderZip
        mime.startsWith("text/") || ext in setOf("doc", "docx", "odt", "rtf", "xls", "xlsx", "ppt", "pptx") -> Icons.Rounded.Description
        else -> Icons.Rounded.InsertDriveFile
    }
}

fun isVisualMedia(name: String, mimeType: String?): Boolean {
    val mime = mimeType ?: FileNames.mimeFromName(name) ?: return false
    return mime.startsWith("image/") || mime.startsWith("video/")
}

fun openReceivedFile(context: Context, location: String, mimeType: String?, name: String) {
    try {
        val uri = AndroidReceiveStorage.uriFor(context, location)
        val type = context.contentResolver.getType(uri) ?: AndroidReceiveStorage.mimeTypeFor(name, mimeType)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

fun openDownloads(context: Context) {
    try {
        context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Files are in Downloads/Syncro", Toast.LENGTH_SHORT).show()
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Syncro", text))
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openLink(context: Context, text: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(text.trim())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun looksLikeUrl(text: String): Boolean {
    val t = text.trim()
    return !t.contains(' ') && !t.contains('\n') && (t.startsWith("http://") || t.startsWith("https://"))
}

fun relativeTime(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

fun qrBitmap(content: String, size: Int = 720, foreground: Int = 0xFF0E1116.toInt(), background: Int = 0xFFFFFFFF.toInt()): ImageBitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8")
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        val offset = y * matrix.width
        for (x in 0 until matrix.width) pixels[offset + x] = if (matrix[x, y]) foreground else background
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}
