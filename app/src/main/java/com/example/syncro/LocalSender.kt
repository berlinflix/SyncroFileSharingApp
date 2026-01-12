package com.example.syncro

import android.util.Log
import java.io.DataOutputStream
import java.net.Socket
import kotlin.concurrent.thread

object LocalSender {

    private const val PORT = 8988

    fun sendFile(
        receiverIp: String,
        fileName: String,
        fileBytes: ByteArray
    ) {
        TransferState.start(TransferRole.SENDER)

        Thread {
            try {
                val socket = Socket(receiverIp, PORT)
                val output = DataOutputStream(socket.getOutputStream())

                // 🔑 PROTOCOL (MUST MATCH RECEIVER)
                output.writeUTF(fileName)
                output.writeLong(fileBytes.size.toLong())

                var sent = 0
                val chunkSize = 4096

                while (sent < fileBytes.size) {
                    val end = minOf(sent + chunkSize, fileBytes.size)
                    output.write(fileBytes, sent, end - sent)
                    sent = end

                    val percent = (sent * 100L / fileBytes.size).toInt()
                    TransferState.updateProgress(percent)
                }

                output.flush()
                socket.close()

                TransferState.markCompleted()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
