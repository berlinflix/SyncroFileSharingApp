package com.syncro.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.syncro.core.transfer.Direction
import com.syncro.core.transfer.TransferPhase
import com.syncro.desktop.ui.App
import java.awt.Dimension
import java.io.File
import java.io.RandomAccessFile
import javax.swing.JOptionPane
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isNotEmpty() && args[0] in setOf("send", "receive", "--help", "-h")) {
        exitProcess(Cli.run(args))
    }

    val dataDir = DesktopGraph.defaultDataDir()
    if (!acquireSingleInstanceLock(dataDir)) {
        JOptionPane.showMessageDialog(null, "Syncro is already running — look for it in the system tray.", "Syncro", JOptionPane.INFORMATION_MESSAGE)
        return
    }

    val graph = DesktopGraph(dataDir)
    val controller = DesktopController(graph)

    application {
        var windowVisible by remember { mutableStateOf(!args.contains("--minimized")) }
        val settings by graph.settings.state.collectAsState()
        val trayState = rememberTrayState()
        @Suppress("DEPRECATION")
        val icon = painterResource("syncro.png")
        val windowState = rememberWindowState(width = 1180.dp, height = 780.dp, position = WindowPosition.PlatformDefault)

        Tray(
            icon = icon,
            state = trayState,
            tooltip = "Syncro — ${if (settings.visible) "ready to receive" else "hidden"}",
            onAction = { windowVisible = true },
            menu = {
                Item("Open Syncro", onClick = { windowVisible = true })
                CheckboxItem("Visible to nearby devices", checked = settings.visible) { checked ->
                    graph.settings.update { it.copy(visible = checked) }
                }
                Separator()
                Item("Quit Syncro", onClick = {
                    graph.engine.shutdown()
                    exitApplication()
                })
            },
        )

        Window(
            onCloseRequest = {
                if (settings.closeToTray) {
                    windowVisible = false
                } else {
                    graph.engine.shutdown()
                    exitApplication()
                }
            },
            visible = windowVisible,
            title = "Syncro",
            icon = icon,
            state = windowState,
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(960, 640) }

            LaunchedEffect(Unit) {
                graph.notifications.collect { event ->
                    when (event) {
                        is DesktopNotification.Request -> {
                            windowVisible = true
                            window.toFront()
                            if (!window.isFocused) {
                                trayState.sendNotification(
                                    Notification("${event.transfer.peerName} wants to share", "Open Syncro to accept · PIN ${event.transfer.pin}", Notification.Type.Info),
                                )
                            }
                        }
                        is DesktopNotification.Finished -> {
                            val t = event.transfer
                            if (!window.isFocused || !windowVisible) {
                                val count = if (t.items.size == 1) t.items.first().name else "${t.items.size} items"
                                val (title, body) = when {
                                    t.phase == TransferPhase.COMPLETED && t.direction == Direction.RECEIVE -> "Received $count" to "From ${t.peerName} · saved to ${File(settings.downloadDir).name}"
                                    t.phase == TransferPhase.COMPLETED -> "Sent $count" to "To ${t.peerName}"
                                    t.phase == TransferPhase.REJECTED && t.direction == Direction.RECEIVE -> return@collect
                                    else -> "Transfer ${t.phase.name.lowercase()}" to (t.message ?: "")
                                }
                                trayState.sendNotification(Notification(title, body, if (t.phase == TransferPhase.COMPLETED) Notification.Type.Info else Notification.Type.Warning))
                            }
                        }
                    }
                }
            }

            App(controller, window)
        }
    }
}

private var lockHandle: RandomAccessFile? = null

private fun acquireSingleInstanceLock(dataDir: File): Boolean = runCatching {
    val file = RandomAccessFile(File(dataDir, "instance.lock"), "rw")
    val lock = file.channel.tryLock()
    if (lock == null) {
        file.close()
        false
    } else {
        lockHandle = file
        true
    }
}.getOrDefault(true)
