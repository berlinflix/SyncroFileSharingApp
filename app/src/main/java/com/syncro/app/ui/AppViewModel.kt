package com.syncro.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.syncro.app.data.UriSendItem
import com.syncro.app.graph
import com.syncro.app.net.NetworkStatus
import com.syncro.core.engine.Peer
import com.syncro.core.link.ConnectLink
import com.syncro.core.store.HistoryEntry
import com.syncro.core.store.KnownDevice
import com.syncro.core.transfer.Decision
import com.syncro.core.transfer.Direction
import com.syncro.core.transfer.SendItem
import com.syncro.core.transfer.TextSendItem
import com.syncro.core.transfer.TransferInfo
import com.syncro.core.transfer.TransferPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Send : Screen
    data class Transfer(val id: String) : Screen
    data object ReceiveQr : Screen
    data object History : Screen
    data object Settings : Screen
}

data class SelectedItem(val key: String, val item: SendItem, val uri: Uri?) {
    val name: String get() = item.name
    val size: Long get() = item.size
    val mimeType: String? get() = item.mimeType
    val isText: Boolean get() = item is TextSendItem
}

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data object PickFiles : UiEvent
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = application.graph
    val engine = graph.engine
    val settings = graph.settings
    val identity = graph.identity
    val nearbySupported: Boolean = graph.nearby != null
    val nearbyAvailable: StateFlow<Boolean> = graph.nearby?.available ?: MutableStateFlow(false)
    val lanPort: StateFlow<Int?> = graph.lan.port

    private val backStack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    val screen: StateFlow<Screen> = backStack.map { it.last() }.stateIn(viewModelScope, SharingStarted.Eagerly, Screen.Home)

    private val _selection = MutableStateFlow<List<SelectedItem>>(emptyList())
    val selection: StateFlow<List<SelectedItem>> = _selection.asStateFlow()

    private val _resolving = MutableStateFlow(false)
    val resolving: StateFlow<Boolean> = _resolving.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _network = MutableStateFlow(NetworkStatus(null, null))
    val network: StateFlow<NetworkStatus> = _network.asStateFlow()

    private val _addresses = MutableStateFlow<List<String>>(emptyList())
    val addresses: StateFlow<List<String>> = _addresses.asStateFlow()

    val peers: StateFlow<List<Peer>> = engine.peers
    val transfers: StateFlow<List<TransferInfo>> = engine.transfers
    val history: StateFlow<List<HistoryEntry>> = graph.history.entries
    val knownDevices: StateFlow<List<KnownDevice>> = graph.knownDevices.devices

    val pendingRequests: StateFlow<List<TransferInfo>> = transfers
        .map { list -> list.filter { it.phase == TransferPhase.AWAITING_DECISION } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var pendingLink: ConnectLink? = null
    private val sentItems = mutableMapOf<String, Pair<Peer?, List<SelectedItem>>>()
    private val autoOpened = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            while (isActive) {
                val (status, addresses) = withContext(Dispatchers.IO) { graph.network.describe() to graph.network.shareableAddresses() }
                _network.value = status
                _addresses.value = addresses
                delay(3_000)
            }
        }
        // Jump to the progress screen when a trusted device starts sending without a prompt.
        viewModelScope.launch {
            transfers.collect { list ->
                val current = screen.value
                list.filter { it.direction == Direction.RECEIVE && it.phase == TransferPhase.TRANSFERRING && it.id !in autoOpened }
                    .forEach { transfer ->
                        autoOpened += transfer.id
                        if (current == Screen.Home || current == Screen.ReceiveQr) navigate(Screen.Transfer(transfer.id))
                    }
            }
        }
    }

    // Navigation ---------------------------------------------------------------------------------

    fun navigate(target: Screen) {
        backStack.update { stack -> if (stack.last() == target) stack else stack + target }
    }

    fun back() {
        backStack.update { stack -> if (stack.size > 1) stack.dropLast(1) else stack }
    }

    fun home() {
        backStack.value = listOf(Screen.Home)
    }

    // Selection ----------------------------------------------------------------------------------

    fun addUris(uris: List<Uri>, openSend: Boolean = true) {
        if (uris.isEmpty()) return
        val context = getApplication<Application>()
        viewModelScope.launch {
            _resolving.value = true
            val resolved = withContext(Dispatchers.IO) {
                uris.distinct().mapNotNull { uri -> UriSendItem.resolve(context, uri)?.let { SelectedItem(uri.toString(), it, uri) } }
            }
            _resolving.value = false
            if (resolved.isEmpty()) {
                message("Couldn't read the selected files")
                return@launch
            }
            if (resolved.size < uris.size) message("Some files couldn't be read and were skipped")
            _selection.update { current -> (current + resolved).distinctBy { it.key } }
            afterSelection(openSend)
        }
    }

    fun addText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _selection.update { it + SelectedItem("text:${System.nanoTime()}", TextSendItem(trimmed), null) }
        afterSelection(true)
    }

    fun removeItem(key: String) {
        _selection.update { list -> list.filterNot { it.key == key } }
    }

    fun clearSelection() {
        _selection.value = emptyList()
    }

    private fun afterSelection(openSend: Boolean) {
        val link = pendingLink
        if (link != null) {
            pendingLink = null
            sendToLink(link)
        } else if (openSend && screen.value != Screen.Send) {
            navigate(Screen.Send)
        }
    }

    // Sending ------------------------------------------------------------------------------------

    fun sendTo(peer: Peer) {
        val items = _selection.value
        if (items.isEmpty()) {
            message("Pick something to send first")
            return
        }
        val id = engine.send(peer, items.map { it.item })
        sentItems[id] = peer to items
        navigate(Screen.Transfer(id))
    }

    fun onQrScanned(raw: String) {
        val link = ConnectLink.parse(raw)
        if (link == null) {
            message("That isn't a Syncro QR code")
            return
        }
        if (link.id == identity.deviceId) {
            message("That's this device's own code")
            return
        }
        if (_selection.value.isEmpty()) {
            pendingLink = link
            message("Choose what to send to ${link.name}")
            _events.tryEmit(UiEvent.PickFiles)
        } else {
            sendToLink(link)
        }
    }

    private fun sendToLink(link: ConnectLink) {
        val items = _selection.value
        if (items.isEmpty()) return
        val id = engine.send(link, items.map { it.item })
        sentItems[id] = null to items
        navigate(Screen.Transfer(id))
    }

    fun sendToAddress(address: String) {
        val items = _selection.value
        if (items.isEmpty() || address.isBlank()) return
        val id = engine.sendToAddress(address.trim(), items.map { it.item })
        sentItems[id] = null to items
        navigate(Screen.Transfer(id))
    }

    fun canRetry(transferId: String): Boolean = sentItems[transferId]?.first != null

    fun retry(transferId: String) {
        val (peer, items) = sentItems[transferId] ?: return
        if (peer == null) return
        val latest = peers.value.firstOrNull { it.id == peer.id } ?: peer
        val id = engine.send(latest, items.map { it.item })
        sentItems[id] = latest to items
        backStack.update { stack -> stack.dropLast(1) + Screen.Transfer(id) }
    }

    fun finishTransfer(transfer: TransferInfo?) {
        if (transfer != null) {
            if (transfer.direction == Direction.SEND && transfer.phase == TransferPhase.COMPLETED) clearSelection()
            if (transfer.phase.isFinished) engine.dismiss(transfer.id)
        }
        val stack = backStack.value
        backStack.value = if (transfer?.direction == Direction.SEND && transfer.phase != TransferPhase.COMPLETED) {
            stack.dropLast(1).ifEmpty { listOf(Screen.Home) }
        } else {
            listOf(Screen.Home)
        }
    }

    // Receiving ----------------------------------------------------------------------------------

    fun respond(transferId: String, decision: Decision) {
        engine.respond(transferId, decision)
        graph.notifications.cancelIncomingRequest(transferId)
        if (decision != Decision.DECLINE) {
            autoOpened += transferId
            navigate(Screen.Transfer(transferId))
        }
    }

    fun openTransfer(transferId: String) {
        if (engine.transfer(transferId) != null) navigate(Screen.Transfer(transferId)) else navigate(Screen.History)
    }

    fun cancel(transferId: String) = engine.cancel(transferId)

    // Settings -----------------------------------------------------------------------------------

    fun setVisible(visible: Boolean) = settings.setVisible(visible)

    fun setTrusted(id: String, trusted: Boolean) = graph.knownDevices.setTrusted(id, trusted)

    fun forgetDevice(id: String) = graph.knownDevices.remove(id)

    fun clearHistory() = graph.history.clear()

    fun removeHistory(id: String) = graph.history.remove(id)

    fun onPermissionsChanged() = graph.onPermissionsChanged()

    fun setScanning(scanning: Boolean) = engine.setScanning(scanning)

    fun message(text: String) {
        _events.tryEmit(UiEvent.Message(text))
    }

    fun connectLink(): ConnectLink? {
        val port = lanPort.value ?: return null
        val hosts = addresses.value.take(4)
        if (hosts.isEmpty()) return null
        val self = engine.self
        return ConnectLink(self.id, self.name, self.type, hosts, port)
    }
}
