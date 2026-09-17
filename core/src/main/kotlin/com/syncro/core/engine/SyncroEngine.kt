package com.syncro.core.engine

import com.syncro.core.DeviceInfo
import com.syncro.core.DeviceType
import com.syncro.core.ProtocolException
import com.syncro.core.SecurityException
import com.syncro.core.Syncro
import com.syncro.core.SyncroException
import com.syncro.core.crypto.DeviceIdentity
import com.syncro.core.lan.LanTransport
import com.syncro.core.link.ConnectLink
import com.syncro.core.sanitizeDeviceName
import com.syncro.core.secure.Handshake
import com.syncro.core.secure.Msg
import com.syncro.core.secure.SecureSession
import com.syncro.core.secure.WireItem
import com.syncro.core.store.KnownDevice
import com.syncro.core.store.KnownDevices
import com.syncro.core.transfer.Decision
import com.syncro.core.transfer.Direction
import com.syncro.core.transfer.PeerCancelledException
import com.syncro.core.transfer.ReceiveStorage
import com.syncro.core.transfer.ReceiverProtocol
import com.syncro.core.transfer.SendItem
import com.syncro.core.transfer.SenderProtocol
import com.syncro.core.transfer.TextSendItem
import com.syncro.core.transfer.Timeouts
import com.syncro.core.transfer.TransferInfo
import com.syncro.core.transfer.TransferItemInfo
import com.syncro.core.transfer.TransferPhase
import com.syncro.core.transfer.TransferRejectedException
import com.syncro.core.transfer.Watchdog
import com.syncro.core.transport.Connection
import com.syncro.core.transport.Transport
import com.syncro.core.transport.TransportHost
import com.syncro.core.transport.TransportPeer
import com.syncro.core.util.FileNames
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** A device reachable through one or more transports. */
data class Peer(
    val device: DeviceInfo,
    val routes: List<Route>,
    val trusted: Boolean,
    val lastSeen: Long,
) {
    val id: String get() = device.id
    val transportLabels: List<String> get() = routes.map { it.label }.distinct()
}

data class Route(val transport: String, val label: String, val address: String, val priority: Int)

/**
 * Platform-independent heart of Syncro: owns transports, merges discovered peers, runs the encrypted
 * send/receive state machines and exposes everything as flows for the UI.
 */
