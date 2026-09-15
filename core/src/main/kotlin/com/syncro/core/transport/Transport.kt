package com.syncro.core.transport

import com.syncro.core.DeviceInfo
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/** A reliable, ordered, full-duplex byte pipe to a peer. Security is layered on top by the handshake. */
interface Connection : Closeable {
    val input: InputStream
    val output: OutputStream

    /** Human readable medium, e.g. "Wi-Fi" or "Wi-Fi Direct". */
    val transportLabel: String

    /** Remote host for IP based transports, used to remember where a device was last reachable. */
    val remoteHost: String?
}

/** A device seen by one transport at one address. */
data class TransportPeer(
    val device: DeviceInfo,
    val address: String,
    val lastSeen: Long,
)

interface TransportHost {
    val self: DeviceInfo
    fun onIncomingConnection(connection: Connection)
    fun log(message: String, error: Throwable? = null)
}

/**
 * A way of finding and reaching devices (LAN sockets, Nearby Connections, ...). The engine merges
 * peers from every transport and connects through the highest-priority route that works.
 */
interface Transport {
    val id: String
    val label: String

    /** Lower connects first. */
    val priority: Int
    val discovered: StateFlow<List<TransportPeer>>

    /** Whether the transport can currently be used (permissions, radios, services). */
    val available: StateFlow<Boolean>

    fun start(host: TransportHost)
    fun setAdvertising(enabled: Boolean)
    fun setScanning(enabled: Boolean)
    suspend fun connect(addresses: List<String>): Connection
    fun stop()
}
