package com.syncro.app.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.syncro.core.DeviceInfo
import com.syncro.core.DeviceType
import com.syncro.core.SyncroException
import com.syncro.core.transport.Connection
import com.syncro.core.transport.Transport
import com.syncro.core.transport.TransportHost
import com.syncro.core.transport.TransportPeer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Google Nearby Connections — the same radio stack Quick Share uses. Devices find each other over
 * Bluetooth/BLE and the link is upgraded to Wi-Fi Direct or a local hotspot, so no shared network
 * is required. Syncro runs its own end-to-end encrypted protocol over a pair of stream payloads.
 */
class NearbyTransport(private val context: Context) : Transport {
    override val id: String = ID
    override val label: String = "Nearby"
    override val priority: Int = 10

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val _discovered = MutableStateFlow<List<TransportPeer>>(emptyList())
    override val discovered: StateFlow<List<TransportPeer>> = _discovered.asStateFlow()
    private val _available = MutableStateFlow(hasPermissions(context))
    override val available: StateFlow<Boolean> = _available.asStateFlow()

    private lateinit var host: TransportHost
    private val endpoints = ConcurrentHashMap<String, TransportPeer>()
    private val connections = ConcurrentHashMap<String, NearbyConnection>()
    private val earlyStreams = ConcurrentHashMap<String, InputStream>()
    private val incomingFlags = ConcurrentHashMap<String, Boolean>()
    private val pendingOutgoing = ConcurrentHashMap<String, CompletableDeferred<Connection>>()

    @Volatile private var wantAdvertising = false
    @Volatile private var wantScanning = false
    @Volatile private var advertising = false
    @Volatile private var scanning = false
    @Volatile private var connectingCount = 0

    override fun start(host: TransportHost) {
        this.host = host
    }

    override fun setAdvertising(enabled: Boolean) {
        wantAdvertising = enabled
        applyAdvertising()
    }

    override fun setScanning(enabled: Boolean) {
        wantScanning = enabled
        applyScanning()
    }

    /** Call after runtime permissions change. */
    fun refresh() {
        _available.value = hasPermissions(context)
        applyAdvertising()
        applyScanning()
    }

    /** Re-advertise with a new device name. */
    fun restartAdvertising() {
        if (advertising) {
            client.stopAdvertising()
            advertising = false
        }
        applyAdvertising()
    }

    override fun stop() {
        wantAdvertising = false
        wantScanning = false
        applyAdvertising()
        applyScanning()
        client.stopAllEndpoints()
    }

    @Synchronized
    private fun applyAdvertising() {
        val should = wantAdvertising && hasPermissions(context)
        if (should && !advertising) {
            advertising = true
            val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
            client.startAdvertising(encodeName(host.self), SERVICE_ID, lifecycle, options)
                .addOnFailureListener { error ->
                    advertising = false
                    host.log("Nearby advertising unavailable: ${error.message}")
                }
        } else if (!should && advertising) {
            client.stopAdvertising()
            advertising = false
        }
    }

    @Synchronized
    private fun applyScanning() {
        val should = wantScanning && hasPermissions(context) && connectingCount == 0
        if (should && !scanning) {
            scanning = true
            val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
            client.startDiscovery(SERVICE_ID, discovery, options)
                .addOnFailureListener { error ->
                    scanning = false
                    host.log("Nearby discovery unavailable: ${error.message}")
                }
        } else if (!should && scanning) {
            client.stopDiscovery()
            scanning = false
            if (!wantScanning) {
                endpoints.clear()
                publish()
            }
        }
    }

    override suspend fun connect(addresses: List<String>): Connection {
        val endpointId = addresses.firstOrNull() ?: throw SyncroException("No Nearby endpoint")
        if (!hasPermissions(context)) throw SyncroException("Nearby permissions not granted")
        val deferred = CompletableDeferred<Connection>()
        pendingOutgoing[endpointId] = deferred
        // Discovery competes for the radio and makes connection setup flaky; pause it while connecting.
        synchronized(this) { connectingCount++ }
        applyScanning()
        try {
            client.requestConnection(encodeName(host.self), endpointId, lifecycle)
                .addOnFailureListener { error ->
                    deferred.completeExceptionally(SyncroException("Nearby connection failed: ${error.message}"))
                }
            return deferred.await()
        } catch (e: Throwable) {
            if (!deferred.isCompleted) client.disconnectFromEndpoint(endpointId)
            throw e
        } finally {
            pendingOutgoing.remove(endpointId)
            synchronized(this) { connectingCount-- }
            applyScanning()
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            val device = decodeName(info.endpointName) ?: return
            if (device.id == host.self.id) return
            endpoints[endpointId] = TransportPeer(device, endpointId, System.currentTimeMillis())
            publish()
        }

        override fun onEndpointLost(endpointId: String) {
            if (endpoints.remove(endpointId) != null) publish()
        }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            incomingFlags[endpointId] = info.isIncomingConnection
            if (info.isIncomingConnection && !wantAdvertising) {
                client.rejectConnection(endpointId)
                return
            }
            // Syncro's own handshake authenticates the device and encrypts end to end.
            client.acceptConnection(endpointId, payloads)
                .addOnFailureListener { error ->
                    pendingOutgoing[endpointId]?.completeExceptionally(SyncroException("Nearby accept failed: ${error.message}"))
                }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (!resolution.status.isSuccess) {
                pendingOutgoing[endpointId]?.completeExceptionally(
                    SyncroException("Nearby connection rejected (${resolution.status.statusCode})"),
                )
                incomingFlags.remove(endpointId)
                return
            }
            val connection = NearbyConnection(endpointId, client)
            connections[endpointId] = connection
            earlyStreams.remove(endpointId)?.let(connection::attachIncoming)
            try {
                connection.openOutgoing()
            } catch (e: Exception) {
                connection.close()
                pendingOutgoing[endpointId]?.completeExceptionally(e)
                return
            }
            val incoming = incomingFlags.remove(endpointId) == true
            val pending = pendingOutgoing[endpointId]
            if (!incoming && pending != null) {
                if (!pending.complete(connection)) connection.close()
            } else {
                host.onIncomingConnection(connection)
            }
        }