class SyncroEngine(
    val identity: DeviceIdentity,
    val knownDevices: KnownDevices,
    private val platform: Platform,
) {
    interface Platform {
        fun deviceName(): String
        fun deviceType(): DeviceType
        val storage: ReceiveStorage
        fun autoAcceptTrusted(): Boolean = true
        fun onIncomingRequest(transfer: TransferInfo) {}
        fun onTransferFinished(transfer: TransferInfo) {}
        fun log(message: String, error: Throwable? = null) {}
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Serializes transport control calls off the caller's (often UI) thread. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val control = Dispatchers.IO.limitedParallelism(1)
    private val transports = MutableStateFlow<List<Transport>>(emptyList())
    private val runners = ConcurrentHashMap<String, Runner>()
    private val incomingSlots = Semaphore(MAX_PENDING_INCOMING)

    private val _transfers = MutableStateFlow<List<TransferInfo>>(emptyList())
    val transfers: StateFlow<List<TransferInfo>> = _transfers.asStateFlow()

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val self: DeviceInfo
        get() = DeviceInfo(identity.deviceId, sanitizeDeviceName(platform.deviceName()), platform.deviceType())

    val transportList: StateFlow<List<Transport>> = transports.asStateFlow()

    private val host = object : TransportHost {
        override val self: DeviceInfo get() = this@SyncroEngine.self
        override fun onIncomingConnection(connection: Connection) = handleIncoming(connection)
        override fun log(message: String, error: Throwable?) = platform.log(message, error)
    }

    init {
        @OptIn(ExperimentalCoroutinesApi::class)
        scope.launch {
            transports
                .flatMapLatest { list ->
                    if (list.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(list.map { transport -> transport.discovered.map { peers -> peers.map { transport to it } } }) { lists ->
                            lists.flatMap { it }
                        }
                    }
                }
                .combine(knownDevices.devices) { entries, known -> mergePeers(entries, known) }
                .collect { _peers.value = it }
        }
    }

    fun addTransport(transport: Transport) {
        transports.update { (it + transport).sortedBy { t -> t.priority } }
        scope.launch(control) {
            transport.start(host)
            transport.setAdvertising(_visible.value)
            transport.setScanning(_scanning.value)
        }
    }

    fun transport(id: String): Transport? = transports.value.firstOrNull { it.id == id }

    fun setVisible(enabled: Boolean) {
        _visible.value = enabled
        scope.launch(control) {
            transports.value.forEach { runCatching { it.setAdvertising(_visible.value) }.onFailure { e -> platform.log("setAdvertising failed", e) } }
        }
    }

    fun setScanning(enabled: Boolean) {
        _scanning.value = enabled
        scope.launch(control) {
            transports.value.forEach { runCatching { it.setScanning(_scanning.value) }.onFailure { e -> platform.log("setScanning failed", e) } }
        }
    }

    /** Re-announces after a device rename or network change. */
    fun refreshPresence() {
        scope.launch(control) {
            if (!_visible.value) return@launch
            transports.value.filter { it.id == LanTransport.ID }.forEach {
                runCatching {
                    it.setAdvertising(false)
                    it.setAdvertising(true)
                }
            }
        }
    }

    fun send(peer: Peer, items: List<SendItem>): String =
        startOutgoing(peer.device, items) { connectToPeer(peer) }

    fun send(link: ConnectLink, items: List<SendItem>): String =
        startOutgoing(DeviceInfo(link.id, link.name, link.type), items) {
            val lan = transport(LanTransport.ID) ?: throw SyncroException("Wi-Fi transport unavailable")
            withTimeout(LAN_CONNECT_TIMEOUT_MS) { lan.connect(link.hosts.map { "$it:${link.port}" }) }
        }

    /** Manual "connect by address" — the peer's identity is not pinned, so the PIN check matters. */
    fun sendToAddress(address: String, items: List<SendItem>): String =
        startOutgoing(null, items) {
            val lan = transport(LanTransport.ID) ?: throw SyncroException("Wi-Fi transport unavailable")
            withTimeout(LAN_CONNECT_TIMEOUT_MS) { lan.connect(listOf(address)) }
        }

    fun respond(transferId: String, decision: Decision) {
        (runners[transferId] as? IncomingRunner)?.decision?.complete(decision)
    }

    fun cancel(transferId: String) {
        runners[transferId]?.cancel()
    }

    fun dismiss(transferId: String) {
        _transfers.update { list -> list.filterNot { it.id == transferId && it.phase.isFinished } }
    }

    fun clearFinished() {
        _transfers.update { list -> list.filter { it.isActive } }
    }

    fun transfer(id: String): TransferInfo? = _transfers.value.firstOrNull { it.id == id }

    fun shutdown() {
        runners.values.forEach { it.cancel() }
        transports.value.forEach { runCatching { it.stop() } }
    }

    // ---------------------------------------------------------------------------------------------

    private fun mergePeers(entries: List<Pair<Transport, TransportPeer>>, known: List<KnownDevice>): List<Peer> {
        val trusted = known.filter { it.trusted }.map { it.id }.toSet()
        return entries
            .filter { it.second.device.id != identity.deviceId }
            .groupBy { it.second.device.id }
            .map { (id, group) ->
                val freshest = group.maxBy { it.second.lastSeen }.second
                Peer(
                    device = freshest.device,
                    routes = group
                        .sortedWith(compareBy<Pair<Transport, TransportPeer>> { it.first.priority }.thenByDescending { it.second.lastSeen })
                        .map { (transport, peer) -> Route(transport.id, transport.label, peer.address, transport.priority) },
                    trusted = id in trusted,
                    lastSeen = freshest.lastSeen,
                )
            }
            .sortedWith(compareByDescending<Peer> { it.trusted }.thenBy { it.device.name.lowercase() }.thenBy { it.id })
    }

    private suspend fun connectToPeer(selected: Peer): Connection {
        val current = _peers.value.firstOrNull { it.id == selected.id } ?: selected
        val routes = current.routes.toMutableList()
        knownDevices.get(selected.id)?.hosts?.forEach { host ->
            if (routes.none { it.transport == LanTransport.ID && it.address.substringBeforeLast(':') == host }) {
                routes += Route(LanTransport.ID, "Wi-Fi", "$host:${Syncro.TRANSFER_PORT}", 0)
            }
        }
        var lastError: Throwable? = null
        val grouped = routes.groupBy { it.transport }.entries.sortedBy { entry -> entry.value.minOf { it.priority } }
        for ((transportId, group) in grouped) {
            val transport = transport(transportId) ?: continue
            try {
                val timeout = if (transportId == LanTransport.ID) LAN_CONNECT_TIMEOUT_MS else OTHER_CONNECT_TIMEOUT_MS
                return withTimeout(timeout) { transport.connect(group.map { it.address }) }
            } catch (e: TimeoutCancellationException) {
                lastError = SyncroException("${transport.label} connection timed out")
            } catch (e: SyncroException) {
                lastError = e
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw SyncroException("Couldn't reach ${selected.device.name}", lastError)
    }

    private fun startOutgoing(expected: DeviceInfo?, items: List<SendItem>, connector: suspend () -> Connection): String {
        require(items.isNotEmpty()) { "Nothing to send" }
        val id = newTransferId()
        val itemInfos = items.mapIndexed { index, item ->
            val inline = item is TextSendItem && item.size <= Syncro.MAX_INLINE_TEXT_BYTES
            TransferItemInfo(index, item.name, item.size, item.mimeType, inline, (item as? TextSendItem)?.text)
        }
        val info = TransferInfo(
            id = id,
            direction = Direction.SEND,
            peer = expected,
            peerTrusted = knownDevices.isTrusted(expected?.id),
            items = itemInfos,
            totalBytes = items.sumOf { it.size.coerceAtLeast(0) },
            phase = TransferPhase.CONNECTING,
        )
        _transfers.update { it + info }
        val runner = OutgoingRunner(id, expected, items, connector)
        runners[id] = runner
        runner.job = scope.launch { runner.run() }
        return id
    }

    private fun handleIncoming(connection: Connection) {
        if (!incomingSlots.tryAcquire()) {
            connection.close()
            return
        }
        val runner = IncomingRunner(newTransferId(), connection)
        runners[runner.id] = runner
        runner.job = scope.launch {
            try {
                runner.run()
            } finally {
                incomingSlots.release()
            }
        }
    }

    private fun update(id: String, transform: (TransferInfo) -> TransferInfo) {
        _transfers.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun startTicker(id: String, progress: AtomicLong): Job = scope.launch {
        var lastBytes = progress.get()
        var lastTime = System.nanoTime()
        var rate = 0.0
        while (isActive) {
            delay(250)
            val bytes = progress.get()
            val now = System.nanoTime()
            val seconds = (now - lastTime) / 1e9
            if (seconds > 0) {
                val instant = (bytes - lastBytes) / seconds
                rate = if (rate == 0.0) instant else rate * 0.7 + instant * 0.3
            }
            lastBytes = bytes
            lastTime = now
            update(id) { if (it.phase == TransferPhase.TRANSFERRING) it.copy(bytesDone = bytes, bytesPerSecond = rate.toLong()) else it }
        }
    }

    private abstract inner class Runner(val id: String) {
        @Volatile var connection: Connection? = null
        @Volatile var session: SecureSession? = null
        @Volatile var cancelledLocally = false
        var job: Job? = null
        val progress = AtomicLong()
        val watchdog = Watchdog { connection?.close() }
        private val finished = AtomicBoolean(false)

        fun cancel() {
            if (cancelledLocally) return
            cancelledLocally = true
            scope.launch {
                sendCancelQuietly("cancelled")
                job?.cancel()
                connection?.close()
            }
        }

        fun sendCancelQuietly(reason: String) {
            val channel = session?.channel ?: return
            val sender = Thread({ runCatching { channel.sendControl(Msg.Cancel(reason)) } }, "syncro-cancel")
            sender.isDaemon = true
            sender.start()
            runCatching { sender.join(750) }
        }

        fun finish(phase: TransferPhase, message: String?) {
            if (!finished.compareAndSet(false, true)) return
            update(id) {
                it.copy(
                    phase = phase,
                    message = message,
                    finishedAt = System.currentTimeMillis(),
                    bytesDone = if (phase == TransferPhase.COMPLETED) it.totalBytes else progress.get(),
                    bytesPerSecond = 0,
                )
            }
            transfer(id)?.let { info -> runCatching { platform.onTransferFinished(info) } }
        }

        fun fail(error: Throwable) {
            val (phase, message) = describe(error, cancelledLocally, watchdog.expired)
            if (phase == TransferPhase.FAILED && !cancelledLocally) platform.log("Transfer $id failed", error)
            if (error !is PeerCancelledException && error !is TransferRejectedException && !cancelledLocally) {
                sendCancelQuietly(if (error is SecurityException) REASON_INTEGRITY else REASON_ERROR)
            }
            finish(phase, message)
        }

        fun cleanup(ticker: Job?) {
            ticker?.cancel()
            watchdog.stop()
            connection?.close()
            runners.remove(id)
        }
    }

    private inner class OutgoingRunner(
        id: String,
        private val expected: DeviceInfo?,
        private val items: List<SendItem>,
        private val connector: suspend () -> Connection,
    ) : Runner(id) {

        suspend fun run() {
            var ticker: Job? = null
            try {
                val conn = connector()
                connection = conn
                if (cancelledLocally) throw SyncroException("Cancelled")
                update(id) { it.copy(transport = conn.transportLabel) }
                watchdog.arm(Timeouts.HANDSHAKE_MS)
                val secure = Handshake.client(conn, identity, self)
                session = secure
                if (expected != null && secure.peer.id != expected.id) {
                    throw SecurityException("That device's identity key doesn't match — transfer blocked")
                }
                val trusted = knownDevices.isTrusted(secure.peer.id, secure.peerFingerprint)
                knownDevices.recordSeen(secure.peer, secure.peerFingerprint, conn.remoteHost)
                update(id) {
                    it.copy(peer = secure.peer, peerTrusted = trusted, pin = secure.pin, phase = TransferPhase.WAITING_FOR_ACCEPT)
                }
                ticker = startTicker(id, progress)
                SenderProtocol(
                    session = secure,
                    transferId = id,
                    items = items,
                    progress = progress,
                    watchdog = watchdog,
                    onAccepted = { update(id) { it.copy(phase = TransferPhase.TRANSFERRING) } },
                    onItem = { index -> update(id) { it.copy(currentItem = index) } },
                ).run()
                finish(TransferPhase.COMPLETED, null)
            } catch (t: Throwable) {
                fail(t)
            } finally {
                cleanup(ticker)
            }
        }
    }

    private inner class IncomingRunner(id: String, private val incoming: Connection) : Runner(id) {
        val decision = CompletableDeferred<Decision>()

        suspend fun run() {
            var ticker: Job? = null
            var registered = false
            connection = incoming
            try {
                watchdog.arm(Timeouts.HANDSHAKE_MS)
                val secure = Handshake.server(incoming, identity, self)
                session = secure
                watchdog.arm(Timeouts.OFFER_MS)
                val offer = secure.channel.receiveControl() as? Msg.Offer ?: throw ProtocolException("Expected a transfer offer")
                val items = validateOffer(offer)
                val trusted = knownDevices.isTrusted(secure.peer.id, secure.peerFingerprint)
                knownDevices.recordSeen(secure.peer, secure.peerFingerprint, incoming.remoteHost)
                val totalBytes = items.sumOf { it.size }
                val info = TransferInfo(
                    id = id,
                    direction = Direction.RECEIVE,
                    peer = secure.peer,
                    peerTrusted = trusted,
                    items = items,
                    totalBytes = totalBytes,
                    phase = TransferPhase.AWAITING_DECISION,
                    pin = secure.pin,
                    transport = incoming.transportLabel,
                )
                _transfers.update { it + info }
                registered = true

                val protocol = ReceiverProtocol(
                    session = secure,
                    transferId = id,
                    items = items,
                    storage = platform.storage,
                    progress = progress,
                    watchdog = watchdog,
                    onItem = { index -> update(id) { it.copy(currentItem = index) } },
                    onReceived = { item -> update(id) { it.copy(received = it.received + item) } },
                )
                protocol.startReading()
                watchdog.arm(Timeouts.DECISION_MS + 30_000)

                if (trusted && platform.autoAcceptTrusted()) {
                    decision.complete(Decision.ACCEPT)
                } else {
                    runCatching { platform.onIncomingRequest(info) }
                }

                val keepAlive = scope.launch {
                    while (isActive) {
                        delay(4_000)
                        runCatching { protocol.sendWaiting() }
                    }
                }
                val choice = try {
                    withTimeoutOrNull(Timeouts.DECISION_MS) {
                        select {
                            decision.onAwait { it }
                            protocol.finished.onAwait { throw ProtocolException("Sender stopped before the request was answered") }
                        }
                    }
                } finally {
                    keepAlive.cancel()
                }

                when (choice) {
                    null -> {
                        runCatching { protocol.reject("timeout") }
                        lingerThenClose()
                        finish(TransferPhase.REJECTED, "Request expired")
                    }
                    Decision.DECLINE -> {
                        runCatching { protocol.reject("declined") }
                        lingerThenClose()
                        finish(TransferPhase.REJECTED, "Declined")
                    }
                    Decision.ACCEPT, Decision.ACCEPT_AND_TRUST -> {
                        if (choice == Decision.ACCEPT_AND_TRUST) {
                            knownDevices.setTrusted(secure.peer.id, true)
                            update(id) { it.copy(peerTrusted = true) }
                        }
                        if (platform.storage.availableBytes() < totalBytes) {
                            runCatching { protocol.reject("storage") }
                            lingerThenClose()
                            finish(TransferPhase.FAILED, "Not enough free storage")
                            return
                        }
                        update(id) { it.copy(phase = TransferPhase.TRANSFERRING) }
                        ticker = startTicker(id, progress)
                        protocol.accept()
                        protocol.finished.await()
                        lingerThenClose()
                        finish(TransferPhase.COMPLETED, null)
                    }
                }
            } catch (t: Throwable) {
                if (registered) fail(t) else platform.log("Incoming connection dropped: ${t.message}")
            } finally {
                cleanup(ticker)
            }
        }

        /** Gives the last message time to leave buffered transports before the connection is torn down. */
        private suspend fun lingerThenClose() {
            delay(200)
        }
    }

    private fun validateOffer(offer: Msg.Offer): List<TransferItemInfo> {
        if (offer.items.isEmpty() || offer.items.size > Syncro.MAX_ITEMS) throw ProtocolException("Invalid number of items")
        return offer.items.mapIndexed { index, item ->
            if (item.i != index) throw ProtocolException("Invalid item index")
            if (item.size < 0) throw ProtocolException("Invalid item size")
            when (item.kind) {
                WireItem.KIND_TEXT -> {
                    val text = item.text ?: throw ProtocolException("Missing text")
                    if (item.size > Syncro.MAX_INLINE_TEXT_BYTES || text.toByteArray(Charsets.UTF_8).size.toLong() != item.size) {
                        throw ProtocolException("Invalid text item")
                    }
                    TransferItemInfo(index, "Text", item.size, "text/plain", true, text)
                }
                WireItem.KIND_FILE -> TransferItemInfo(index, FileNames.sanitize(item.name), item.size, item.mime?.take(128), false)
                else -> throw ProtocolException("Unsupported item type ${item.kind}")
            }
        }
    }

    private fun describe(error: Throwable, cancelledLocally: Boolean, timedOut: Boolean): Pair<TransferPhase, String> = when {
        cancelledLocally -> TransferPhase.CANCELLED to "Cancelled"
        error is TransferRejectedException -> TransferPhase.REJECTED to when (error.reason) {
            "declined" -> "Declined"
            "timeout" -> "No response from the receiver"
            "storage" -> "Receiver doesn't have enough free space"
            "busy" -> "Receiver is busy"
            else -> "Declined"
        }
        error is PeerCancelledException -> when (error.reason) {
            REASON_INTEGRITY -> TransferPhase.FAILED to "Stopped: the other device detected modified data"
            REASON_ERROR -> TransferPhase.FAILED to "Transfer failed on the other device"
            else -> TransferPhase.CANCELLED to "Cancelled on the other device"
        }
        timedOut -> TransferPhase.FAILED to "Connection stalled"
        error is SecurityException -> TransferPhase.FAILED to (error.message ?: "Security check failed")
        error is ProtocolException -> TransferPhase.FAILED to "Incompatible device (${error.message})"
        error is ConnectException || error is NoRouteToHostException || error is SocketTimeoutException ->
            TransferPhase.FAILED to "Couldn't reach the device"
        error is SyncroException -> TransferPhase.FAILED to (error.message ?: "Transfer failed")
        error is IOException -> TransferPhase.FAILED to "Connection lost"
        else -> TransferPhase.FAILED to (error.message ?: error.javaClass.simpleName)
    }

    private fun newTransferId(): String = UUID.randomUUID().toString()

    companion object {
        private const val MAX_PENDING_INCOMING = 8
        private const val REASON_ERROR = "error"
        private const val REASON_INTEGRITY = "integrity"
        private const val LAN_CONNECT_TIMEOUT_MS = 6_000L
        private const val OTHER_CONNECT_TIMEOUT_MS = 45_000L
    }
}
