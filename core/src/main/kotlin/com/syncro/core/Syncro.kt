package com.syncro.core

/** Protocol-wide constants shared by every Syncro implementation. */
object Syncro {
    const val PROTOCOL = "syncro"
    const val PROTOCOL_VERSION = 1

    /** UDP port used for LAN presence beacons and queries. */
    const val DISCOVERY_PORT = 47820

    /** Preferred TCP port for incoming transfers (falls back to an ephemeral port when busy). */
    const val TRANSFER_PORT = 47821

    /** Organization-local multicast group used alongside broadcasts. */
    const val MULTICAST_GROUP = "239.255.77.77"

    /** Plaintext bytes carried by a single encrypted data frame. */
    const val CHUNK_SIZE = 1 shl 20

    /** Text shares at or below this size travel inline with the offer. */
    const val MAX_INLINE_TEXT_BYTES = 128 * 1024

    const val MAX_ITEMS = 10_000
    const val MAX_NAME_LENGTH = 64
}

enum class DeviceType {
    PHONE, TABLET, LAPTOP, DESKTOP;

    companion object {
        fun parse(value: String?): DeviceType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PHONE
    }
}

data class DeviceInfo(
    val id: String,
    val name: String,
    val type: DeviceType,
)

open class SyncroException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)

class ProtocolException(message: String) : SyncroException(message)

class SecurityException(message: String) : SyncroException(message)

internal fun sanitizeDeviceName(raw: String?): String {
    val cleaned = raw.orEmpty()
        .filter { !it.isISOControl() }
        .trim()
        .take(Syncro.MAX_NAME_LENGTH)
    return cleaned.ifEmpty { "Unknown device" }
}
