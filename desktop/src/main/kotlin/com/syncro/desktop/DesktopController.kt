package com.syncro.desktop

import com.syncro.core.engine.Peer
import com.syncro.core.link.ConnectLink
import com.syncro.core.transfer.Decision
import com.syncro.core.transfer.FileSendItem
import com.syncro.core.transfer.SendItem
import com.syncro.core.transfer.TextSendItem
import com.syncro.core.util.FileNames
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class Page { SHARE, ACTIVITY, DEVICES, SETTINGS }

data class DesktopItem(val key: String, val item: SendItem, val file: File?) {
    val name: String get() = item.name
    val size: Long get() = item.size
    val isText: Boolean get() = item is TextSendItem
}

class DesktopController(val graph: DesktopGraph) {
    val engine = graph.engine

    private val _page = MutableStateFlow(Page.SHARE)
    val page: StateFlow<Page> = _page.asStateFlow()

    private val _selection = MutableStateFlow<List<DesktopItem>>(emptyList())
    val selection: StateFlow<List<DesktopItem>> = _selection.asStateFlow()

    private val _addresses = MutableStateFlow<List<String>>(emptyList())
    val addresses: StateFlow<List<String>> = _addresses.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val retryTargets = mutableMapOf<String, Pair<Peer, List<DesktopItem>>>()

    init {
        graph.scope.launch {
            while (isActive) {
                _addresses.value = runCatching { graph.network.shareableAddresses() }.getOrDefault(emptyList())
                delay(4_000)
            }
        }
        graph.scope.launch {
            _page.collect { engine.setScanning(it == Page.SHARE) }
        }
    }

    fun open(page: Page) {
        _page.value = page
    }

    fun addFiles(files: List<File>) {
        val expanded = files.flatMap { file ->
            if (file.isDirectory) file.walkTopDown().filter { it.isFile && !it.isHidden }.take(5_000).toList() else listOf(file)
        }.filter { it.isFile && it.canRead() }
        if (expanded.isEmpty()) {
            flash("Nothing readable to add")
            return
        }
        val items = expanded.map { DesktopItem(it.absolutePath, FileSendItem(it, FileNames.mimeFromName(it.name)), it) }
        _selection.update { (it + items).distinctBy { item -> item.key } }
    }

    fun addText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _selection.update { it + DesktopItem("text:${System.nanoTime()}", TextSendItem(trimmed), null) }
    }

    fun remove(key: String) = _selection.update { list -> list.filterNot { it.key == key } }

    fun clearSelection() {
        _selection.value = emptyList()
    }

    fun sendTo(peer: Peer) {
        val items = _selection.value
        if (items.isEmpty()) {
            flash("Add files first")
            return
        }
        val id = engine.send(peer, items.map { it.item })
        retryTargets[id] = peer to items
        _selection.value = emptyList()
    }

    fun sendToAddress(address: String) {
        val items = _selection.value
        if (items.isEmpty()) {
            flash("Add files first")
            return
        }
        val link = ConnectLink.parse(address)
        if (link != null) engine.send(link, items.map { it.item }) else engine.sendToAddress(address.trim(), items.map { it.item })
        _selection.value = emptyList()
    }

    fun canRetry(id: String) = retryTargets.containsKey(id)

    fun retry(id: String) {
        val (peer, items) = retryTargets[id] ?: return
        val latest = engine.peers.value.firstOrNull { it.id == peer.id } ?: peer
        engine.dismiss(id)
        val newId = engine.send(latest, items.map { it.item })
        retryTargets[newId] = latest to items
    }

    fun respond(id: String, decision: Decision) = engine.respond(id, decision)

    fun cancel(id: String) = engine.cancel(id)

    fun dismiss(id: String) = engine.dismiss(id)

    fun connectLink(): ConnectLink? {
        val port = graph.lan.port.value ?: return null
        val hosts = _addresses.value.take(4).ifEmpty { return null }
        val self = engine.self
        return ConnectLink(self.id, self.name, self.type, hosts, port)
    }

    fun flash(text: String) {
        _message.value = text
        graph.scope.launch {
            delay(3_500)
            if (_message.value == text) _message.value = null
        }
    }
}
