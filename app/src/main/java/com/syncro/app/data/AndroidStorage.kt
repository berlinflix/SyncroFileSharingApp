package com.syncro.app.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import com.syncro.core.DeviceInfo
import com.syncro.core.transfer.DirectoryStorage
import com.syncro.core.transfer.IncomingFile
import com.syncro.core.transfer.ReceiveStorage
import com.syncro.core.transfer.TransferItemInfo
import com.syncro.core.util.FileNames
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Saves into Downloads/Syncro: MediaStore on Android 10+, the public Downloads folder before that. */
class AndroidReceiveStorage(private val context: Context) : ReceiveStorage {

    private val legacyDirectory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
    private val legacy = DirectoryStorage { legacyDirectory }

    override fun availableBytes(): Long = runCatching {
        StatFs(Environment.getExternalStorageDirectory().path).availableBytes
    }.getOrDefault(Long.MAX_VALUE)

    override fun open(transferId: String, peer: DeviceInfo, item: TransferItemInfo): IncomingFile =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) openMediaStore(item) else openLegacy(transferId, peer, item)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun openMediaStore(item: TransferItemInfo): IncomingFile {
        val resolver = context.contentResolver
        val name = FileNames.sanitize(item.name)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFor(name, item.mimeType))
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Couldn't create $name in Downloads")
        val stream = try {
            resolver.openOutputStream(uri, "w") ?: throw IOException("Couldn't write $name")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return object : IncomingFile {
            override val output: OutputStream = stream

            override fun commit(): String {
                output.close()
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                return uri.toString()
            }

            override fun abort() {
                runCatching { output.close() }
                runCatching { resolver.delete(uri, null, null) }
            }
        }
    }

    private fun openLegacy(transferId: String, peer: DeviceInfo, item: TransferItemInfo): IncomingFile {
        val delegate = legacy.open(transferId, peer, item)
        return object : IncomingFile by delegate {
            override fun commit(): String {
                val path = delegate.commit()
                MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                return path
            }
        }
    }

    companion object {
        const val FOLDER = "Syncro"

        fun mimeTypeFor(name: String, hint: String?): String {
            val ext = FileNames.extension(name).lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: FileNames.mimeFromName(name)
                ?: hint?.takeIf { it.contains('/') }
                ?: "application/octet-stream"
        }

        /** Content Uri for a stored location, whether it came from MediaStore or a legacy file path. */
        fun uriFor(context: Context, location: String): Uri =
            if (location.startsWith("content://")) {
                Uri.parse(location)
            } else {
                androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", File(location))
            }
    }
}
