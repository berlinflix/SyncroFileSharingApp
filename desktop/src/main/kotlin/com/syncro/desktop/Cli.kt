package com.syncro.desktop

import com.syncro.core.DeviceType
import com.syncro.core.crypto.Crypto
import com.syncro.core.crypto.DeviceIdentity
import com.syncro.core.engine.SyncroEngine
import com.syncro.core.lan.LanTransport
import com.syncro.core.link.ConnectLink
import com.syncro.core.store.KnownDevices
import com.syncro.core.transfer.Decision
import com.syncro.core.transfer.DirectoryStorage
import com.syncro.core.transfer.FileSendItem
import com.syncro.core.transfer.ReceiveStorage
import com.syncro.core.transfer.SendItem
import com.syncro.core.transfer.TextSendItem
import com.syncro.core.transfer.TransferInfo
import com.syncro.core.transfer.TransferPhase
import com.syncro.core.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Headless mode for scripts, servers and testing:
 *
 *   Syncro receive [--dir DIR] [--name NAME] [--port PORT] [--data DIR]
 *   Syncro send <ip[:port] | syncro://…> <file|text:…>... [--name NAME] [--data DIR]
 */
object Cli {
    fun run(args: Array<String>): Int {
        val options = parse(args.drop(1))
        return when (args[0]) {
            "receive" -> receive(options)
            "send" -> send(options)
            else -> {
                println(
                    """
                    Syncro command line
                      Syncro receive [--dir DIR] [--name NAME] [--port PORT] [--data DIR]
                      Syncro send <ip[:port]|syncro://link> <file|text:message>... [--name NAME] [--data DIR]
                    """.trimIndent(),
                )
                0
            }
        }
    }

    private class Options(val positional: List<String>, val flags: Map<String, String>)

    private fun parse(args: List<String>): Options {
        val positional = mutableListOf<String>()
        val flags = mutableMapOf<String, String>()
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (arg.startsWith("--") && i + 1 < args.size) {
                flags[arg.removePrefix("--")] = args[i + 1]
                i += 2
            } else {
                positional += arg
                i++
            }
        }
        return Options(positional, flags)
    }

    private fun engine(options: Options, storage: ReceiveStorage, onRequest: (SyncroEngine, TransferInfo) -> Unit = { _, _ -> }): Pair<SyncroEngine, LanTransport> {
        val dataDir = File(options.flags["data"] ?: File(DesktopGraph.defaultDataDir(), "cli").path).apply { mkdirs() }
        val name = options.flags["name"] ?: "${DesktopGraph.defaultDeviceName()} (CLI)"
        val known = KnownDevices(File(dataDir, "devices.json"))
        val lan = options.flags["port"]?.toIntOrNull()?.let { LanTransport(preferredPort = it) } ?: LanTransport()
        lateinit var engine: SyncroEngine
        engine = SyncroEngine(DeviceIdentity.loadOrCreate(File(dataDir, "identity.bin")), known, object : SyncroEngine.Platform {
            override fun deviceName() = name
            override fun deviceType() = DeviceType.DESKTOP
            override val storage: ReceiveStorage = storage
            override fun onIncomingRequest(transfer: TransferInfo) = onRequest(engine, transfer)
            override fun onTransferFinished(transfer: TransferInfo) = report(transfer)
            override fun log(message: String, error: Throwable?) {
                System.err.println("[syncro] $message${error?.let { ": $it" } ?: ""}")
            }
        })
        engine.addTransport(lan)
        Crypto.warmUp()
        return engine to lan
    }

    private fun receive(options: Options): Int = runBlocking {
        val dir = File(options.flags["dir"] ?: DesktopSettings.defaultDownloads().path)
        val (engine, lan) = engine(options, DirectoryStorage { dir }) { e, transfer ->
            println("Accepting ${transfer.items.size} item(s) from ${transfer.peerName} · PIN ${transfer.pin}")
            e.respond(transfer.id, Decision.ACCEPT)
        }
        engine.setVisible(true)
        val port = lan.port.first { it != null }
        val link = ConnectLink(engine.self.id, engine.self.name, engine.self.type, com.syncro.core.lan.NetworkPlatform.Default.shareableAddresses(), port!!)
        println("Receiving as \"${engine.self.name}\" on port $port → ${dir.absolutePath}")
        println("Link: ${link.toUri()}")
        while (true) delay(60_000)
        @Suppress("UNREACHABLE_CODE")
        0
    }

    private fun send(options: Options): Int = runBlocking {
        if (options.positional.size < 2) {
            System.err.println("Usage: Syncro send <ip[:port]|syncro://link> <file|text:message>...")
            return@runBlocking 2
        }
        val target = options.positional[0]
        val items: List<SendItem> = options.positional.drop(1).map { spec ->
            if (spec.startsWith("text:")) TextSendItem(spec.removePrefix("text:")) else FileSendItem(File(spec).also { require(it.isFile) { "Not a file: $spec" } })
        }
        val (engine, _) = engine(options, DirectoryStorage { File(".") })
        val link = ConnectLink.parse(target)
        val id = if (link != null) engine.send(link, items) else engine.sendToAddress(target, items)
        var lastPrint = 0L
        var announcedPin = false
        val result = engine.transfers.first { list ->
            val t = list.firstOrNull { it.id == id } ?: return@first false
            if (t.phase == TransferPhase.WAITING_FOR_ACCEPT && !announcedPin) {
                announcedPin = true
                println("Waiting for ${t.peerName} to accept · PIN ${t.pin}")
            }
            if (t.phase == TransferPhase.TRANSFERRING && System.currentTimeMillis() - lastPrint > 1_000) {
                lastPrint = System.currentTimeMillis()
                println("  ${(t.fraction * 100).toInt()}%  ${Format.bytes(t.bytesDone)} / ${Format.bytes(t.totalBytes)}  ${Format.speed(t.bytesPerSecond)}")
            }
            t.phase.isFinished
        }.first { it.id == id }
        engine.shutdown()
        if (result.phase == TransferPhase.COMPLETED) 0 else 1
    }

    private fun report(t: TransferInfo) {
        val seconds = ((t.finishedAt ?: System.currentTimeMillis()) - t.startedAt) / 1000.0
        val avg = if (seconds > 0) Format.speed((t.totalBytes / seconds).toLong()) else "—"
        println("${t.direction} ${t.phase} · ${t.peerName} · ${Format.bytes(t.totalBytes)} in ${"%.1f".format(seconds)}s ($avg)${t.message?.let { " · $it" } ?: ""}")
        t.received.forEach { println("  saved ${it.location ?: "text: " + it.text}") }
    }
}
