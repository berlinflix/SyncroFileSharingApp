package com.example.syncro

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.net.*
import kotlin.concurrent.thread

object LocalDiscovery {

    private const val DISCOVERY_PORT = 8987
    private const val HEADER = "SYNCRO_HELLO"
    private const val TAG = "SyncroDiscovery"

    @Volatile private var running = false

    // We keep a reference to the lock so we can release it later
    private var multicastLock: WifiManager.MulticastLock? = null

    // 📡 Receiver: Broadcast "I am here"
    fun startBroadcasting() {
        if (running) return
        running = true
        Log.d(TAG, "🟢 startBroadcasting: Starting...")

        thread {
            try {
                val socket = DatagramSocket().apply { broadcast = true }
                val deviceName = Build.MODEL ?: "Unknown Device"
                val message = "$HEADER|$deviceName"
                val data = message.toByteArray()

                while (running) {
                    try {
                        val packet = DatagramPacket(
                            data,
                            data.size,
                            InetAddress.getByName("255.255.255.255"),
                            DISCOVERY_PORT
                        )
                        socket.send(packet)
                        Log.d(TAG, "📡 Packet sent! (Size: ${data.size})")
                    } catch (e: Exception) {
                        Log.e(TAG, "🔴 Packet send failed", e)
                    }
                    Thread.sleep(1500)
                }
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "🔴 Broadcast critical error", e)
            }
        }
    }

    fun stopBroadcasting() {
        running = false
    }

    // 🔍 Sender: Listen (NOW WITH MULTICAST LOCK)
    fun startListening(context: Context) {
        if (running) return
        running = true
        Log.d(TAG, "🔵 startListening: Acquiring Multicast Lock...")

        // 1. Acquire Multicast Lock to allow receiving broadcasts
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("SyncroLock").apply {
            setReferenceCounted(true)
            acquire()
        }
        Log.d(TAG, "🔓 Multicast Lock Acquired!")

        thread {
            try {
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                }
                val buffer = ByteArray(1024)

                Log.d(TAG, "👂 Socket opened. Listening...")

                while (running) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet) // Blocks until packet received

                    val msg = String(packet.data, 0, packet.length)
                    val senderIp = packet.address.hostAddress

                    Log.d(TAG, "📨 Packet received from $senderIp: $msg")

                    if (msg.startsWith(HEADER)) {
                        val parts = msg.split("|")
                        val name = if (parts.size > 1) parts[1] else "Unknown"
                        if (senderIp != null) {
                            TransferState.addDevice(DiscoveredDevice(senderIp, name))
                        }
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "🔴 Listening error", e)
            }
        }
    }

    fun stopListening() {
        running = false
        // Release the lock to save battery
        multicastLock?.let {
            if (it.isHeld) it.release()
        }
        Log.d(TAG, "🔒 Multicast Lock Released & Stopped.")
    }
}