package com.syncro.core.transfer

import com.syncro.core.DeviceInfo
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream

/** Something the user wants to send. Sizes must be known up-front so the receiver can approve them. */
interface SendItem {
    val name: String
    val size: Long
    val mimeType: String?
    fun open(): InputStream
}

class FileSendItem(private val file: File, override val mimeType: String? = null) : SendItem {
    override val name: String = file.name
    override val size: Long = file.length()
    override fun open(): InputStream = FileInputStream(file)
}

class TextSendItem(val text: String) : SendItem {
    private val bytes = text.toByteArray(Charsets.UTF_8)
    override val name: String = "Text"
    override val size: Long = bytes.size.toLong()
    override val mimeType: String = "text/plain"
    override fun open(): InputStream = ByteArrayInputStream(bytes)
}

enum class Direction { SEND, RECEIVE }

enum class TransferPhase {
    CONNECTING,
    WAITING_FOR_ACCEPT,
    AWAITING_DECISION,
    TRANSFERRING,
    COMPLETED,
    REJECTED,
    CANCELLED,
    FAILED;

    val isFinished: Boolean get() = this == COMPLETED || this == REJECTED || this == CANCELLED || this == FAILED
}

data class TransferItemInfo(
    val index: Int,
    val name: String,
    val size: Long,
    val mimeType: String?,
    val isText: Boolean,
    val text: String? = null,
)

data class ReceivedItem(
    val index: Int,
    val name: String,
    val size: Long,
    val mimeType: String?,
    /** Platform location of the saved file (content Uri on Android, absolute path on desktop). */
    val location: String?,
    val text: String?,
)

data class TransferInfo(
    val id: String,
    val direction: Direction,
    val peer: DeviceInfo?,
    val peerTrusted: Boolean = false,
    val items: List<TransferItemInfo>,
    val totalBytes: Long,
    val bytesDone: Long = 0,
    val bytesPerSecond: Long = 0,
    val phase: TransferPhase,
    val pin: String? = null,
    val transport: String? = null,
    val message: String? = null,
    val currentItem: Int = -1,
    val received: List<ReceivedItem> = emptyList(),
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
) {
    val fraction: Float
        get() = when {
            phase == TransferPhase.COMPLETED -> 1f
            totalBytes <= 0 -> 0f
            else -> (bytesDone.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
        }

    val isActive: Boolean get() = !phase.isFinished

    val etaSeconds: Long
        get() = if (bytesPerSecond <= 0 || totalBytes <= 0) -1 else (totalBytes - bytesDone).coerceAtLeast(0) / bytesPerSecond

    val peerName: String get() = peer?.name ?: "Unknown device"
}

/** Where received files go. Implemented per platform (MediaStore on Android, a folder on desktop). */
interface ReceiveStorage {
    fun availableBytes(): Long = Long.MAX_VALUE
    fun open(transferId: String, peer: DeviceInfo, item: TransferItemInfo): IncomingFile
}

interface IncomingFile {
    val output: OutputStream

    /** Closes the output, makes the file visible and returns its location. */
    fun commit(): String

    /** Closes the output and deletes the partial file. */
    fun abort()
}

enum class Decision { ACCEPT, ACCEPT_AND_TRUST, DECLINE }
