package com.syncro.core

import com.syncro.core.crypto.Crypto
import com.syncro.core.crypto.DeviceIdentity
import com.syncro.core.engine.SyncroEngine
import com.syncro.core.lan.LanTransport
import com.syncro.core.link.ConnectLink
import com.syncro.core.store.KnownDevices
import com.syncro.core.transfer.Decision
import com.syncro.core.transfer.DirectoryStorage
import com.syncro.core.transfer.FileSendItem
import com.syncro.core.transfer.IncomingFile
import com.syncro.core.transfer.ReceiveStorage
import com.syncro.core.transfer.SendItem
import com.syncro.core.transfer.TextSendItem
import com.syncro.core.transfer.TransferInfo
import com.syncro.core.transfer.TransferItemInfo
import com.syncro.core.transfer.TransferPhase
import com.syncro.core.util.Format
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineTransferTest {
    private val engines = mutableListOf<SyncroEngine>()
    private val tempDirs = mutableListOf<File>()

    private inner class Node(name: String, storage: ReceiveStorage? = null, var decide: (TransferInfo) -> Decision? = { Decision.ACCEPT }) {
        val dir: File = Files.createTempDirectory("syncro-$name").toFile().also { tempDirs += it }
        val downloads = File(dir, "downloads")
        val requests = CopyOnWriteArrayList<TransferInfo>()
        val finished = CopyOnWriteArrayList<TransferInfo>()
        val lan = LanTransport(preferredPort = 0, discoveryPort = 0)
        lateinit var engine: SyncroEngine
        private val store: ReceiveStorage = storage ?: DirectoryStorage { downloads }

        init {
            engine = SyncroEngine(DeviceIdentity.generate(), KnownDevices(File(dir, "known.json")), object : SyncroEngine.Platform {
                override fun deviceName() = name
                override fun deviceType() = DeviceType.DESKTOP
                override val storage: ReceiveStorage get() = store
                override fun onIncomingRequest(transfer: TransferInfo) {
                    requests += transfer
                    decide(transfer)?.let { engine.respond(transfer.id, it) }
                }
                override fun onTransferFinished(transfer: TransferInfo) {
                    finished += transfer
                }
            })
            engines += engine
            engine.addTransport(lan)
            engine.setVisible(true)
        }

        val address: String get() = "127.0.0.1:${lan.port.value}"
    }

    @AfterTest
    fun tearDown() {
        engines.forEach { it.shutdown() }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private suspend fun awaitFinished(engine: SyncroEngine, id: String, timeoutMs: Long = 60_000): TransferInfo =
        withTimeout(timeoutMs) { engine.transfers.first { list -> list.any { it.id == id && it.phase.isFinished } }.first { it.id == id } }

    private suspend fun awaitReceiverFinished(node: Node, timeoutMs: Long = 60_000): TransferInfo =
        withTimeout(timeoutMs) { node.engine.transfers.first { list -> list.any { it.phase.isFinished } }.first { it.phase.isFinished } }

    private fun randomFile(dir: File, name: String, size: Int): File {
        val bytes = ByteArray(size).also { Random(size.toLong()).nextBytes(it) }
        return File(dir, name).also { it.writeBytes(bytes) }
    }

    @Test
    fun sendsFilesAndTextEndToEnd() = runBlocking {
        val receiver = Node("Receiver")
        val sender = Node("Sender")
        val files = listOf(
            randomFile(sender.dir, "empty.bin", 0),
            randomFile(sender.dir, "tiny.txt", 1),
            randomFile(sender.dir, "photo.jpg", 3 * 1024 * 1024 + 17),
        )
        val items: List<SendItem> = files.map { FileSendItem(it) } + TextSendItem("https://example.com — héllo ✓")

        val id = sender.engine.sendToAddress(receiver.address, items)
        val sent = awaitFinished(sender.engine, id)
        val received = awaitReceiverFinished(receiver)

        assertEquals(TransferPhase.COMPLETED, sent.phase, sent.message)
        assertEquals(TransferPhase.COMPLETED, received.phase, received.message)
        assertEquals(1, receiver.requests.size)
        assertEquals(sent.pin, receiver.requests.single().pin)
        assertEquals("Sender", received.peer?.name)
        assertEquals(sender.engine.identity.deviceId, received.peer?.id)
        for (file in files) {
            val copy = File(receiver.downloads, file.name)
            assertTrue(copy.isFile, "missing ${file.name}")
            assertTrue(Crypto.sha256(file.readBytes()).contentEquals(Crypto.sha256(copy.readBytes())), "content differs for ${file.name}")
        }
        assertEquals("https://example.com — héllo ✓", received.received.single { it.text != null }.text)
        assertTrue(receiver.downloads.listFiles()!!.none { it.name.endsWith(".syncro-part") })
    }

    @Test
    fun declineIsReportedToSender() = runBlocking {
        val receiver = Node("Receiver", decide = { Decision.DECLINE })
        val sender = Node("Sender")
        val id = sender.engine.sendToAddress(receiver.address, listOf(TextSendItem("hi")))
        val result = awaitFinished(sender.engine, id)
        assertEquals(TransferPhase.REJECTED, result.phase)
        assertEquals("Declined", result.message)
    }

    @Test
    fun trustedDevicesAreAutoAccepted() = runBlocking {
        val receiver = Node("Receiver", decide = { Decision.ACCEPT_AND_TRUST })
        val sender = Node("Sender")
        val first = sender.engine.sendToAddress(receiver.address, listOf(TextSendItem("one")))
        assertEquals(TransferPhase.COMPLETED, awaitFinished(sender.engine, first).phase)
        receiver.decide = { null }
        val second = sender.engine.sendToAddress(receiver.address, listOf(TextSendItem("two")))
        assertEquals(TransferPhase.COMPLETED, awaitFinished(sender.engine, second).phase)
        assertEquals(1, receiver.requests.size)
    }

    @Test
    fun pinnedIdentityMismatchIsBlocked() = runBlocking {
        val receiver = Node("Receiver")
        val sender = Node("Sender")
        val fake = ConnectLink("0123456789abcdef", "Receiver", DeviceType.DESKTOP, listOf("127.0.0.1"), receiver.lan.port.value!!)
        val id = sender.engine.send(fake, listOf(TextSendItem("secret")))
        val result = awaitFinished(sender.engine, id)
        assertEquals(TransferPhase.FAILED, result.phase)
        assertTrue(receiver.requests.isEmpty())
    }

    @Test
    fun receiverCancelStopsSender() = runBlocking {
        val receiver = Node("Receiver")
        val sender = Node("Sender")
        val slow = object : SendItem {
            override val name = "slow.bin"
            override val size = 64L * 1024 * 1024
            override val mimeType: String? = null
            override fun open(): InputStream = object : InputStream() {
                override fun read(): Int = 0
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    Thread.sleep(20)
                    val n = minOf(len, 64 * 1024)
                    java.util.Arrays.fill(b, off, off + n, 7)
                    return n
                }
            }
        }
        val id = sender.engine.sendToAddress(receiver.address, listOf(slow))
        withTimeout(20_000) { receiver.engine.transfers.first { l -> l.any { it.phase == TransferPhase.TRANSFERRING && it.bytesDone > 0 } } }
        receiver.engine.cancel(receiver.engine.transfers.value.single().id)
        val result = awaitFinished(sender.engine, id)
        assertEquals(TransferPhase.CANCELLED, result.phase)
        assertEquals("Cancelled on the other device", result.message)
        assertEquals(TransferPhase.CANCELLED, awaitReceiverFinished(receiver).phase)
        assertTrue(receiver.downloads.listFiles().orEmpty().isEmpty(), "partial files must be removed")
    }

    @Test
    fun tamperedStreamIsDetected() = runBlocking {
        val receiver = Node("Receiver")
        val sender = Node("Sender")
        val proxy = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            val inbound = proxy.accept()
            val outbound = Socket("127.0.0.1", receiver.lan.port.value!!)
            thread(isDaemon = true) { pump(outbound.getInputStream(), inbound.getOutputStream(), -1) }
            pump(inbound.getInputStream(), outbound.getOutputStream(), 300_000)
        }
        val file = randomFile(sender.dir, "data.bin", 2 * 1024 * 1024)
        val id = sender.engine.sendToAddress("127.0.0.1:${proxy.localPort}", listOf(FileSendItem(file)))
        assertEquals(TransferPhase.FAILED, awaitFinished(sender.engine, id).phase)
        val received = awaitReceiverFinished(receiver)
        assertEquals(TransferPhase.FAILED, received.phase)
        assertTrue(received.message!!.contains("modified"), received.message)
        proxy.close()
    }

    @Test
    fun throughputBenchmark() = runBlocking {
        val counting = object : ReceiveStorage {
            override fun open(transferId: String, peer: DeviceInfo, item: TransferItemInfo) = object : IncomingFile {
                override val output: OutputStream = object : OutputStream() {
                    override fun write(b: Int) {}
                    override fun write(b: ByteArray, off: Int, len: Int) {}
                }
                override fun commit() = "memory"
                override fun abort() {}
            }
        }
        val receiver = Node("Receiver", storage = counting)
        val sender = Node("Sender")
        val size = 512L * 1024 * 1024
        val item = object : SendItem {
            override val name = "big.bin"
            override val size = size
            override val mimeType: String? = null
            override fun open(): InputStream = object : InputStream() {
                var remaining = size
                override fun read(): Int = if (remaining-- > 0) 1 else -1
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (remaining <= 0) return -1
                    val n = minOf(len.toLong(), remaining).toInt()
                    remaining -= n
                    return n
                }
            }
        }
        repeat(3) { round ->
            val started = System.nanoTime()
            val id = sender.engine.sendToAddress(receiver.address, listOf(item))
            val result = awaitFinished(sender.engine, id, 120_000)
            val seconds = (System.nanoTime() - started) / 1e9
            assertEquals(TransferPhase.COMPLETED, result.phase, result.message)
            println("Loopback throughput round $round: ${Format.speed((size / seconds).toLong())} (${"%.2f".format(seconds)} s for ${Format.bytes(size)})")
        }
    }

    private fun pump(input: InputStream, output: OutputStream, flipAt: Long) {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        try {
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (flipAt in total until total + n) {
                    val index = (flipAt - total).toInt()
                    buffer[index] = (buffer[index].toInt() xor 0x40).toByte()
                }
                total += n
                output.write(buffer, 0, n)
            }
        } catch (_: Exception) {
        } finally {
            runCatching { output.close() }
        }
    }
}
