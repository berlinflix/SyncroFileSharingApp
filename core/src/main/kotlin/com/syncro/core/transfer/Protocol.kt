package com.syncro.core.transfer

import com.syncro.core.ProtocolException
import com.syncro.core.Syncro
import com.syncro.core.SyncroException
import com.syncro.core.secure.Msg
import com.syncro.core.secure.SecureChannel
import com.syncro.core.secure.SecureSession
import com.syncro.core.secure.WireItem
import kotlinx.coroutines.CompletableDeferred
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class TransferRejectedException(val reason: String) : SyncroException("Rejected: $reason")

class PeerCancelledException(val reason: String) : SyncroException("Cancelled by peer: $reason")

internal object Timeouts {
    const val HANDSHAKE_MS = 20_000L
    const val OFFER_MS = 20_000L
    const val WAIT_SILENCE_MS = 25_000L
    const val MAX_WAIT_MS = 150_000L
    const val DECISION_MS = 120_000L
    const val TRANSFER_SILENCE_MS = 45_000L
    const val FINALIZE_MS = 60_000L
}

/**
 * Deadline that closes the connection when it passes. Blocking socket/pipe IO cannot be interrupted,
 * but closing the underlying connection makes it throw immediately.
 */
internal class Watchdog(private val onExpire: () -> Unit) {
    @Volatile private var deadline = Long.MAX_VALUE
    @Volatile private var stopped = false

    @Volatile var expired = false
        private set

    private val worker = thread(isDaemon = true, name = "syncro-watchdog") {
        while (!stopped) {
            try {
                Thread.sleep(250)
            } catch (_: InterruptedException) {
                return@thread
            }
            if (!stopped && System.currentTimeMillis() > deadline) {
                expired = true
                runCatching(onExpire)
                return@thread
            }
        }
    }

    fun arm(timeoutMs: Long) {
        deadline = System.currentTimeMillis() + timeoutMs
    }

    fun stop() {
        stopped = true
        worker.interrupt()
    }
}

