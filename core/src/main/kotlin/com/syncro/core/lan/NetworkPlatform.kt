package com.syncro.core.lan

import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket

/** An IPv4 address on a local interface together with its subnet. */
data class LocalNet(
    val interfaceName: String,
    val displayName: String,
    val address: Inet4Address,
    val prefixLength: Int,
    val broadcast: Inet4Address?,
    val networkInterface: NetworkInterface?,
) {
    fun contains(other: InetAddress): Boolean {
        if (other !is Inet4Address || prefixLength !in 1..32) return false
        val mask = if (prefixLength == 32) -1 else (-1 shl (32 - prefixLength))
        return (toInt(address) and mask) == (toInt(other) and mask)
    }

    /** Heuristic: real Wi-Fi/Ethernet/hotspot adapters first, VPN and virtual adapters last. */
    val preference: Int
        get() {
            val label = "$interfaceName $displayName".lowercase()
            val name = interfaceName.lowercase()
            return when {
                address.isLinkLocalAddress -> 50
                VIRTUAL_HINTS.any { it in label } -> 40
                name.startsWith("wlan") || label.contains("wi-fi") || label.contains("wifi") || label.contains("wireless") -> 0
                name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> 5
                name.startsWith("eth") || name.startsWith("en") || label.contains("ethernet") -> 10
                else -> 20
            }
        }

    companion object {
        private val VIRTUAL_HINTS = listOf(
            "virtual", "vmware", "vbox", "virtualbox", "hyper-v", "vethernet", "wsl", "docker", "tap", "tun",
            "tailscale", "zerotier", "wireguard", "loopback", "bluetooth", "rmnet", "ccmni", "dummy", "p2p-dev",
        )

        fun toInt(address: InetAddress): Int {
            val b = address.address
            return ((b[0].toInt() and 0xff) shl 24) or ((b[1].toInt() and 0xff) shl 16) or
                ((b[2].toInt() and 0xff) shl 8) or (b[3].toInt() and 0xff)
        }

        fun fromInt(value: Int): Inet4Address = InetAddress.getByAddress(
            byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()),
        ) as Inet4Address
    }
}

/**
 * OS integration points for LAN networking. The default works on desktop JVMs; Android overrides it to bind
 * sockets to the Wi-Fi network (otherwise traffic can leak onto mobile data when Wi-Fi has no internet)
 * and to hold a multicast lock.
 */
open class NetworkPlatform {
    open fun localNets(): List<LocalNet> {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
        return interfaces.flatMap { ni ->
            val usable = runCatching { ni.isUp && !ni.isLoopback }.getOrDefault(false)
            if (!usable) return@flatMap emptyList()
            ni.interfaceAddresses.orEmpty().mapNotNull { ia ->
                val address = ia?.address as? Inet4Address ?: return@mapNotNull null
                val prefix = ia.networkPrefixLength.toInt()
                if (prefix !in 8..30) return@mapNotNull null
                val broadcast = (ia.broadcast as? Inet4Address) ?: computeBroadcast(address, prefix)
                LocalNet(ni.name, ni.displayName ?: ni.name, address, prefix, broadcast, ni)
            }
        }.sortedBy { it.preference }
    }

    open fun prepareTcp(socket: Socket, target: InetAddress) {}

    open fun prepareUdp(socket: DatagramSocket, net: LocalNet) {}

    /** Returns a handle to release, or null when the platform needs no lock to receive broadcasts. */
    open fun acquireMulticastLock(): AutoCloseable? = null

    /** Addresses worth putting in a QR code, best first. */
    fun shareableAddresses(): List<String> =
        localNets().filter { !it.address.isLinkLocalAddress }.map { it.address.hostAddress }.distinct()

    companion object {
        val Default = NetworkPlatform()

        fun computeBroadcast(address: Inet4Address, prefix: Int): Inet4Address {
            val mask = if (prefix == 32) -1 else (-1 shl (32 - prefix))
            return LocalNet.fromInt(LocalNet.toInt(address) or mask.inv())
        }
    }
}
