package com.syncro.core.secure

import com.syncro.core.ProtocolException
import com.syncro.core.transport.Connection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.nio.ByteBuffer

internal val WireJson = Json {
    classDiscriminator = "t"
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/** Length-prefixed framing over a connection. One instance per connection: it owns the read buffer. */
internal class Wire(connection: Connection) {
    val input = DataInputStream(BufferedInputStream(connection.input, 64 * 1024))
    val output: OutputStream = connection.output

    fun writePlain(payload: ByteArray) {
        val frame = ByteBuffer.allocate(4 + payload.size).putInt(payload.size).put(payload).array()
        output.write(frame)
        output.flush()
    }

    fun readPlain(maxSize: Int): ByteArray {
        val size = input.readInt()
        if (size < 0 || size > maxSize) throw ProtocolException("Handshake frame too large ($size)")
        return ByteArray(size).also(input::readFully)
    }
}

@Serializable
internal data class WireDevice(val id: String, val name: String, val type: String)

@Serializable
internal data class ClientInit(val proto: String, val v: Int, val commit: String)

@Serializable
internal data class ServerInit(
    val proto: String,
    val v: Int,
    val eph: String,
    val nonce: String,
    val key: String,
    val device: WireDevice,
)

@Serializable
internal data class ClientKey(
    val eph: String,
    val nonce: String,
    val key: String,
    val device: WireDevice,
)

@Serializable
data class WireItem(
    val i: Int,
    val name: String,
    val size: Long,
    val mime: String? = null,
    val kind: String = KIND_FILE,
    val text: String? = null,
) {
    companion object {
        const val KIND_FILE = "file"
        const val KIND_TEXT = "text"
    }
}

/** Control messages, sent encrypted after the handshake. */
@Serializable
sealed interface Msg {
    @Serializable @SerialName("auth")
    data class Auth(val sig: String) : Msg

    @Serializable @SerialName("offer")
    data class Offer(val id: String, val items: List<WireItem>) : Msg

    @Serializable @SerialName("wait")
    data object Waiting : Msg

    @Serializable @SerialName("accept")
    data object Accept : Msg

    @Serializable @SerialName("reject")
    data class Reject(val reason: String = "declined") : Msg

    @Serializable @SerialName("file")
    data class FileStart(val i: Int) : Msg

    @Serializable @SerialName("file_end")
    data class FileEnd(val i: Int, val bytes: Long) : Msg

    @Serializable @SerialName("skip")
    data class Skip(val i: Int, val reason: String) : Msg

    @Serializable @SerialName("done")
    data object Done : Msg

    @Serializable @SerialName("done_ack")
    data object DoneAck : Msg

    @Serializable @SerialName("cancel")
    data class Cancel(val reason: String = "cancelled") : Msg

    /** Placeholder for message types added by newer protocol revisions. */
    @Serializable @SerialName("unknown")
    data object Unknown : Msg
}