        override fun onDisconnected(endpointId: String) {
            connections.remove(endpointId)?.onRemoteDisconnected()
            earlyStreams.remove(endpointId)
            pendingOutgoing[endpointId]?.completeExceptionally(SyncroException("Nearby device disconnected"))
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.STREAM) return
            val stream = payload.asStream()?.asInputStream() ?: return
            val connection = connections[endpointId]
            if (connection != null) connection.attachIncoming(stream) else earlyStreams[endpointId] = stream
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            connections[endpointId]?.onTransferUpdate(update)
        }
    }

    private fun publish() {
        val cutoff = System.currentTimeMillis() - 5 * 60_000
        _discovered.value = endpoints.values.filter { it.lastSeen > cutoff }.sortedBy { it.device.name }
    }

    companion object {
        const val ID = "nearby"
        private const val SERVICE_ID = "com.syncro.share.v1"
        private val STRATEGY = Strategy.P2P_POINT_TO_POINT

        fun isSupported(context: Context): Boolean =
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

        fun requiredPermissions(): List<String> = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        fun hasPermissions(context: Context): Boolean = requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        /** Endpoint names carry `SY1|<type>|<device id>|<name>` so peers merge with LAN discoveries. */
        private fun encodeName(self: DeviceInfo): String =
            "SY1|${self.type.name.first()}|${self.id}|${self.name.take(24)}"

        private fun decodeName(value: String): DeviceInfo? {
            val parts = value.split('|', limit = 4)
            if (parts.size != 4 || parts[0] != "SY1" || parts[2].length != 16) return null
            val type = DeviceType.entries.firstOrNull { it.name.first().toString() == parts[1] } ?: DeviceType.PHONE
            return DeviceInfo(parts[2], parts[3].ifBlank { "Nearby device" }, type)
        }
    }
}

/** Full-duplex byte pipe emulated with one Nearby stream payload in each direction. */
internal class NearbyConnection(
    private val endpointId: String,
    private val client: ConnectionsClient,
) : Connection {
    override val transportLabel: String = "Nearby"
    override val remoteHost: String? = null

    private val pipe = ParcelFileDescriptor.createPipe()
    private val outgoingDone = CountDownLatch(1)
    private val incomingReady = CountDownLatch(1)
    @Volatile private var incomingStream: InputStream? = null
    @Volatile private var outgoingPayloadId = 0L
    @Volatile private var closed = false

    override val output: OutputStream = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])

    override val input: InputStream = object : InputStream() {
        private fun stream(): InputStream {
            incomingReady.await()
            return incomingStream ?: throw IOException("Nearby connection closed")
        }

        override fun read(): Int = stream().read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = stream().read(b, off, len)
        override fun available(): Int = if (incomingReady.count == 0L) incomingStream?.available() ?: 0 else 0
        override fun close() = this@NearbyConnection.close()
    }

    fun openOutgoing() {
        val payload = Payload.fromStream(pipe[0])
        outgoingPayloadId = payload.id
        client.sendPayload(endpointId, payload)
    }

    fun attachIncoming(stream: InputStream) {
        if (closed) {
            runCatching { stream.close() }
            return
        }
        incomingStream = stream
        incomingReady.countDown()
    }

    fun onTransferUpdate(update: PayloadTransferUpdate) {
        if (update.payloadId == outgoingPayloadId && update.status != PayloadTransferUpdate.Status.IN_PROGRESS) {
            outgoingDone.countDown()
        }
    }

    fun onRemoteDisconnected() {
        closed = true
        outgoingDone.countDown()
        incomingReady.countDown()
        runCatching { output.close() }
        runCatching { incomingStream?.close() }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { output.close() }
        incomingReady.countDown()
        // Let Nearby drain what is still buffered in the pipe before tearing the link down.
        thread(isDaemon = true, name = "syncro-nearby-close") {
            outgoingDone.await(3, TimeUnit.SECONDS)
            client.disconnectFromEndpoint(endpointId)
            runCatching { incomingStream?.close() }
            runCatching { pipe[0].close() }
        }
    }
}
