package com.example.syncro

import android.content.Context
import android.util.Log
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

object LocalReceiver {

    private const val PORT = 8988

    @Volatile
    private var isReceiving = false

    private var serverSocket: ServerSocket? = null

    /**
     * 🚀 Call ONLY when user taps "Receive"
     */
    fun startReceiving(context: Context) {
        if (isReceiving) {
            Log.d("SyncroReceiver", "Already receiving")
            return
        }

        isReceiving = true

        thread {
            try {
                serverSocket = ServerSocket(PORT).apply {
                    reuseAddress = true
                }

                Log.d("SyncroReceiver", "Listening on port $PORT")

                val socket = serverSocket!!.accept()
                Log.d("SyncroReceiver", "Client connected")

                handleClient(socket, context)

            } catch (e: Exception) {
                if (isReceiving) {
                    Log.e("SyncroReceiver", "Server error", e)
                }
            }
        }
    }

    /**
     * 📥 Receives exactly ONE file
     */
    private fun handleClient(socket: Socket, context: Context) {
        thread {
            try {
                TransferState.start(TransferRole.RECEIVER)

                val input = DataInputStream(socket.getInputStream())

                // 🔑 PROTOCOL (must match sender)
                val fileName = input.readUTF()
                val fileSize = input.readLong()

                Log.d(
                    "SyncroReceiver",
                    "Incoming file: $fileName size=$fileSize"
                )

                if (fileSize <= 0 || fileSize > 500L * 1024 * 1024) {
                    throw IllegalArgumentException("Invalid file size")
                }

                val buffer = ByteArray(fileSize.toInt())
                var bytesRead = 0
                val chunkSize = 4096

                while (bytesRead < buffer.size && isReceiving) {
                    val read = input.read(
                        buffer,
                        bytesRead,
                        minOf(chunkSize, buffer.size - bytesRead)
                    )

                    if (read == -1) break

                    bytesRead += read
                    val percent = ((bytesRead * 100L) / fileSize).toInt()
                    TransferState.updateProgress(percent)
                }

                Log.d(
                    "SyncroReceiver",
                    "Bytes received: $bytesRead / $fileSize"
                )

                if (bytesRead != fileSize.toInt()) {
                    throw IllegalStateException("Incomplete file received")
                }

                // 💾 Save file
                val uri = FileUtils.saveFile(context, fileName, buffer)

                if (uri != null) {
                    TransferState.setReceivedFile(uri)
                    TransferState.markCompleted()
                } else {
                    Log.e("SyncroReceiver", "File save failed")
                }

                TransferState.markCompleted()

                Log.d("SyncroReceiver", "Saved file URI: $uri")


            } catch (e: Exception) {
                Log.e("SyncroReceiver", "Receive failed", e)
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {}

                stop()
            }
        }
    }

    /**
     * 🛑 Stops receiver safely
     */
    fun stop() {
        isReceiving = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        Log.d("SyncroReceiver", "Receiver stopped")
    }
}
