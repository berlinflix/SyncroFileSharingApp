package com.syncro.desktop

import com.syncro.core.DeviceType
import com.syncro.core.crypto.Crypto
import com.syncro.core.crypto.DeviceIdentity
import com.syncro.core.engine.SyncroEngine
import com.syncro.core.lan.LanTransport
import com.syncro.core.lan.NetworkPlatform
import com.syncro.core.store.HistoryStore
import com.syncro.core.store.KnownDevices
import com.syncro.core.transfer.Direction
import com.syncro.core.transfer.DirectoryStorage
import com.syncro.core.transfer.ReceiveStorage
import com.syncro.core.transfer.TransferInfo
import com.syncro.core.transfer.TransferPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetAddress
import kotlin.concurrent.thread

/** Wires the shared engine to desktop storage, settings and notifications. */
class DesktopGraph(val dataDir: File = defaultDataDir()) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = DesktopSettings(File(dataDir, "settings.json"), defaultName = defaultDeviceName())
    val identity: DeviceIdentity = DeviceIdentity.loadOrCreate(File(dataDir, "identity.bin"))
    val knownDevices = KnownDevices(File(dataDir, "devices.json"))
    val history = HistoryStore(File(dataDir, "history.json"))
    val network = NetworkPlatform.Default
    val lan = LanTransport(network, probeHosts = { knownDevices.recentHosts() })

    private val _notifications = MutableSharedFlow<DesktopNotification>(extraBufferCapacity = 16)
    val notifications: SharedFlow<DesktopNotification> = _notifications.asSharedFlow()

    private val storage = DirectoryStorage { File(settings.state.value.downloadDir) }

    val engine = SyncroEngine(identity, knownDevices, object : SyncroEngine.Platform {
        override fun deviceName(): String = settings.state.value.deviceName
        override fun deviceType(): DeviceType = if (isLaptop) DeviceType.LAPTOP else DeviceType.DESKTOP
        override val storage: ReceiveStorage get() = this@DesktopGraph.storage
        override fun autoAcceptTrusted(): Boolean = settings.state.value.autoAcceptTrusted

        override fun onIncomingRequest(transfer: TransferInfo) {
            _notifications.tryEmit(DesktopNotification.Request(transfer))
        }

        override fun onTransferFinished(transfer: TransferInfo) {
            val declinedIncoming = transfer.direction == Direction.RECEIVE && transfer.phase == TransferPhase.REJECTED
            if (!declinedIncoming) history.add(transfer)
            _notifications.tryEmit(DesktopNotification.Finished(transfer))
            if (transfer.direction == Direction.RECEIVE && transfer.phase == TransferPhase.COMPLETED && settings.state.value.openFolderOnReceive) {
                transfer.received.firstOrNull { it.location != null }?.location?.let { Platform.revealFile(File(it)) }
            }
        }

        override fun log(message: String, error: Throwable?) {
            System.err.println("[syncro] $message${error?.let { ": $it" } ?: ""}")
        }
    })

    init {
        // Bring AES-GCM to full JIT speed before the first transfer.
        thread(isDaemon = true, name = "syncro-warmup", priority = Thread.MIN_PRIORITY) { runCatching { Crypto.warmUp() } }
        engine.addTransport(lan)
        scope.launch {
            settings.state.map { it.visible }.distinctUntilChanged().collect { engine.setVisible(it) }
        }
        scope.launch {
            settings.state.map { it.deviceName }.distinctUntilChanged().drop(1).collect { engine.refreshPresence() }
        }
    }

    companion object {
        fun defaultDataDir(): File {
            val os = System.getProperty("os.name").lowercase()
            val base = when {
                os.contains("win") -> System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), "AppData/Roaming")
                os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support")
                else -> System.getenv("XDG_CONFIG_HOME")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
            }
            return File(base, "Syncro").apply { mkdirs() }
        }

        fun defaultDeviceName(): String {
            val env = System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME")
            val host = env ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
            return host?.takeIf { it.isNotBlank() }?.let { name ->
                if (name == name.uppercase()) name.lowercase().replaceFirstChar { it.uppercase() } else name
            } ?: "My PC"
        }

        val isLaptop: Boolean by lazy { Platform.hasBattery() }
    }
}

sealed interface DesktopNotification {
    data class Request(val transfer: TransferInfo) : DesktopNotification
    data class Finished(val transfer: TransferInfo) : DesktopNotification
}
