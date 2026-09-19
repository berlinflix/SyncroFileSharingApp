package com.syncro.app.ui.screens

import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.syncro.app.ui.components.DeviceAvatar
import com.syncro.app.ui.components.GradientButton
import com.syncro.app.ui.components.PinBadge
import com.syncro.app.ui.components.PulseRings
import com.syncro.app.ui.components.SoftButton
import com.syncro.app.ui.components.SyncroAlertDialog
import com.syncro.app.ui.components.SyncroInput
import com.syncro.app.ui.iconForFile
import com.syncro.app.ui.theme.Syncro
import com.syncro.core.DeviceType
import com.syncro.core.transfer.Decision
import com.syncro.core.transfer.TransferInfo
import com.syncro.core.util.Format

@Composable
fun IncomingRequestDialog(request: TransferInfo, onRespond: (Decision) -> Unit) {
    val colors = Syncro.colors
    var alwaysAccept by remember(request.id) { mutableStateOf(false) }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(colors.surface)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                PulseRings(Modifier.size(128.dp))
                DeviceAvatar(request.peer?.type ?: DeviceType.PHONE, size = 64.dp, highlighted = true, trusted = request.peerTrusted)
            }
            Text(
                "${request.peerName} wants to share",
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            val allText = request.items.all { it.isText }
            Text(
                if (allText) "A text message" else "${request.items.size} ${if (request.items.size == 1) "item" else "items"} · ${Format.bytes(request.totalBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceHigh)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                request.items.take(3).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(iconForFile(item.name, item.mimeType, item.isText), null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (item.isText) item.text.orEmpty() else item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text,
                            maxLines = if (item.isText) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (!item.isText) {
                            Spacer(Modifier.width(8.dp))
                            Text(Format.bytes(item.size), style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        }
                    }
                }
                if (request.items.size > 3) {
                    Text("+ ${request.items.size - 3} more", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                }
            }
            request.pin?.let { pin ->
                Spacer(Modifier.height(16.dp))
                Text("Check the PIN matches the sender's screen", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                Spacer(Modifier.height(8.dp))
                PinBadge(pin)
            }
            if (!request.peerTrusted) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { alwaysAccept = !alwaysAccept }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = alwaysAccept,
                        onCheckedChange = { alwaysAccept = it },
                        colors = CheckboxDefaults.colors(checkedColor = colors.accent, uncheckedColor = colors.textMuted),
                    )
                    Text("Always accept from this device", style = MaterialTheme.typography.bodyMedium, color = colors.text)
                }
            } else {
                Spacer(Modifier.height(14.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Decline", onClick = { onRespond(Decision.DECLINE) }, modifier = Modifier.weight(1f))
                GradientButton(
                    "Accept",
                    onClick = { onRespond(if (alwaysAccept) Decision.ACCEPT_AND_TRUST else Decision.ACCEPT) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = colors.success, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text("End-to-end encrypted", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
        }
    }
}

@Composable
fun TextComposeDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    SyncroAlertDialog(
        title = "Send text",
        confirmText = "Next",
        confirmEnabled = text.isNotBlank(),
        onConfirm = { onSend(text) },
        onDismiss = onDismiss,
    ) {
        SyncroInput(value = text, onValueChange = { text = it }, placeholder = "Type a note, link or code…", multiline = true)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                    val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!pasted.isNullOrEmpty()) text = pasted
                }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ContentPaste, null, tint = Syncro.colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Paste clipboard", style = MaterialTheme.typography.labelMedium, color = Syncro.colors.accent)
        }
    }
}

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    SyncroAlertDialog(
        title = "Device name",
        confirmText = "Save",
        confirmEnabled = text.isNotBlank(),
        onConfirm = { onSave(text) },
        onDismiss = onDismiss,
    ) {
        Text("This is how other devices see you.", style = MaterialTheme.typography.bodyMedium, color = Syncro.colors.textMuted)
        Spacer(Modifier.height(12.dp))
        SyncroInput(value = text, onValueChange = { text = it.take(40) })
    }
}
