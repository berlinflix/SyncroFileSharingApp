package com.syncro.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.syncro.core.lan.LocalNet
import com.syncro.core.lan.NetworkPlatform
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket

/**
 * Android needs two things the desktop doesn't:
 * - sockets bound to the Wi-Fi network that owns the peer's subnet. When Wi-Fi has no internet (e.g. a
 *   phone hotspot with data off) Android makes mobile data the default network and unbound sockets
 *   would never reach the local peer;
 * - a multicast lock, without which the Wi-Fi driver drops broadcast and multicast packets.
 */
class AndroidNetworkPlatform(context: Context) : NetworkPlatform() {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    override fun prepareTcp(socket: Socket, target: InetAddress) {
        networkFor(target)?.let { runCatching { it.bindSocket(socket) } }
    }

    override fun prepareUdp(socket: DatagramSocket, net: LocalNet) {
        networkFor(net.address)?.let { runCatching { it.bindSocket(socket) } }
    }

    override fun acquireMulticastLock(): AutoCloseable? {
        val lock = wifi?.createMulticastLock("syncro-discovery") ?: return null
        lock.setReferenceCounted(false)
        lock.acquire()
        return AutoCloseable { if (lock.isHeld) lock.release() }
    }

    @Suppress("DEPRECATION")
    private fun networkFor(address: InetAddress): Network? {
        if (address !is Inet4Address) return null
        val cm = connectivity ?: return null
        return cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return@firstOrNull false
            val links = cm.getLinkProperties(network)?.linkAddresses ?: return@firstOrNull false
            links.any { link ->
                val local = link.address as? Inet4Address ?: return@any false
                LocalNet("", "", local, link.prefixLength, null, null).contains(address)
            }
        }
    }

    /** Short description of the current local network for the UI. */
    fun describe(): NetworkStatus {
        val nets = localNets().filter { it.preference < 40 }
        val primary = nets.firstOrNull() ?: return NetworkStatus(null, null)
        val name = primary.interfaceName.lowercase()
        val kind = when {
            name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") || (name.startsWith("wlan") && nets.count { it.interfaceName.startsWith("wlan") } > 1 && primary.address.hostAddress.endsWith(".1")) -> "Hotspot"
            name.startsWith("wlan") -> "Wi-Fi"
            name.startsWith("eth") -> "Ethernet"
            name.startsWith("p2p") -> "Wi-Fi Direct"
            else -> "Network"
        }
        return NetworkStatus(kind, primary.address.hostAddress)
    }
}

data class NetworkStatus(val kind: String?, val address: String?) {
    val connected: Boolean get() = address != null
}
