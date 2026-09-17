package com.syncro.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.syncro.core.engine.Peer
import com.syncro.core.transfer.TransferPhase
import com.syncro.core.util.Format
import com.syncro.desktop.DesktopController
import com.syncro.desktop.DesktopItem
import com.syncro.desktop.Platform
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.File

@Composable
fun SharePage(controller: DesktopController, window: java.awt.Window) {
    val selection by controller.selection.collectAsState()
    val peers by controller.engine.peers.collectAsState()
    val transfers by controller.engine.transfers.collectAsState()
    val settings by controller.graph.settings.state.collectAsState()
    val addresses by controller.addresses.collectAsState()
    val port by controller.graph.lan.port.collectAsState()
    var composingText by remember { mutableStateOf(false) }

    val chooseFiles = {
        val dialog = FileDialog(window as? Frame, "Choose files to send", FileDialog.LOAD).apply { isMultipleMode = true }
        dialog.isVisible = true
        controller.addFiles(dialog.files?.toList().orEmpty())
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            PageHeader("Share", "Send files and text to phones and computers nearby.")
            DropZone(
                selection = selection,
                onDropFiles = controller::addFiles,
                onChooseFiles = chooseFiles,
                onText = { composingText = true },
                onRemove = controller::remove,
                onClear = controller::clearSelection,
            )
            val visibleTransfers = transfers.filter { it.phase != TransferPhase.AWAITING_DECISION }.asReversed()
            if (visibleTransfers.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SectionTitle("Transfers")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    visibleTransfers.take(6).forEach { transfer ->
                        TransferCard(
                            transfer = transfer,
                            canRetry = controller.canRetry(transfer.id),
                            onCancel = { controller.cancel(transfer.id) },
                            onDismiss = { controller.dismiss(transfer.id) },
                            onRetry = { controller.retry(transfer.id) },
                            onOpenFolder = { Platform.openFolder(File(settings.downloadDir)) },
                        )
                    }
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(4.dp))
            DevicesCard(peers, canSend = selection.isNotEmpty(), onSend = controller::sendTo, onSendToAddress = controller::sendToAddress)
            Spacer(Modifier.height(18.dp))
            ReceiveCard(
                visible = settings.visible,
                link = if (port != null && addresses.isNotEmpty()) controller.connectLink() else null,
                fingerprint = controller.graph.identity.displayFingerprint,
                onMakeVisible = { controller.graph.settings.update { it.copy(visible = true) } },
            )
        }
    }

    if (composingText) {
        TextDialog(onDismiss = { composingText = false }, onAdd = {
            composingText = false
            controller.addText(it)
        })
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DropZone(
    selection: List<DesktopItem>,
    onDropFiles: (List<File>) -> Unit,
    onChooseFiles: () -> Unit,
    onText: () -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    val c = Theme.colors
    var dragging by remember { mutableStateOf(false) }
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                dragging = true
            }

            override fun onExited(event: DragAndDropEvent) {
                dragging = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                dragging = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragging = false
                val transferable = event.awtTransferable
                if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                val files = (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>).orEmpty().filterIsInstance<File>()
                onDropFiles(files)
                return files.isNotEmpty()
            }
        }
    }
    val borderColor = if (dragging) c.accent else c.outline
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (dragging) c.accent.copy(alpha = 0.08f) else c.surface)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(width = 1.6.dp.toPx(), pathEffect = if (selection.isEmpty()) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null),
                )
            }
            .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
            .padding(24.dp),
    ) {
        if (selection.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IconBubble(Icons.Rounded.CloudUpload, size = 68.dp)
                Spacer(Modifier.height(16.dp))
                Text(if (dragging) "Release to add" else "Drop files or folders here", style = MaterialTheme.typography.titleLarge, color = c.text)
                Spacer(Modifier.height(6.dp))
                Text("No size limit. Everything is end-to-end encrypted.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Choose files", onClick = onChooseFiles, icon = Icons.Rounded.Add)
                    SecondaryButton("Send text", onClick = onText, icon = Icons.Rounded.TextFields)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${selection.size} ${if (selection.size == 1) "item" else "items"} ready", style = MaterialTheme.typography.titleLarge, color = c.text)
                    Text("${Format.bytes(selection.sumOf { it.size })} · pick a device to send", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                }
                SecondaryButton("Add", onClick = onChooseFiles, icon = Icons.Rounded.Add)
                Spacer(Modifier.width(8.dp))
                SecondaryButton("Text", onClick = onText, icon = Icons.Rounded.TextFields)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onClear) { Text("Clear", color = c.textMuted) }
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                selection.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBubble(fileIcon(item.name, item.isText), size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (item.isText) (item.item as com.syncro.core.transfer.TextSendItem).text else item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(Format.bytes(item.size), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                        IconButton(onClick = { onRemove(item.key) }) {
                            Icon(Icons.Rounded.Close, "Remove", tint = c.textMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

fun fileIcon(name: String, isText: Boolean) = when {
    isText -> Icons.Rounded.Notes
    name.substringAfterLast('.', "").lowercase() in setOf("pdf", "doc", "docx", "txt", "md", "xls", "xlsx", "ppt", "pptx") -> Icons.Rounded.Description
    else -> Icons.Rounded.InsertDriveFile
}

@Composable
private fun DevicesCard(peers: List<Peer>, canSend: Boolean, onSend: (Peer) -> Unit, onSendToAddress: (String) -> Unit) {
    val c = Theme.colors
    var address by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column {
            SectionTitle("Nearby devices")
            if (peers.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        PulseRings(Modifier.size(64.dp))
                        Icon(Icons.Rounded.Wifi, null, tint = c.accent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Looking for devices…", style = MaterialTheme.typography.titleSmall, color = c.text)
                        Text(
                            "Open Syncro on your phone on the same Wi-Fi or hotspot. Or scan this PC's QR code from the phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textMuted,
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    peers.forEach { peer ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.surfaceHigh.copy(alpha = 0.5f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DeviceAvatar(peer.device.type, size = 42.dp, trusted = peer.trusted)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(peer.device.name, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    peer.routes.firstOrNull()?.address?.substringBeforeLast(':') ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textMuted,
                                )
                            }
                            if (peer.trusted) {
                                Pill("Trusted", color = c.success)
                                Spacer(Modifier.width(8.dp))
                            }
                            PrimaryButton("Send", onClick = { onSend(peer) }, icon = Icons.Rounded.Send, enabled = canSend)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Or send to an address", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.trim() },
                    singleLine = true,
                    placeholder = { Text("e.g. 192.168.1.20", color = c.textMuted.copy(alpha = 0.6f)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.outline),
                    modifier = Modifier.weight(1f).height(52.dp),
                )
                Spacer(Modifier.width(10.dp))
                SecondaryButton("Send", onClick = { if (address.isNotBlank()) onSendToAddress(address) }, icon = Icons.Rounded.Send)
            }
        }
    }
}

@Composable
private fun ReceiveCard(visible: Boolean, link: com.syncro.core.link.ConnectLink?, fingerprint: String, onMakeVisible: () -> Unit) {
    val c = Theme.colors
    Card(Modifier.fillMaxWidth()) {
        Column {
            SectionTitle("Receive from your phone")
            when {
                !visible -> {
                    Text("This PC is hidden. Turn on visibility so your phone can send to it.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Become visible", onClick = onMakeVisible)
                }
                link == null -> Text("Connect this PC to Wi-Fi, Ethernet or your phone's hotspot.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    val qr = remember(link) { qrImage(link.toUri()) }
                    Box(Modifier.clip(RoundedCornerShape(18.dp)).background(c.accentBrush).padding(8.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)) {
                            Image(qr, "Syncro QR code", modifier = Modifier.size(150.dp))
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.QrCode2, null, tint = c.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan with Syncro", style = MaterialTheme.typography.titleSmall, color = c.text)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "On your phone tap Scan, pick files and they land in this PC's Syncro folder. The code pins this PC's identity.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textMuted,
                        )
                        Spacer(Modifier.height(10.dp))
                        link.hosts.take(3).forEach {
                            Text("$it:${link.port}", style = MaterialTheme.typography.labelLarge, color = c.text)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Fingerprint $fingerprint", style = MaterialTheme.typography.labelSmall, color = c.textMuted, textAlign = TextAlign.Start)
                    }
                }
            }
        }
    }
}

fun qrImage(content: String, size: Int = 360): androidx.compose.ui.graphics.ImageBitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8")
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val image = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until matrix.height) for (x in 0 until matrix.width) image.setRGB(x, y, if (matrix[x, y]) 0x0E1116 else 0xFFFFFF)
    return image.toComposeImageBitmap()
}

@Composable
private fun TextDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send text") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("A note, link or code…") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                )
                TextButton(onClick = { Platform.clipboardText()?.let { text = it } }) { Text("Paste clipboard") }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(text) }, enabled = text.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Theme.colors.surface,
    )
}
