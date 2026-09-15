package com.syncro.core.store

import com.syncro.core.DeviceInfo
import com.syncro.core.DeviceType
import com.syncro.core.transfer.Direction
import com.syncro.core.transfer.TransferInfo
import com.syncro.core.transfer.TransferPhase
import com.syncro.core.util.toHex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object JsonFiles {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun <T> read(file: File?, serializer: KSerializer<T>): T? {
        if (file == null || !file.isFile) return null
        return runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull()
    }

    fun <T> write(file: File?, serializer: KSerializer<T>, value: T) {
        if (file == null) return
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json.encodeToString(serializer, value))
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

@Serializable
data class KnownDevice(
    val id: String,
    val name: String,
    val type: String,
    val fingerprint: String,
    val trusted: Boolean = false,
    val hosts: List<String> = emptyList(),
    val lastSeen: Long = 0L,
) {
    val deviceType: DeviceType get() = DeviceType.parse(type)
    val info: DeviceInfo get() = DeviceInfo(id, name, deviceType)
}

/** Devices this one has completed a handshake with: names, verified key fingerprints, trust and last addresses. */
class KnownDevices(private val file: File?) {
    private val lock = Any()
    private val serializer = ListSerializer(KnownDevice.serializer())
    private val _devices = MutableStateFlow(JsonFiles.read(file, serializer).orEmpty())
    val devices: StateFlow<List<KnownDevice>> = _devices.asStateFlow()

    fun get(id: String): KnownDevice? = _devices.value.firstOrNull { it.id == id }

    fun isTrusted(id: String, fingerprint: ByteArray): Boolean {
        val device = get(id) ?: return false
        return device.trusted && device.fingerprint == fingerprint.toHex()
    }

    fun isTrusted(id: String?): Boolean = id != null && get(id)?.trusted == true

    fun recordSeen(device: DeviceInfo, fingerprint: ByteArray, host: String?) = mutate { list ->
        val fp = fingerprint.toHex()
        val existing = list.firstOrNull { it.id == device.id }
        val hosts = (listOfNotNull(host) + existing?.hosts.orEmpty()).distinct().take(4)
        val updated = KnownDevice(
            id = device.id,
            name = device.name,
            type = device.type.name,
            fingerprint = fp,
            trusted = existing?.trusted == true && existing.fingerprint == fp,
            hosts = hosts,
            lastSeen = System.currentTimeMillis(),
        )
        (list.filter { it.id != device.id } + updated).sortedByDescending { it.lastSeen }.take(200)
    }

    fun setTrusted(id: String, trusted: Boolean) = mutate { list ->
        list.map { if (it.id == id) it.copy(trusted = trusted) else it }
    }

    fun remove(id: String) = mutate { list -> list.filter { it.id != id } }

    /** Hosts worth probing directly when broadcasts are blocked. */
    fun recentHosts(maxAgeMs: Long = 30L * 24 * 3600 * 1000): List<String> {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        return _devices.value.filter { it.lastSeen >= cutoff }.flatMap { it.hosts }.distinct().take(64)
    }

    private fun mutate(block: (List<KnownDevice>) -> List<KnownDevice>) {
        synchronized(lock) {
            val next = block(_devices.value)
            _devices.value = next
            JsonFiles.write(file, serializer, next)
        }
    }
}

@Serializable
data class HistoryItem(
    val name: String,
    val size: Long,
    val mime: String? = null,
    val location: String? = null,
    val text: String? = null,
)

@Serializable
data class HistoryEntry(
    val id: String,
    val direction: String,
    val peerName: String,
    val peerType: String,
    val items: List<HistoryItem>,
    val totalBytes: Long,
    val status: String,
    val message: String? = null,
    val time: Long,
    val durationMs: Long = 0,
) {
    val isSend: Boolean get() = direction == Direction.SEND.name
    val isSuccess: Boolean get() = status == TransferPhase.COMPLETED.name
}

class HistoryStore(private val file: File?, private val maxEntries: Int = 300) {
    private val lock = Any()
    private val serializer = ListSerializer(HistoryEntry.serializer())
    private val _entries = MutableStateFlow(JsonFiles.read(file, serializer).orEmpty())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    fun add(transfer: TransferInfo) = mutate { list ->
        (listOf(transfer.toHistoryEntry()) + list.filter { it.id != transfer.id }).take(maxEntries)
    }

    fun remove(id: String) = mutate { list -> list.filter { it.id != id } }

    fun clear() = mutate { emptyList() }

    private fun mutate(block: (List<HistoryEntry>) -> List<HistoryEntry>) {
        synchronized(lock) {
            val next = block(_entries.value)
            _entries.value = next
            JsonFiles.write(file, serializer, next)
        }
    }
}

fun TransferInfo.toHistoryEntry(): HistoryEntry {
    val historyItems = if (direction == Direction.RECEIVE) {
        received.sortedBy { it.index }.map { HistoryItem(it.name, it.size, it.mimeType, it.location, it.text) }
            .ifEmpty { items.map { HistoryItem(it.name, it.size, it.mimeType, null, null) } }
    } else {
        items.map { HistoryItem(it.name, it.size, it.mimeType, null, it.text) }
    }
    return HistoryEntry(
        id = id,
        direction = direction.name,
        peerName = peerName,
        peerType = (peer?.type ?: DeviceType.PHONE).name,
        items = historyItems,
        totalBytes = totalBytes,
        status = phase.name,
        message = message,
        time = startedAt,
        durationMs = (finishedAt ?: System.currentTimeMillis()) - startedAt,
    )
}
