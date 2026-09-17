package com.syncro.core.lan

import com.syncro.core.DeviceInfo
import com.syncro.core.DeviceType
import com.syncro.core.Syncro
import com.syncro.core.sanitizeDeviceName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@Serializable
internal data class Beacon(
    val p: String = Syncro.PROTOCOL,
    val v: Int = Syncro.PROTOCOL_VERSION,
    /** a = announce, q = query (please announce), b = bye */
    val o: String,
    val i: String,
    val n: String,
    val t: String,
    val port: Int = 0,
)

/**
 * UDP presence protocol that survives the networks where a single 255.255.255.255 broadcast fails:
 *
 * - one socket per local IPv4 network, so beacons leave through every interface (Wi-Fi, hotspot, Ethernet)
 *   and can be bound to that network on Android;
 * - announces go to the subnet broadcast address, the limited broadcast address and a multicast group;
 * - scanners send queries that visible devices answer with unicast replies;
 * - when broadcasts are filtered (common on routers and some hotspots) scanners probe every host in
 *   small subnets and every address a device was last seen at, which only needs unicast to work.
 */
internal class LanDiscovery(
    private val network: NetworkPlatform,
    private val discoveryPort: Int,
    private val self: () -> DeviceInfo,
    private val serverPort: () -> Int?,
    private val probeHosts: () -> Collection<String>,
    private val onPeer: (DeviceInfo, InetAddress, Int) -> Unit,
    private val onBye: (String, InetAddress) -> Unit,
    private val log: (String, Throwable?) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val group: InetAddress = InetAddress.getByName(Syncro.MULTICAST_GROUP)
    private val limitedBroadcast: InetAddress = InetAddress.getByName("255.255.255.255")

    @Volatile private var advertising = false
    @Volatile private var scanning = false
    @Volatile private var stopped = false

    private val wake = LinkedBlockingQueue<Unit>()
    private var listenSocket: MulticastSocket? = null
    private val senders = ConcurrentHashMap<String, Sender>()
    private var multicastLock: AutoCloseable? = null
    private var worker: Thread? = null

    private class Sender(val net: LocalNet, val socket: MulticastSocket)

    fun start() {
        if (worker != null) return
        worker = thread(isDaemon = true, name = "syncro-discovery") { loop() }
    }

    fun stop() {
        stopped = true
        wake.offer(Unit)
    }

    fun setAdvertising(enabled: Boolean) {
        if (advertising == enabled) return
        if (!enabled) sendAll(beacon("b"))
        advertising = enabled
        wake.offer(Unit)
    }

    fun setScanning(enabled: Boolean) {
        if (scanning == enabled) return
        scanning = enabled
        scanStartedAt = System.currentTimeMillis()
        lastQuery = 0
        lastSweep = 0
        wake.offer(Unit)
    }

    @Volatile private var scanStartedAt = 0L
    @Volatile private var lastQuery = 0L
    @Volatile private var lastSweep = 0L

    private fun loop() {
        var lastRefresh = 0L
        var lastAnnounce = 0L
        var wasActive = false
        while (!stopped) {
            val active = advertising || scanning
            val now = System.currentTimeMillis()
            if (active) {
                if (!wasActive) {
                    openSockets()
                    lastRefresh = now
                    lastAnnounce = 0
                } else if (now - lastRefresh > 5_000) {
                    refreshSenders()
                    lastRefresh = now
                }
                if (advertising && now - lastAnnounce >= 4_000) {
                    sendAll(beacon("a"))
                    lastAnnounce = now
                }
                if (scanning) {
                    if (now - lastQuery >= 2_000) {
                        sendAll(beacon("q"))
                        lastQuery = now
                    }
                    val sinceStart = now - scanStartedAt
                    if (sinceStart >= 1_200 && now - lastSweep >= 9_000) {
                        sweep()
                        lastSweep = now
                    }
                }
            } else if (wasActive) {
                closeSockets()
            }
            wasActive = active
            wake.poll(if (active) 250L else 2_000L, TimeUnit.MILLISECONDS)
        }
        closeSockets()
    }

    private fun openSockets() {
        multicastLock = runCatching { network.acquireMulticastLock() }.getOrNull()
        listenSocket = try {
            MulticastSocket(null as java.net.SocketAddress?).apply {
                reuseAddress = true
                bind(InetSocketAddress(discoveryPort))
            }.also { socket -> startReceiver(socket, "listen") }
        } catch (e: Exception) {
            log("Discovery port $discoveryPort unavailable; relying on unicast replies", e)
            null
        }
        refreshSenders()
    }

    private fun closeSockets() {
        listenSocket?.close()
        listenSocket = null
        senders.values.forEach { it.socket.close() }
        senders.clear()
        runCatching { multicastLock?.close() }
        multicastLock = null
    }

    private fun refreshSenders() {
        val nets = network.localNets()
        val keys = nets.map { it.address.hostAddress }.toSet()
        senders.keys.filter { it !in keys }.forEach { key -> senders.remove(key)?.socket?.close() }
        for (net in nets) {
            val key = net.address.hostAddress
            if (senders.containsKey(key)) continue
            try {
                val socket = MulticastSocket(null as java.net.SocketAddress?)
                network.prepareUdp(socket, net)
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(net.address, 0))
                socket.broadcast = true
                runCatching { socket.timeToLive = 1 }
                net.networkInterface?.let { ni -> runCatching { socket.networkInterface = ni } }
                senders[key] = Sender(net, socket)
                startReceiver(socket, key)
                listenSocket?.let { listen ->
                    net.networkInterface?.let { ni -> runCatching { listen.joinGroup(InetSocketAddress(group, 0), ni) } }
                }
            } catch (e: Exception) {
                log("Cannot open discovery socket on ${net.interfaceName}", e)
            }
        }
    }

    private fun startReceiver(socket: DatagramSocket, label: String) {
        thread(isDaemon = true, name = "syncro-discovery-rx-$label") {
            val buffer = ByteArray(2048)
            while (!socket.isClosed) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    handle(packet, socket)
                } catch (_: SocketException) {
                    break
                } catch (e: Exception) {
                    if (socket.isClosed) break
                    log("Discovery receive error", e)
                }
            }
        }
    }

    private fun handle(packet: DatagramPacket, receivedOn: DatagramSocket) {
        val beacon = runCatching {
            json.decodeFromString(Beacon.serializer(), String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
        }.getOrNull() ?: return
        if (beacon.p != Syncro.PROTOCOL || beacon.v != Syncro.PROTOCOL_VERSION) return
        val me = self()
        if (beacon.i == me.id || !isValidId(beacon.i)) return
        val from = packet.address?.let(::normalize) ?: return
        when (beacon.o) {
            "b" -> onBye(beacon.i, from)
            "a", "q" -> {
                if (beacon.port in 1..65535) {
                    onPeer(DeviceInfo(beacon.i, sanitizeDeviceName(beacon.n), DeviceType.parse(beacon.t)), from, beacon.port)
                }
                if (beacon.o == "q" && advertising && serverPort() != null) {
                    val reply = beacon("a")
                    // Answer from the socket the query arrived on so the 5-tuple matches: NATs and stateful
                    // firewalls (Windows Firewall, emulator/VM networking) only pass replies from that port.
                    send(receivedOn, reply, from, packet.port)
                    // Also answer through the socket bound to the matching network (Android routing), and to the
                    // well-known port in case the querier's ephemeral socket is gone.
                    sendTo(reply, from, packet.port)
                    if (packet.port != discoveryPort) sendTo(reply, from, discoveryPort)
                }
            }
        }
    }

    private fun beacon(op: String): ByteArray {
        val me = self()
        val port = if (advertising) serverPort() ?: 0 else 0
        return json.encodeToString(Beacon.serializer(), Beacon(o = op, i = me.id, n = me.name, t = me.type.name, port = port))
            .toByteArray(Charsets.UTF_8)
    }

    private fun sendAll(payload: ByteArray) {
        val all = senders.values.toList()
        for (sender in all) {
            sender.net.broadcast?.let { send(sender.socket, payload, it, discoveryPort) }
            send(sender.socket, payload, limitedBroadcast, discoveryPort)
            send(sender.socket, payload, group, discoveryPort)
        }
        if (all.isEmpty()) {
            listenSocket?.let {
                send(it, payload, limitedBroadcast, discoveryPort)
                send(it, payload, group, discoveryPort)
            }
        }
    }

    private fun sendTo(payload: ByteArray, target: InetAddress, port: Int) {
        val socket = senders.values.firstOrNull { it.net.contains(target) }?.socket
            ?: senders.values.firstOrNull()?.socket
            ?: listenSocket
            ?: return
        send(socket, payload, target, port)
    }

    private fun sweep() {
        val query = beacon("q")
        val selfAddresses = senders.values.map { it.net.address }.toSet()
        var sent = 0
        for (host in probeHosts()) {
            val address = runCatching { InetAddress.getByName(host) }.getOrNull() as? Inet4Address ?: continue
            sendTo(query, address, discoveryPort)
        }
        for (sender in senders.values.toList()) {
            val net = sender.net
            if (net.prefixLength !in 22..30 || net.preference >= 40) continue
            val mask = -1 shl (32 - net.prefixLength)
            val base = LocalNet.toInt(net.address) and mask
            val hostCount = (1 shl (32 - net.prefixLength)) - 2
            for (i in 1..hostCount) {
                if (!scanning) return
                val target = LocalNet.fromInt(base + i)
                if (target in selfAddresses) continue
                send(sender.socket, query, target, discoveryPort)
                if (++sent % 64 == 0) Thread.sleep(2)
            }
        }
    }

    private fun send(socket: DatagramSocket, payload: ByteArray, target: InetAddress, port: Int) {
        try {
            socket.send(DatagramPacket(payload, payload.size, target, port))
        } catch (_: Exception) {
            // Unreachable networks, filtered broadcasts and closed sockets are all expected here.
        }
    }

    /** Dual-stack sockets report IPv4 peers as ::ffff:a.b.c.d (sometimes with a scope id); use plain IPv4. */
    private fun normalize(address: InetAddress): InetAddress {
        if (address !is java.net.Inet6Address) return address
        val b = address.address
        val mapped = (0 until 10).all { b[it] == 0.toByte() } && b[10] == 0xff.toByte() && b[11] == 0xff.toByte()
        return if (mapped) InetAddress.getByAddress(b.copyOfRange(12, 16)) else address
    }

    private fun isValidId(id: String) = id.length == 16 && id.all { it in '0'..'9' || it in 'a'..'f' }
}