/** Sender half of the transfer protocol. Blocking; run on an IO thread. */
internal class SenderProtocol(
    private val session: SecureSession,
    private val transferId: String,
    private val items: List<SendItem>,
    private val progress: AtomicLong,
    private val watchdog: Watchdog,
    private val onAccepted: () -> Unit,
    private val onItem: (Int) -> Unit,
) {
    @Volatile private var peerCancelReason: String? = null

    fun run() {
        val channel = session.channel
        val offerItems = buildOfferItems()
        val offer = Msg.Offer(transferId, offerItems)
        watchdog.arm(Timeouts.WAIT_SILENCE_MS)
        channel.sendControl(offer)

        val waitStarted = System.currentTimeMillis()
        waiting@ while (true) {
            val message = channel.receiveControl()
            watchdog.arm(Timeouts.WAIT_SILENCE_MS)
            when (message) {
                Msg.Accept -> break@waiting
                Msg.Waiting, Msg.Unknown -> {
                    if (System.currentTimeMillis() - waitStarted > Timeouts.MAX_WAIT_MS) {
                        throw TransferRejectedException("timeout")
                    }
                }
                is Msg.Reject -> throw TransferRejectedException(message.reason)
                is Msg.Cancel -> throw PeerCancelledException(message.reason)
                else -> throw ProtocolException("Unexpected message while waiting for acceptance")
            }
        }

        onAccepted()
        progress.addAndGet(offerItems.filter { it.kind == WireItem.KIND_TEXT }.sumOf { it.size })

        val acknowledged = CompletableDeferred<Unit>()
        thread(isDaemon = true, name = "syncro-send-reader") {
            try {
                while (true) {
                    when (val message = channel.receiveControl()) {
                        Msg.DoneAck -> {
                            acknowledged.complete(Unit)
                            return@thread
                        }
                        is Msg.Cancel -> {
                            peerCancelReason = message.reason
                            acknowledged.completeExceptionally(PeerCancelledException(message.reason))
                            session.connection.close()
                            return@thread
                        }
                        else -> Unit
                    }
                }
            } catch (t: Throwable) {
                acknowledged.completeExceptionally(t)
            }
        }

        try {
            val buffer = ByteArray(Syncro.CHUNK_SIZE)
            for (item in offerItems) {
                if (item.kind != WireItem.KIND_FILE) continue
                onItem(item.i)
                val input = try {
                    items[item.i].open()
                } catch (e: Exception) {
                    channel.sendControl(Msg.Skip(item.i, "unreadable"))
                    progress.addAndGet(item.size)
                    continue
                }
                var sent = 0L
                input.use { stream ->
                    channel.sendControl(Msg.FileStart(item.i))
                    watchdog.arm(Timeouts.TRANSFER_SILENCE_MS)
                    while (true) {
                        val read = readChunk(stream, buffer)
                        if (read <= 0) break
                        if (sent + read > item.size) throw SyncroException("${item.name} changed while it was being sent")
                        channel.send(SecureChannel.TYPE_DATA, buffer, 0, read)
                        watchdog.arm(Timeouts.TRANSFER_SILENCE_MS)
                        sent += read
                        progress.addAndGet(read.toLong())
                    }
                }
                if (sent != item.size) throw SyncroException("${item.name} changed while it was being sent")
                channel.sendControl(Msg.FileEnd(item.i, sent))
            }
            channel.sendControl(Msg.Done)
            watchdog.arm(Timeouts.FINALIZE_MS)
            awaitBlocking(acknowledged)
        } catch (t: Throwable) {
            peerCancelReason?.let { throw PeerCancelledException(it) }
            throw t
        }
    }

    private fun buildOfferItems(): List<WireItem> {
        var inlineBudget = Syncro.CHUNK_SIZE / 2
        val result = items.mapIndexed { index, item ->
            if (item.size < 0) throw SyncroException("Size of ${item.name} is unknown")
            if (item is TextSendItem && item.size <= Syncro.MAX_INLINE_TEXT_BYTES && item.size <= inlineBudget) {
                inlineBudget -= item.size.toInt()
                WireItem(index, item.name, item.size, "text/plain", WireItem.KIND_TEXT, item.text)
            } else {
                WireItem(index, item.name, item.size, item.mimeType, WireItem.KIND_FILE)
            }
        }
        if (result.size > Syncro.MAX_ITEMS) throw SyncroException("Too many items (max ${Syncro.MAX_ITEMS})")
        return result
    }

    private fun readChunk(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = input.read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}

/** Receiver half. A single reader thread drives the state machine; decisions arrive from the UI. */
internal class ReceiverProtocol(
    private val session: SecureSession,
    private val transferId: String,
    private val items: List<TransferItemInfo>,
    private val storage: ReceiveStorage,
    private val progress: AtomicLong,
    private val watchdog: Watchdog,
    private val onItem: (Int) -> Unit,
    private val onReceived: (ReceivedItem) -> Unit,
) {
    val finished = CompletableDeferred<Unit>()

    @Volatile private var accepted = false

    fun startReading() {
        thread(isDaemon = true, name = "syncro-receive") { readLoop() }
    }

    fun sendWaiting() = session.channel.sendControl(Msg.Waiting)

    fun reject(reason: String) = session.channel.sendControl(Msg.Reject(reason))

    fun accept() {
        accepted = true
        items.filter { it.isText }.forEach {
            onReceived(ReceivedItem(it.index, it.name, it.size, it.mimeType, null, it.text))
            progress.addAndGet(it.size)
        }
        watchdog.arm(Timeouts.TRANSFER_SILENCE_MS)
        session.channel.sendControl(Msg.Accept)
    }

    private fun readLoop() {
        val completed = BooleanArray(items.size) { items[it].isText }
        var current: IncomingFile? = null
        var currentIndex = -1
        var currentBytes = 0L
        var currentSize = 0L
        try {
            while (true) {
                val frame = session.channel.receive()
                if (frame.type == SecureChannel.TYPE_DATA) {
                    val file = current ?: throw ProtocolException("Data outside of a file")
                    if (currentBytes + frame.length > currentSize) throw ProtocolException("File is larger than announced")
                    file.output.write(frame.buffer, frame.offset, frame.length)
                    currentBytes += frame.length
                    progress.addAndGet(frame.length.toLong())
                    watchdog.arm(Timeouts.TRANSFER_SILENCE_MS)
                    continue
                }
                if (frame.type != SecureChannel.TYPE_CONTROL) throw ProtocolException("Unknown record type")
                when (val message = SecureChannel.decodeControl(frame)) {
                    is Msg.Cancel -> throw PeerCancelledException(message.reason)
                    Msg.Waiting, Msg.Unknown -> Unit
                    is Msg.FileStart -> {
                        if (!accepted) throw ProtocolException("File sent before it was accepted")
                        if (current != null) throw ProtocolException("File started twice")
                        val item = items.getOrNull(message.i)?.takeIf { !it.isText && !completed[it.index] }
                            ?: throw ProtocolException("Bad file index")
                        onItem(item.index)
                        current = storage.open(transferId, session.peer, item)
                        currentIndex = item.index
                        currentBytes = 0
                        currentSize = item.size
                        watchdog.arm(Timeouts.TRANSFER_SILENCE_MS)
                    }
                    is Msg.FileEnd -> {
                        val file = current ?: throw ProtocolException("File ended before it started")
                        if (message.i != currentIndex || message.bytes != currentBytes || currentBytes != currentSize) {
                            throw ProtocolException("File size mismatch")
                        }
                        current = null
                        val location = file.commit()
                        completed[currentIndex] = true
                        val item = items[currentIndex]
                        onReceived(ReceivedItem(item.index, item.name, item.size, item.mimeType, location, null))
                    }
                    is Msg.Skip -> {
                        if (!accepted || current != null) throw ProtocolException("Unexpected skip")
                        val item = items.getOrNull(message.i)?.takeIf { !it.isText && !completed[it.index] }
                            ?: throw ProtocolException("Bad skip index")
                        completed[item.index] = true
                        progress.addAndGet(item.size)
                    }
                    Msg.Done -> {
                        if (!accepted || current != null || !completed.all { it }) throw ProtocolException("Transfer ended early")
                        session.channel.sendControl(Msg.DoneAck)
                        finished.complete(Unit)
                        return
                    }
                    else -> throw ProtocolException("Unexpected message")
                }
            }
        } catch (t: Throwable) {
            current?.let { runCatching { it.abort() } }
            finished.completeExceptionally(t)
        }
    }
}

internal fun <T> awaitBlocking(deferred: CompletableDeferred<T>): T {
    val latch = java.util.concurrent.CountDownLatch(1)
    deferred.invokeOnCompletion { latch.countDown() }
    latch.await()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val error = deferred.getCompletionExceptionOrNull()
    if (error != null) throw error
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    return deferred.getCompleted()
}
