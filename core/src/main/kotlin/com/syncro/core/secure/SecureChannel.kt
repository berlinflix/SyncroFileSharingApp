package com.syncro.core.secure

import com.syncro.core.ProtocolException
import com.syncro.core.Syncro
import kotlinx.serialization.SerializationException
import java.io.Closeable
import java.io.IOException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM record layer.
 *
 * Record: `u32 ciphertextLength || AES-GCM(key, nonce = 0^4 || u64 counter, aad = length, type || body)`.
 * Each direction has its own key and a strictly increasing counter, so records cannot be replayed,
 * reordered, truncated or moved between directions without failing authentication.
 */
class SecureChannel internal constructor(
    private val wire: Wire,
    sendKey: ByteArray,
    receiveKey: ByteArray,
    private val onClose: () -> Unit,
) : Closeable {

    class Frame internal constructor(val type: Byte, val buffer: ByteArray, val offset: Int, val length: Int)

    private val sendKeySpec = SecretKeySpec(sendKey, "AES")
    private val receiveKeySpec = SecretKeySpec(receiveKey, "AES")
    private val sendCipher = Cipher.getInstance("AES/GCM/NoPadding")
    private val receiveCipher = Cipher.getInstance("AES/GCM/NoPadding")

    private val writeLock = Any()
    private var sendCounter = 0L
    private var receiveCounter = 0L

    private val sendPlain = ByteArray(MAX_PLAINTEXT)
    private val sendRecord = ByteArray(4 + MAX_PLAINTEXT + TAG_BYTES)
    private val receiveHeader = ByteArray(4)
    private val receiveRecord = ByteArray(MAX_PLAINTEXT + TAG_BYTES)
    private val receivePlain = ByteArray(MAX_PLAINTEXT + TAG_BYTES)

    fun send(type: Byte, body: ByteArray, offset: Int = 0, length: Int = body.size) {
        require(length in 0..(MAX_PLAINTEXT - 1)) { "Record too large" }
        synchronized(writeLock) {
            sendPlain[0] = type
            System.arraycopy(body, offset, sendPlain, 1, length)
            val cipherLength = 1 + length + TAG_BYTES
            putInt(sendRecord, 0, cipherLength)
            sendCipher.init(Cipher.ENCRYPT_MODE, sendKeySpec, GCMParameterSpec(TAG_BYTES * 8, nonce(sendCounter++)))
            sendCipher.updateAAD(sendRecord, 0, 4)
            val written = sendCipher.doFinal(sendPlain, 0, 1 + length, sendRecord, 4)
            if (written != cipherLength) throw IOException("Unexpected cipher output size")
            wire.output.write(sendRecord, 0, 4 + cipherLength)
            wire.output.flush()
        }
    }

    fun sendControl(message: Msg) {
        val bytes = WireJson.encodeToString(Msg.serializer(), message).toByteArray()
        send(TYPE_CONTROL, bytes)
    }

    /** Reads and authenticates the next record. The returned frame's buffer is reused by the next call. */
    fun receive(): Frame {
        wire.input.readFully(receiveHeader)
        val cipherLength = getInt(receiveHeader, 0)
        if (cipherLength < 1 + TAG_BYTES || cipherLength > MAX_PLAINTEXT + TAG_BYTES) {
            throw ProtocolException("Invalid record length $cipherLength")
        }
        wire.input.readFully(receiveRecord, 0, cipherLength)
        val plainLength = try {
            receiveCipher.init(Cipher.DECRYPT_MODE, receiveKeySpec, GCMParameterSpec(TAG_BYTES * 8, nonce(receiveCounter++)))
            receiveCipher.updateAAD(receiveHeader, 0, 4)
            receiveCipher.doFinal(receiveRecord, 0, cipherLength, receivePlain, 0)
        } catch (e: AEADBadTagException) {
            throw com.syncro.core.SecurityException("Record authentication failed — data was modified in transit")
        }
        return Frame(receivePlain[0], receivePlain, 1, plainLength - 1)
    }

    fun receiveControl(): Msg {
        val frame = receive()
        if (frame.type != TYPE_CONTROL) throw ProtocolException("Expected control message")
        return decodeControl(frame)
    }

    override fun close() = onClose()

    companion object {
        const val TYPE_CONTROL: Byte = 1
        const val TYPE_DATA: Byte = 2
        private const val TAG_BYTES = 16
        private const val MAX_PLAINTEXT = Syncro.CHUNK_SIZE + 1

        fun decodeControl(frame: Frame): Msg = try {
            WireJson.decodeFromString(Msg.serializer(), String(frame.buffer, frame.offset, frame.length, Charsets.UTF_8))
        } catch (_: SerializationException) {
            Msg.Unknown
        } catch (_: IllegalArgumentException) {
            Msg.Unknown
        }

        private fun nonce(counter: Long): ByteArray {
            val nonce = ByteArray(12)
            for (i in 0 until 8) nonce[11 - i] = (counter ushr (8 * i)).toByte()
            return nonce
        }

        private fun putInt(buffer: ByteArray, offset: Int, value: Int) {
            buffer[offset] = (value ushr 24).toByte()
            buffer[offset + 1] = (value ushr 16).toByte()
            buffer[offset + 2] = (value ushr 8).toByte()
            buffer[offset + 3] = value.toByte()
        }

        private fun getInt(buffer: ByteArray, offset: Int): Int =
            ((buffer[offset].toInt() and 0xff) shl 24) or
                ((buffer[offset + 1].toInt() and 0xff) shl 16) or
                ((buffer[offset + 2].toInt() and 0xff) shl 8) or
                (buffer[offset + 3].toInt() and 0xff)
    }
}
