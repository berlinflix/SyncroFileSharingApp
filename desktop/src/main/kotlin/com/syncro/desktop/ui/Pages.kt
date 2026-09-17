package com.syncro.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syncro.core.DeviceType
import com.syncro.core.store.HistoryEntry
import com.syncro.core.transfer.Decision
import com.syncro.core.transfer.Direction
import com.syncro.core.transfer.TransferInfo
import com.syncro.core.transfer.TransferPhase
import com.syncro.core.util.Format
import com.syncro.desktop.DesktopController
import com.syncro.desktop.Platform
import java.io.File
import java.text.DateFormat
import java.util.Date
import javax.swing.JFileChooser

@Composable
fun TransferCard(
    transfer: TransferInfo,
    canRetry: Boolean,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenFolder: () -> Unit,
) {
    val c = Theme.colors
    val sending = transfer.direction == Direction.SEND
    Card(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when (transfer.phase) {
                    TransferPhase.COMPLETED -> Icons.Rounded.CheckCircle to c.success
                    TransferPhase.FAILED -> Icons.Rounded.ErrorOutline to c.danger
                    TransferPhase.REJECTED -> Icons.Rounded.Block to c.warning
                    TransferPhase.CANCELLED -> Icons.Rounded.Close to c.textMuted
                    else -> (if (sending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward) to c.accent
                }
                IconBubble(icon, size = 40.dp, tint = tint, background = tint.copy(alpha = 0.12f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (sending) "To " else "From ") + transfer.peerName,
                        style = MaterialTheme.typography.titleSmall,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val summary = if (transfer.items.size == 1) transfer.items.first().let { if (it.isText) "Text" else it.name } else "${transfer.items.size} items"
                    val status = when (transfer.phase) {
                        TransferPhase.CONNECTING -> "Connecting…"
                        TransferPhase.WAITING_FOR_ACCEPT -> "Waiting for them to accept"
                        TransferPhase.AWAITING_DECISION -> "Waiting for you"
                        TransferPhase.TRANSFERRING -> "${(transfer.fraction * 100).toInt()}% · ${Format.speed(transfer.bytesPerSecond)}" +
                            (transfer.etaSeconds.takeIf { it >= 0 }?.let { " · ${Format.duration(it)} left" } ?: "")
                        TransferPhase.COMPLETED -> "Done · ${Format.bytes(transfer.totalBytes)}"
                        else -> transfer.message ?: transfer.phase.name.lowercase()
                    }
                    Text("$summary · $status", style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (transfer.phase == TransferPhase.WAITING_FOR_ACCEPT && transfer.pin != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("PIN", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                        Text(transfer.pin!!, style = MaterialTheme.typography.titleLarge, color = c.text)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                when {
                    transfer.isActive -> TextButton(onClick = onCancel) { Text("Cancel", color = c.danger) }
                    transfer.phase == TransferPhase.COMPLETED && !sending && transfer.received.any { it.location != null } -> {
                        SecondaryButton("Show", onClick = {
                            transfer.received.firstOrNull { it.location != null }?.location?.let { Platform.revealFile(File(it)) } ?: onOpenFolder()
                        }, icon = Icons.Rounded.FolderOpen)
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Dismiss", tint = c.textMuted, modifier = Modifier.size(18.dp)) }
                    }
                    transfer.phase != TransferPhase.COMPLETED && sending && canRetry -> {
                        SecondaryButton("Retry", onClick = onRetry, icon = Icons.Rounded.Refresh)
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Dismiss", tint = c.textMuted, modifier = Modifier.size(18.dp)) }
                    }
                    else -> IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Dismiss", tint = c.textMuted, modifier = Modifier.size(18.dp)) }
                }
            }
            if (transfer.phase == TransferPhase.TRANSFERRING) {
                Spacer(Modifier.height(12.dp))
                Progress(transfer.fraction)
            }
            val texts = transfer.received.mapNotNull { it.text } + if (sending) emptyList() else transfer.items.filter { it.isText && transfer.phase == TransferPhase.COMPLETED }.mapNotNull { it.text }
            texts.distinct().forEach { text ->
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (text.startsWith("http://") || text.startsWith("https://")) {
                        IconButton(onClick = { Platform.openUrl(text) }) { Icon(Icons.Rounded.OpenInNew, "Open", tint = c.accent, modifier = Modifier.size(18.dp)) }
                    }
                    IconButton(onClick = { Platform.copy(text) }) { Icon(Icons.Rounded.ContentCopy, "Copy", tint = c.accent, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
fun IncomingRequestOverlay(request: TransferInfo, onRespond: (Decision) -> Unit) {
    val c = Theme.colors
    var trust by remember(request.id) { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = true, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(c.surface)
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                PulseRings(Modifier.size(116.dp))
                DeviceAvatar(request.peer?.type ?: DeviceType.PHONE, size = 60.dp, highlighted = true, trusted = request.peerTrusted)
            }
            Text("${request.peerName} wants to share", style = MaterialTheme.typography.headlineSmall, color = c.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                if (request.items.all { it.isText }) "A text message" else "${request.items.size} ${if (request.items.size == 1) "item" else "items"} · ${Format.bytes(request.totalBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textMuted,
            )
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                request.items.take(4).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(fileIcon(item.name, item.isText), null, tint = c.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (item.isText) item.text.orEmpty() else item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (!item.isText) Text(Format.bytes(item.size), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                    }
                }
                if (request.items.size > 4) Text("+ ${request.items.size - 4} more", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            }
            request.pin?.let {
                Spacer(Modifier.height(16.dp))
                Text("Make sure the PIN matches on ${request.peerName}", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Spacer(Modifier.height(8.dp))
                PinDigits(it)
            }
            if (!request.peerTrusted) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { trust = !trust }.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(trust, { trust = it }, colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.textMuted))
                    Text("Always accept from this device", style = MaterialTheme.typography.bodyMedium, color = c.text)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton("Decline", onClick = { onRespond(Decision.DECLINE) }, modifier = Modifier.weight(1f))
                PrimaryButton("Accept", onClick = { onRespond(if (trust) Decision.ACCEPT_AND_TRUST else Decision.ACCEPT) }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = c.success, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text("End-to-end encrypted", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
            }
        }
    }
}

@Composable
fun ActivityPage(controller: DesktopController) {
    val c = Theme.colors
    val transfers by controller.engine.transfers.collectAsState()
    val history by controller.graph.history.entries.collectAsState()
    val settings by controller.graph.settings.state.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 28.dp)) {
        PageHeader("Activity", "Everything you've sent and received.") {
            Row {
                SecondaryButton("Open folder", onClick = { Platform.openFolder(File(settings.downloadDir)) }, icon = Icons.Rounded.FolderOpen)
                if (history.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { controller.graph.history.clear() }) { Text("Clear history", color = c.textMuted) }
                }
            }
        }
        val live = transfers.filter { it.isActive && it.phase != TransferPhase.AWAITING_DECISION }
        if (live.isNotEmpty()) {
            SectionTitle("In progress")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                live.forEach { transfer ->
                    TransferCard(transfer, false, { controller.cancel(transfer.id) }, {}, {}, { Platform.openFolder(File(settings.downloadDir)) })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        SectionTitle("History")
        if (history.isEmpty()) {
            EmptyHint(Icons.Rounded.History, "No transfers yet", "Files you send and receive will be listed here.")
        } else {
            Card(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 12.dp)) {
                Column {
                    history.forEachIndexed { index, entry ->
                        HistoryItemRow(entry, onRemove = { controller.graph.history.remove(entry.id) })
                        if (index != history.lastIndex) HorizontalDivider(color = c.outline.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItemRow(entry: HistoryEntry, onRemove: () -> Unit) {
    val c = Theme.colors
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.pointerHoverIcon(PointerIcon.Hand).padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tint = when {
                entry.isSuccess -> c.accent
                entry.status == TransferPhase.CANCELLED.name -> c.textMuted
                else -> c.danger
            }
            IconBubble(if (entry.isSend) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, size = 38.dp, tint = tint, background = tint.copy(alpha = 0.12f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val title = entry.items.singleOrNull()?.let { if (it.text != null) "Text" else it.name } ?: "${entry.items.size} items"
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val status = when (entry.status) {
                    TransferPhase.COMPLETED.name -> Format.bytes(entry.totalBytes)
                    TransferPhase.REJECTED.name -> "Declined"
                    TransferPhase.CANCELLED.name -> "Cancelled"
                    else -> entry.message ?: "Failed"
                }
                Text("${if (entry.isSend) "To" else "From"} ${entry.peerName} · $status", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            }
            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.time)), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        }
        if (expanded) {
            Column(Modifier.padding(start = 50.dp, top = 8.dp)) {
                entry.items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.text ?: item.name, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        when {
                            item.text != null -> IconButton(onClick = { Platform.copy(item.text!!) }) { Icon(Icons.Rounded.ContentCopy, "Copy", tint = c.accent, modifier = Modifier.size(16.dp)) }
                            item.location != null -> {
                                IconButton(onClick = { Platform.openFile(File(item.location!!)) }) { Icon(Icons.Rounded.OpenInNew, "Open", tint = c.accent, modifier = Modifier.size(16.dp)) }
                                IconButton(onClick = { Platform.revealFile(File(item.location!!)) }) { Icon(Icons.Rounded.Folder, "Show in folder", tint = c.accent, modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
                TextButton(onClick = onRemove) { Text("Remove from history", color = c.danger) }
            }
        }
    }
}

@Composable
fun DevicesPage(controller: DesktopController) {
    val c = Theme.colors
    val devices by controller.graph.knownDevices.devices.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 28.dp)) {
        PageHeader("Devices", "Devices you've exchanged files with. Trusted devices can send without asking.")
        if (devices.isEmpty()) {
            EmptyHint(Icons.Rounded.Devices, "No devices yet", "After your first transfer the device shows up here.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                devices.forEach { device ->
                    Card(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DeviceAvatar(device.deviceType, size = 46.dp, trusted = device.trusted)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(device.name, style = MaterialTheme.typography.titleMedium, color = c.text)
                                Text(
                                    "Key ${device.fingerprint.take(24).uppercase().chunked(4).joinToString(" ")} · last seen " +
                                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(device.lastSeen)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textMuted,
                                )
                            }
                            if (device.trusted) {
                                Pill("Trusted", color = c.success)
                                Spacer(Modifier.width(10.dp))
                            }
                            SecondaryButton(if (device.trusted) "Untrust" else "Trust", onClick = { controller.graph.knownDevices.setTrusted(device.id, !device.trusted) })
                            IconButton(onClick = { controller.graph.knownDevices.remove(device.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, "Forget", tint = c.textMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPage(controller: DesktopController, window: java.awt.Window) {
    val c = Theme.colors
    val settings by controller.graph.settings.state.collectAsState()
    var name by remember(settings.deviceName) { mutableStateOf(settings.deviceName) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 28.dp)) {
        PageHeader("Settings", "Make Syncro yours.")
        Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column {
                    SectionTitle("This computer")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(40) },
                            label = { Text("Device name") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.outline, focusedLabelColor = c.accent),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        PrimaryButton("Save", onClick = { controller.graph.settings.update { it.copy(deviceName = name.trim().ifEmpty { it.deviceName }) } }, enabled = name.isNotBlank() && name != settings.deviceName)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.Folder, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Save received files to", style = MaterialTheme.typography.titleSmall, color = c.text)
                            Text(settings.downloadDir, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        SecondaryButton("Change", onClick = {
                            val chooser = JFileChooser(settings.downloadDir).apply {
                                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                dialogTitle = "Choose where Syncro saves files"
                            }
                            if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
                                controller.graph.settings.update { it.copy(downloadDir = chooser.selectedFile.absolutePath) }
                            }
                        })
                        Spacer(Modifier.width(8.dp))
                        SecondaryButton("Open", onClick = { Platform.openFolder(File(settings.downloadDir)) })
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column {
                    SectionTitle("Sharing")
                    ToggleRow("Visible to nearby devices", "Phones and PCs on your network can find this computer", settings.visible, { v -> controller.graph.settings.update { it.copy(visible = v) } }, Icons.Rounded.Visibility)
                    ToggleRow("Auto-accept trusted devices", "Skip the prompt for devices you've trusted", settings.autoAcceptTrusted, { v -> controller.graph.settings.update { it.copy(autoAcceptTrusted = v) } }, Icons.Rounded.AutoAwesome)
                    ToggleRow("Show files after receiving", "Open the folder when a transfer finishes", settings.openFolderOnReceive, { v -> controller.graph.settings.update { it.copy(openFolderOnReceive = v) } }, Icons.Rounded.FolderOpen)
                    ToggleRow("Keep running in the tray", "Closing the window keeps Syncro ready to receive", settings.closeToTray, { v -> controller.graph.settings.update { it.copy(closeToTray = v) } }, Icons.Rounded.Minimize)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column {
                    SectionTitle("Appearance")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "Match system", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                            val selected = settings.theme == value
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) c.accent.copy(alpha = 0.16f) else c.surfaceHigh)
                                    .clickable { controller.graph.settings.update { it.copy(theme = value) } }
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) c.accent else c.text)
                            }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Security")
                    InfoLine(Icons.Rounded.Key, "Key fingerprint", controller.graph.identity.displayFingerprint)
                    InfoLine(
                        Icons.Rounded.Security,
                        "How it's protected",
                        "Fresh ECDH P-256 keys per transfer with a key commitment, ECDSA device signatures, a 4-digit PIN and AES-256-GCM. No servers, no accounts.",
                    )
                    InfoLine(
                        Icons.Rounded.Lock,
                        "Windows Firewall",
                        "If phones can't see this PC, allow Syncro on private networks when Windows asks (TCP 47821, UDP 47820).",
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, title: String, body: String) {
    val c = Theme.colors
    Row(verticalAlignment = Alignment.Top) {
        IconBubble(icon, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(body, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
}

@Composable
private fun EmptyHint(icon: ImageVector, title: String, body: String) {
    val c = Theme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBubble(icon, size = 64.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
    }
}
