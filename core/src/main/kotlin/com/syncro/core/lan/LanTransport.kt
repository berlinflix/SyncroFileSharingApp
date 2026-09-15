package com.syncro.core.lan

import com.syncro.core.DeviceInfo
import com.syncro.core.Syncro
import com.syncro.core.SyncroException
import com.syncro.core.transport.Connection
import com.syncro.core.transport.Transport
import com.syncro.core.transport.TransportHost
import com.syncro.core.transport.TransportPeer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class SocketConnection(private val socket: Socket, override val transportLabel: String) : Connection {
    override val input: InputStream = socket.getInputStream()
    override val output: OutputStream = socket.getOutputStream()
    override val remoteHost: String? = socket.inetAddress?.hostAddress

    override fun close() {
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }
}

/** Direct TCP over whatever IP network both devices share: Wi-Fi, a phone hotspot, Ethernet or a VPN. */
class LanTransport(
    private val network: NetworkPlatform = NetworkPlatform.Default,
    private val preferredPort: Int = Syncro.TRANSFER_PORT,
    private val discoveryPort: Int = Syncro.DISCOVERY_PORT,
    private val probeHosts: () -> Collection<String> = { emptyList() },
) : Transport {
    override val id: String = ID
    override val label: String = "Wi-Fi"
    override val priority: Int = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val peerMap = ConcurrentHashMap<String, TransportPeer>()
    private val _discovered = MutableStateFlow<List<TransportPeer>>(emptyList())
    override val discovered: StateFlow<List<TransportPeer>> = _discovered.asStateFlow()
    override val available: StateFlow<Boolean> = MutableStateFlow(true)

    private val _port = MutableStateFlow<Int?>(null)

    /** Port the transfer server is listening on while advertising. */
    val port: StateFlow<Int?> = _port.asStateFlow()

    private lateinit var host: TransportHost
    private var discovery: LanDiscovery? = null
    private var server: ServerSocket? = null
    private var pruneJob: Job? = null
    private val serverLock = Any()

    override fun start(host: TransportHost) {
        this.host = host
        val discovery = LanDiscovery(
            network = network,
            discoveryPort = discoveryPort,
            self = { host.self },
            serverPort = { _port.value },
            probeHosts = probeHosts,
            onPeer = ::onPeerSeen,
            onBye = { id, address -> removePeer(id, address) },
            log = host::log,
        )
        this.discovery = discovery
        discovery.start()
        pruneJob = scope.launch {
            while (isActive) {
                delay(1_000)
                prune()
            }
        }
    }

    override fun setAdvertising(enabled: Boolean) {
        if (enabled) openServer() else closeServer()
        discovery?.setAdvertising(enabled)
    }

    override fun setScanning(enabled: Boolean) {
        discovery?.setScanning(enabled)
    }

    override fun stop() {
        setAdvertising(false)
        setScanning(false)
        discovery?.stop()
        pruneJob?.cancel()
        scope.cancel()
    }

    /** Races connection attempts to every candidate address (250 ms apart) and keeps the first that answers. */
    override suspend fun connect(addresses: List<String>): Connection {
        val targets = addresses.mapNotNull(::parseAddress).distinct()
        if (targets.isEmpty()) throw SyncroException("No network address for this device")
        val winner = CompletableDeferred<Socket>()
        val sockets = mutableListOf<Socket>()
        var failures = 0
        val lock = Any()
        val jobs = targets.mapIndexed { index, target ->
            scope.launch {
                delay(index * 250L)
                if (winner.isCompleted) return@launch
                val socket = Socket()
                synchronized(lock) { sockets += socket }
                try {
                    val address = InetAddress.getByName(target.hostString)
                    network.prepareTcp(socket, address)
                    socket.tcpNoDelay = true
                    socket.keepAlive = true
                    socket.connect(InetSocketAddress(address, target.port), CONNECT_TIMEOUT_MS)
                    if (!winner.complete(socket)) socket.close()
                } catch (e: Exception) {
                    socket.close()
                    synchronized(lock) {
                        failures++
                        if (failures == targets.size) {
                            winner.completeExceptionally(SyncroException("Couldn't reach the device on the network", e))
                        }
                    }
                }
            }
        }
        try {
            val socket = winner.await()
            return SocketConnection(socket, label)
        } finally {
            jobs.forEach { it.cancel() }
            val chosen = if (winner.isCompleted && !winner.isCancelled) runCatching { winner.getCompletedOrNull() }.getOrNull() else null
            synchronized(lock) { sockets.filter { it !== chosen }.forEach { runCatching { it.close() } } }
        }
    }

    private fun CompletableDeferred<Socket>.getCompletedOrNull(): Socket? =
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        if (getCompletionExceptionOrNull() == null) getCompleted() else null

    private fun openServer() {
        synchronized(serverLock) {
            if (server != null) return
            val socket = ServerSocket().apply { reuseAddress = true }
            try {
                socket.bind(InetSocketAddress(preferredPort), 64)
            } catch (e: IOException) {
                socket.close()
                return openEphemeralServer()
            }
            startServer(socket)
        }
    }

    private fun openEphemeralServer() {
        val socket = ServerSocket()
        try {
            socket.bind(InetSocketAddress(0), 64)
            startServer(socket)
        } catch (e: IOException) {
            socket.close()
            host.log("Unable to open transfer server", e)
        }
    }

    private fun startServer(socket: ServerSocket) {
        server = socket
        _port.value = socket.localPort
        thread(isDaemon = true, name = "syncro-lan-accept") {
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    client.tcpNoDelay = true
                    client.keepAlive = true
                    host.onIncomingConnection(SocketConnection(client, label))
                } catch (e: IOException) {
                    if (socket.isClosed) break
                    host.log("Accept failed", e)
                }
            }
        }
    }

    private fun closeServer() {
        synchronized(serverLock) {
            server?.close()
            server = null
            _port.value = null
        }
    }

    private fun onPeerSeen(device: DeviceInfo, address: InetAddress, port: Int) {
        val key = "${device.id}@${address.hostAddress}"
        val peer = TransportPeer(device, "${address.hostAddress}:$port", System.currentTimeMillis())
        val previous = peerMap.put(key, peer)
        if (previous == null || previous.device != device || previous.address != peer.address) publish()
        else if (peer.lastSeen - previous.lastSeen > 1_000) publish()
    }

    private fun removePeer(id: String, address: InetAddress) {
        if (peerMap.remove("$id@${address.hostAddress}") != null) publish()
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - PEER_TTL_MS
        if (peerMap.entries.removeIf { it.value.lastSeen < cutoff }) publish()
    }

    private fun publish() {
        _discovered.value = peerMap.values.sortedBy { it.device.name }
    }

    companion object {
        const val ID = "lan"
        private const val CONNECT_TIMEOUT_MS = 4_000
        private const val PEER_TTL_MS = 14_000L

        fun parseAddress(value: String): InetSocketAddress? {
            val text = value.trim()
            if (text.isEmpty()) return null
            val (hostPart, portPart) = when {
                text.startsWith("[") -> text.substringAfter('[').substringBefore(']') to text.substringAfter("]:", "")
                text.count { it == ':' } == 1 -> text.substringBefore(':') to text.substringAfter(':')
                else -> text to ""
            }
            val port = portPart.toIntOrNull()?.takeIf { it in 1..65535 } ?: Syncro.TRANSFER_PORT
            if (hostPart.isBlank()) return null
            return InetSocketAddress.createUnresolved(hostPart, port)
        }
    }
}
