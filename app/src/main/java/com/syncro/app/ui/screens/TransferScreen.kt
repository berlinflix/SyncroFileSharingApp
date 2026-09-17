package com.syncro.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncro.app.ui.components.DeviceAvatar
import com.syncro.app.ui.components.GradientButton
import com.syncro.app.ui.components.PinBadge
import com.syncro.app.ui.components.ProgressRing
import com.syncro.app.ui.components.ScreenTopBar
import com.syncro.app.ui.components.SoftButton
import com.syncro.app.ui.components.SyncroCard
import com.syncro.app.ui.copyToClipboard
import com.syncro.app.ui.iconForFile
import com.syncro.app.ui.looksLikeUrl
import com.syncro.app.ui.openDownloads
import com.syncro.app.ui.openLink
import com.syncro.app.ui.openReceivedFile
import com.syncro.app.ui.theme.Syncro
import com.syncro.core.DeviceType
import com.syncro.core.transfer.Direction
import com.syncro.core.transfer.TransferInfo
import com.syncro.core.transfer.TransferItemInfo
import com.syncro.core.transfer.TransferPhase
import com.syncro.core.util.Format

@Composable
fun TransferScreen(
    transfer: TransferInfo?,
    selfType: DeviceType,
    canRetry: Boolean,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = Syncro.colors
    val context = LocalContext.current
    if (transfer == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar("Transfer", onBack = onClose)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This transfer is no longer available", color = colors.textMuted)
            }
        }
        return
    }
    val sending = transfer.direction == Direction.SEND
    val finished = transfer.phase.isFinished

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = if (sending) "Sending" else "Receiving",
            onBack = null,
            actions = {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.text) }
            },
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DevicesBeam(selfType, transfer, sending) }

            item {
                Text(
                    headline(transfer),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { CenterStatus(transfer) }

            if (transfer.phase == TransferPhase.WAITING_FOR_ACCEPT && transfer.pin != null) {
                item {
                    SyncroCard(Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("Confirm this PIN matches on ${transfer.peerName}", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            PinBadge(transfer.pin!!)
                            if (transfer.peerTrusted) {
                                Spacer(Modifier.height(10.dp))
                                Text("Trusted device", style = MaterialTheme.typography.labelMedium, color = colors.success)
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, tint = colors.success, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "End-to-end encrypted · AES-256-GCM" + (transfer.transport?.let { " · via ${if (it == "Nearby") "Direct" else it}" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                    )
                }
            }

            item {
                Text(
                    "${transfer.items.size} ${if (transfer.items.size == 1) "item" else "items"} · ${Format.bytes(transfer.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
            items(transfer.items, key = { it.index }) { item ->
                ItemRow(
                    item = item,
                    transfer = transfer,
                    onOpen = { location -> openReceivedFile(context, location, item.mimeType, item.name) },
                    onCopy = { copyToClipboard(context, it) },
                    onOpenLink = { openLink(context, it) },
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                !finished -> SoftButton("Cancel", onClick = onCancel, icon = Icons.Rounded.Close, tint = colors.danger, modifier = Modifier.fillMaxWidth())
                transfer.phase == TransferPhase.COMPLETED && !sending && transfer.received.any { it.location != null } -> {
                    SoftButton("Downloads", onClick = { openDownloads(context) }, icon = Icons.Rounded.FolderOpen, modifier = Modifier.weight(1f))
                    GradientButton("Done", onClick = onClose, icon = Icons.Rounded.Check, modifier = Modifier.weight(1f))
                }
                transfer.phase != TransferPhase.COMPLETED && sending && canRetry -> {
                    SoftButton("Close", onClick = onClose, modifier = Modifier.weight(1f))
                    GradientButton("Try again", onClick = onRetry, icon = Icons.Rounded.Refresh, modifier = Modifier.weight(1f))
                }
                else -> GradientButton("Done", onClick = onClose, icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun headline(transfer: TransferInfo): String {
    val sending = transfer.direction == Direction.SEND
    val peer = transfer.peerName
    return when (transfer.phase) {
        TransferPhase.CONNECTING -> "Connecting to $peer"
        TransferPhase.WAITING_FOR_ACCEPT -> "Waiting for $peer"
        TransferPhase.AWAITING_DECISION -> "$peer wants to share"
        TransferPhase.TRANSFERRING -> if (sending) "Sending to $peer" else "Receiving from $peer"
        TransferPhase.COMPLETED -> if (sending) "Sent to $peer" else "Received from $peer"
        TransferPhase.REJECTED -> when {
            !sending -> "Declined"
            transfer.message == "Declined" -> "$peer declined"
            else -> "Not accepted"
        }
        TransferPhase.CANCELLED -> "Transfer cancelled"
        TransferPhase.FAILED -> "Transfer failed"
    }
}

@Composable
private fun DevicesBeam(selfType: DeviceType, transfer: TransferInfo, sending: Boolean) {
    val colors = Syncro.colors
    val moving = transfer.phase == TransferPhase.TRANSFERRING
    val transition = rememberInfiniteTransition(label = "beam")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "beamPhase")
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        DeviceAvatar(selfType, size = 58.dp, highlighted = sending)
        Canvas(Modifier.width(120.dp).height(24.dp)) {
            val dots = 7
            val spacing = size.width / dots
            for (i in 0 until dots) {
                val shift = if (moving) phase * spacing else 0f
                val x = (i * spacing + (if (sending) shift else -shift) + size.width) % size.width
                val alpha = if (moving) 0.25f + 0.75f * (1f - kotlin.math.abs(x / size.width - 0.5f) * 2f) else 0.25f
                drawCircle(colors.accent.copy(alpha = alpha.coerceIn(0.1f, 1f)), radius = 3.dp.toPx(), center = Offset(x, size.height / 2))
            }
        }
        DeviceAvatar(transfer.peer?.type ?: DeviceType.PHONE, size = 58.dp, highlighted = !sending, trusted = transfer.peerTrusted)
    }
}

@Composable
private fun CenterStatus(transfer: TransferInfo) {
    val colors = Syncro.colors
    when (transfer.phase) {
        TransferPhase.COMPLETED -> StatusIcon(Icons.Rounded.CheckCircle, colors.success, "All done")
        TransferPhase.FAILED -> StatusIcon(Icons.Rounded.ErrorOutline, colors.danger, transfer.message ?: "Something went wrong")
        TransferPhase.REJECTED -> StatusIcon(Icons.Rounded.Block, colors.warning, transfer.message ?: "Declined")
        TransferPhase.CANCELLED -> StatusIcon(Icons.Rounded.Close, colors.textMuted, transfer.message ?: "Cancelled")
        else -> {
            val determinate = transfer.phase == TransferPhase.TRANSFERRING
            ProgressRing(if (determinate) transfer.fraction else null, Modifier.size(196.dp).padding(8.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (determinate) {
                        Text("${(transfer.fraction * 100).toInt()}%", fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
                        Text(Format.speed(transfer.bytesPerSecond), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        val eta = transfer.etaSeconds
                        if (eta >= 0) Text("${Format.duration(eta)} left", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    } else {
                        Text(
                            when (transfer.phase) {
                                TransferPhase.CONNECTING -> "Connecting"
                                TransferPhase.WAITING_FOR_ACCEPT -> "Waiting"
                                else -> "Pending"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textMuted,
                        )
                    }
                }
            }
            if (determinate) {
                Text(
                    "${Format.bytes(transfer.bytesDone)} of ${Format.bytes(transfer.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun StatusIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 18.dp)) {
        Box(
            Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(40.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(110.dp)) { drawCircle(tint.copy(alpha = 0.12f)) }
            Icon(icon, null, tint = tint, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = Syncro.colors.textMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ItemRow(
    item: TransferItemInfo,
    transfer: TransferInfo,
    onOpen: (String) -> Unit,
    onCopy: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val colors = Syncro.colors
    val received = transfer.received.firstOrNull { it.index == item.index }
    val text = item.text ?: received?.text
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = received?.location != null) { received?.location?.let(onOpen) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(42.dp)) { drawRect(colors.surfaceHigh) }
            Icon(iconForFile(item.name, item.mimeType, item.isText), null, tint = colors.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (item.isText) (text ?: "Text") else item.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
                maxLines = if (item.isText) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            val state = when {
                received != null -> "Done"
                transfer.phase == TransferPhase.TRANSFERRING && transfer.currentItem == item.index -> "In progress"
                transfer.phase.isFinished && transfer.phase != TransferPhase.COMPLETED -> "Not transferred"
                transfer.phase == TransferPhase.COMPLETED -> "Done"
                else -> "Queued"
            }
            Text("${if (item.isText) "Text" else Format.bytes(item.size)} · $state", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        if (text != null && (transfer.direction == Direction.RECEIVE || transfer.phase == TransferPhase.COMPLETED)) {
            if (looksLikeUrl(text)) {
                IconButton(onClick = { onOpenLink(text) }) { Icon(Icons.Rounded.OpenInNew, "Open link", tint = colors.accent) }
            }
            IconButton(onClick = { onCopy(text) }) { Icon(Icons.Rounded.ContentCopy, "Copy", tint = colors.accent) }
        } else if (received?.location != null) {
            IconButton(onClick = { onOpen(received.location!!) }) { Icon(Icons.Rounded.OpenInNew, "Open", tint = colors.accent) }
        }
    }
}
