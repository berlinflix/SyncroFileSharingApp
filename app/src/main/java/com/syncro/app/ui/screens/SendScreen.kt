package com.syncro.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.syncro.app.ui.SelectedItem
import com.syncro.app.ui.components.DeviceAvatar
import com.syncro.app.ui.components.Pill
import com.syncro.app.ui.components.PulseRings
import com.syncro.app.ui.components.ScreenTopBar
import com.syncro.app.ui.components.SectionLabel
import com.syncro.app.ui.components.SoftButton
import com.syncro.app.ui.components.SyncroCard
import com.syncro.app.ui.iconForFile
import com.syncro.app.ui.isVisualMedia
import com.syncro.app.ui.theme.Syncro
import com.syncro.core.DeviceType
import com.syncro.core.engine.Peer
import com.syncro.core.util.Format

@Composable
fun SendScreen(
    selfType: DeviceType,
    selection: List<SelectedItem>,
    resolving: Boolean,
    peers: List<Peer>,
    onBack: () -> Unit,
    onAddMore: () -> Unit,
    onRemove: (String) -> Unit,
    onSend: (Peer) -> Unit,
    onScanQr: () -> Unit,
    onSendToAddress: (String) -> Unit,
    onScanningChanged: (Boolean) -> Unit,
) {
    val colors = Syncro.colors
    var showAddressDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onScanningChanged(true)
        onDispose { onScanningChanged(false) }
    }

    val total = selection.sumOf { it.size }
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "Send",
            subtitle = if (selection.isEmpty()) "Nothing selected" else "${selection.size} ${if (selection.size == 1) "item" else "items"} · ${Format.bytes(total)}",
            onBack = onBack,
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(selection, key = { it.key }) { item -> SelectionThumb(item, onRemove = { onRemove(item.key) }) }
                    item {
                        Box(
                            Modifier
                                .size(78.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .border(1.5.dp, colors.outline, RoundedCornerShape(20.dp))
                                .clickable(onClick = onAddMore),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (resolving) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.accent)
                            } else {
                                Icon(Icons.Rounded.Add, contentDescription = "Add more", tint = colors.textMuted)
                            }
                        }
                    }
                }
            }

            item {
                Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                    PulseRings(Modifier.size(210.dp))
                    DeviceAvatar(selfType, size = 64.dp, highlighted = true)
                }
                Text(
                    if (peers.isEmpty()) "Looking for nearby devices…" else "Tap a device to send",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }

            if (peers.isEmpty()) {
                item {
                    SyncroCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Not seeing the other device?", style = MaterialTheme.typography.titleSmall, color = colors.text)
                            Tip("Open Syncro on it and keep it visible.")
                            Tip("Phones find each other without Wi-Fi when Nearby access is allowed.")
                            Tip("For a PC, scan the QR code shown in Syncro for Windows.")
                        }
                    }
                }
            } else {
                item { SectionLabel("Nearby devices", Modifier.padding(top = 10.dp)) }
                items(peers, key = { it.id }) { peer -> PeerRow(peer, enabled = selection.isNotEmpty(), onClick = { onSend(peer) }) }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SoftButton("Scan QR", onClick = onScanQr, icon = Icons.Rounded.QrCodeScanner, modifier = Modifier.weight(1f))
            SoftButton("Address", onClick = { showAddressDialog = true }, icon = Icons.Rounded.Keyboard, modifier = Modifier.weight(1f))
        }
    }

    if (showAddressDialog) {
        AddressDialog(
            onDismiss = { showAddressDialog = false },
            onConfirm = {
                showAddressDialog = false
                onSendToAddress(it)
            },
        )
    }
}

@Composable
private fun Tip(text: String) {
    val colors = Syncro.colors
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(colors.accent),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
    }
}

@Composable
private fun SelectionThumb(item: SelectedItem, onRemove: () -> Unit) {
    val colors = Syncro.colors
    Box(Modifier.size(78.dp)) {
        Box(
            Modifier
                .size(78.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (item.uri != null && isVisualMedia(item.name, item.mimeType)) {
                AsyncImage(model = item.uri, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
                    Icon(iconForFile(item.name, item.mimeType, item.isText), null, tint = colors.accent, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (item.isText) "Text" else item.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(colors.background.copy(alpha = 0.85f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = colors.text, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun PeerRow(peer: Peer, enabled: Boolean, onClick: () -> Unit) {
    val colors = Syncro.colors
    SyncroCard(
        onClick = if (enabled) onClick else null,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeviceAvatar(peer.device.type, size = 50.dp, trusted = peer.trusted)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.device.name, style = MaterialTheme.typography.titleMedium, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    peer.transportLabels.forEach { label ->
                        Pill(
                            if (label == "Nearby") "Direct" else label,
                            color = if (label == "Nearby") colors.accentAlt else colors.accent,
                            icon = if (label == "Nearby") Icons.Rounded.WifiTethering else Icons.Rounded.Wifi,
                        )
                    }
                    if (peer.trusted) Pill("Trusted", color = colors.success)
                }
            }
        }
    }
}

@Composable
private fun AddressDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send to an address") },
        text = {
            Column {
                Text(
                    "Type the IP address shown on the other device, e.g. 192.168.1.20. You'll confirm the PIN on both screens.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Syncro.colors.textMuted,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.trim() },
                    singleLine = true,
                    placeholder = { Text("192.168.1.20") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Syncro.colors.surface,
    )
}
