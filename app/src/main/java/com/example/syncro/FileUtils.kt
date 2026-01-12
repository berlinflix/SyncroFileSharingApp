package com.example.syncro

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.OutputStream

object FileUtils {

    /**
     * ✅ Get real filename WITH extension from Uri
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var fileName = "syncro_file"

        val cursor: Cursor? = context.contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    fileName = it.getString(index)
                }
            }
        }
        return fileName
    }

    /**
     * ✅ Save received file AND return its Uri
     */
    fun saveFile(
        context: Context,
        fileName: String,
        data: ByteArray
    ): Uri? {

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(fileName))
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/Syncro"
            )
        }

        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: return null

        val outputStream: OutputStream? =
            context.contentResolver.openOutputStream(uri)

        outputStream?.use {
            it.write(data)
            it.flush()
        }

        return uri
    }

    private fun getMimeType(fileName: String): String =
        when {
            fileName.endsWith(".jpg", true) -> "image/jpeg"
            fileName.endsWith(".jpeg", true) -> "image/jpeg"
            fileName.endsWith(".png", true) -> "image/png"
            fileName.endsWith(".pdf", true) -> "application/pdf"
            fileName.endsWith(".mp4", true) -> "video/mp4"
            else -> "application/octet-stream"
        }
}
