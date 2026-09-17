package com.syncro.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncro.app.net.NetworkStatus
import com.syncro.app.ui.components.DeviceAvatar
import com.syncro.app.ui.components.IconBubble
import com.syncro.app.ui.components.SectionLabel
import com.syncro.app.ui.components.SyncroCard
import com.syncro.app.ui.theme.Syncro
import com.syncro.core.DeviceType
import com.syncro.core.store.HistoryEntry
import com.syncro.core.transfer.Direction
import com.syncro.core.transfer.TransferInfo
import com.syncro.core.transfer.TransferPhase
import com.syncro.core.util.Format
import com.syncro.app.ui.relativeTime

data class HomeActions(
    val onPickFiles: () -> Unit,
    val onPickMedia: () -> Unit,
    val onSendText: () -> Unit,
    val onScanQr: () -> Unit,
    val onShowQr: () -> Unit,
    val onToggleVisible: (Boolean) -> Unit,
    val onOpenHistory: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenTransfer: (String) -> Unit,
    val onGrantNearby: () -> Unit,
)

@Composable
fun HomeScreen(
    deviceName: String,
    deviceType: DeviceType,
    visible: Boolean,
    network: NetworkStatus,
    nearbySupported: Boolean,
    nearbyAvailable: Boolean,
    activeTransfers: List<TransferInfo>,
    recent: List<HistoryEntry>,
    actions: HomeActions,
) {
    val colors = Syncro.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Wordmark(Modifier.weight(1f))
                IconButton(onClick = actions.onOpenHistory) {
                    Icon(Icons.Rounded.History, contentDescription = "History", tint = colors.text)
                }
                IconButton(onClick = actions.onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = colors.text)
                }
            }
        }

        item {
            VisibilityCard(deviceName, deviceType, visible, network, nearbyAvailable, actions)
        }

        item {
            SendHero(actions)
        }

        if (nearbySupported && !nearbyAvailable) {
            item {
                SyncroCard(onClick = actions.onGrantNearby, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.WifiTethering, tint = colors.warning, background = colors.warning.copy(alpha = 0.14f))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Share without Wi-Fi", style = MaterialTheme.typography.titleSmall, color = colors.text)
                            Text(
                                "Allow Nearby devices so phones can connect directly, even with no shared network.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted)
                    }
                }
            }
        }

        if (activeTransfers.isNotEmpty()) {
            item { SectionLabel("In progress", Modifier.padding(top = 6.dp)) }
            items(activeTransfers, key = { it.id }) { transfer ->
                ActiveTransferRow(transfer, onClick = { actions.onOpenTransfer(transfer.id) })
            }
        }

        item {
            SectionLabel("Recent", Modifier.padding(top = 6.dp)) {
                if (recent.isNotEmpty()) {
                    Text(
                        "See all",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = actions.onOpenHistory)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        if (recent.isEmpty()) {
            item {
                Text(
                    "Nothing shared yet. Files you send and receive will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        } else {
            items(recent.take(5), key = { "h" + it.id }) { entry ->
                HistoryRow(entry, onClick = actions.onOpenHistory)
            }
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
    val colors = Syncro.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.accentBrush),
            contentAlignment = Alignment.Center,
        ) {
            Text("S", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text("Syncro", style = MaterialTheme.typography.headlineSmall, color = colors.text)
    }
}

@Composable
private fun VisibilityCard(
    deviceName: String,
    deviceType: DeviceType,
    visible: Boolean,
    network: NetworkStatus,
    nearbyAvailable: Boolean,
    actions: HomeActions,
) {
    val colors = Syncro.colors
    SyncroCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceAvatar(deviceType, size = 52.dp, highlighted = visible)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(deviceName, style = MaterialTheme.typography.titleMedium, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val status = when {
                        !visible -> "Hidden · others can't find you"
                        network.connected && nearbyAvailable -> "Visible on ${network.kind} + Nearby"
                        network.connected -> "Visible on ${network.kind} · ${network.address}"
                        nearbyAvailable -> "Visible via Nearby"
                        else -> "Visible · no network yet"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (visible) colors.success else colors.textMuted),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Switch(
                    checked = visible,
                    onCheckedChange = actions.onToggleVisible,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.accent,
                        checkedThumbColor = colors.onAccent,
                        uncheckedTrackColor = colors.surfaceHigh,
                        uncheckedBorderColor = colors.outline,
                        uncheckedThumbColor = colors.textMuted,
                    ),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = colors.success, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("End-to-end encrypted", style = MaterialTheme.typography.labelMedium, color = colors.textMuted, modifier = Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = actions.onShowQr)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.QrCode2, null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("My QR code", style = MaterialTheme.typography.labelMedium, color = colors.accent)
                }
            }
        }
    }
}

@Composable
private fun SendHero(actions: HomeActions) {
    val colors = Syncro.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(colors.accent, Color(0xFF5B3FD9), colors.accentAlt.copy(alpha = 0.95f)),
                ),
            )
            .padding(20.dp),
    ) {
        Column {
            Text(
                buildAnnotatedString {
                    append("Share anything,\n")
                    withStyle(SpanStyle(color = Color.White.copy(alpha = 0.72f))) { append("instantly.") }
                },
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "To phones and PCs nearby — over Wi-Fi, hotspot or directly.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroTile("Files", Icons.Rounded.Folder, actions.onPickFiles, Modifier.weight(1f))
                HeroTile("Media", Icons.Rounded.PhotoLibrary, actions.onPickMedia, Modifier.weight(1f))
                HeroTile("Text", Icons.Rounded.TextFields, actions.onSendText, Modifier.weight(1f))
                HeroTile("Scan", Icons.Rounded.QrCodeScanner, actions.onScanQr, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ActiveTransferRow(transfer: TransferInfo, onClick: () -> Unit) {
    val colors = Syncro.colors
    SyncroCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(if (transfer.direction == Direction.SEND) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (transfer.direction == Direction.SEND) "To " else "From ") + transfer.peerName,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.text,
                        maxLines = 1,
                    )
                    val detail = when (transfer.phase) {
                        TransferPhase.CONNECTING -> "Connecting…"
                        TransferPhase.WAITING_FOR_ACCEPT -> "Waiting for accept · PIN ${transfer.pin}"
                        TransferPhase.AWAITING_DECISION -> "Waiting for you"
                        else -> "${(transfer.fraction * 100).toInt()}% · ${Format.speed(transfer.bytesPerSecond)}"
                    }
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
            }
            if (transfer.phase == TransferPhase.TRANSFERRING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { transfer.fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = colors.accent,
                    trackColor = colors.surfaceHigh,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Syncro.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = when {
            entry.isSuccess -> colors.accent
            entry.status == TransferPhase.CANCELLED.name -> colors.textMuted
            else -> colors.danger
        }
        IconBubble(
            if (entry.isSend) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
            tint = tint,
            background = tint.copy(alpha = 0.12f),
            size = 42.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val title = entry.items.singleOrNull()?.let { if (it.text != null) "Text" else it.name } ?: "${entry.items.size} items"
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = when (entry.status) {
                TransferPhase.COMPLETED.name -> Format.bytes(entry.totalBytes)
                TransferPhase.REJECTED.name -> "Declined"
                TransferPhase.CANCELLED.name -> "Cancelled"
                else -> "Failed"
            }
            Text(
                "${if (entry.isSend) "To" else "From"} ${entry.peerName} · $status",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(relativeTime(entry.time), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
    }
}

@Composable
fun FadeIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible) { content() }
}
