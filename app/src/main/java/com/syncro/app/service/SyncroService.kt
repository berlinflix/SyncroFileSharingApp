package com.syncro.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.syncro.app.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while Syncro is visible or transferring, shows live progress and holds a
 * high-performance Wi-Fi lock plus a partial wake lock during transfers so the radio never naps.
 */
class SyncroService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val graph = graph
        val initial = graph.notifications.statusNotification(graph.engine.transfers.value, graph.engine.visible.value, graph.settings.deviceName.value)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        try {
            ServiceCompat.startForeground(this, Notifications.STATUS_ID, initial, type)
        } catch (e: Exception) {
            Log.w("Syncro", "Could not enter foreground", e)
            stopSelf()
            return
        }

        @OptIn(FlowPreview::class)
        scope.launch {
            combine(graph.engine.transfers, graph.engine.visible, graph.settings.deviceName) { transfers, visible, name ->
                Triple(transfers, visible, name)
            }.sample(500).collect { (transfers, visible, name) ->
                val active = transfers.any { it.isActive }
                updateLocks(active)
                runCatching {
                    androidx.core.app.NotificationManagerCompat.from(this@SyncroService)
                        .notify(Notifications.STATUS_ID, graph.notifications.statusNotification(transfers, visible, name))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun updateLocks(active: Boolean) {
        if (active) {
            if (wakeLock == null) {
                wakeLock = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "syncro:transfer")
                    .apply { acquire(6 * 60 * 60 * 1000L) }
            }
            if (wifiLock == null) {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = applicationContext.getSystemService(WifiManager::class.java)
                    ?.createWifiLock(mode, "syncro:transfer")
                    ?.apply {
                        setReferenceCounted(false)
                        acquire()
                    }
            }
        } else {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            wifiLock?.let { if (it.isHeld) it.release() }
            wifiLock = null
        }
    }

    override fun onDestroy() {
        updateLocks(false)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, SyncroService::class.java))
            } catch (e: Exception) {
                // Starting from the background is not allowed on Android 12+; the next foreground visit retries.
                Log.w("Syncro", "Service start deferred", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SyncroService::class.java))
        }
    }
}
