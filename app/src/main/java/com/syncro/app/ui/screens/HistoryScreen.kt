package com.syncro.app.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syncro.app.ui.components.ScreenTopBar
import com.syncro.app.ui.components.SyncroCard
import com.syncro.app.ui.components.SyncroAlertDialog
import com.syncro.app.ui.copyToClipboard
import com.syncro.app.ui.iconForFile
import com.syncro.app.ui.openReceivedFile
import com.syncro.app.ui.theme.Syncro
import com.syncro.core.store.HistoryEntry
import com.syncro.core.util.Format
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = Syncro.colors
    val context = LocalContext.current
    var expanded by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = "History",
            subtitle = "${entries.size} transfers",
            onBack = onBack,
            actions = {
                if (entries.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear", color = colors.accent) }
            },
        )
        if (entries.isEmpty()) {
            EmptyState("No transfers yet", "Everything you send and receive shows up here.", Icons.Rounded.History)
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
            items(entries, key = { it.id }) { entry ->
                Column(Modifier.animateContentSize()) {
                    HistoryRow(entry, onClick = { expanded = if (expanded == entry.id) null else entry.id })
                    if (expanded == entry.id) {
                        SyncroCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentPadding = PaddingValues(12.dp)) {
                            Column {
                                Text(
                                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.time)) +
                                        (entry.message?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textMuted,
                                )
                                Spacer(Modifier.height(6.dp))
                                entry.items.forEach { item ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                        Icon(iconForFile(item.name, item.mime, item.text != null), null, tint = colors.accent, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            item.text ?: item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.text,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (item.text == null) Text(Format.bytes(item.size), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                                        when {
                                            item.text != null -> IconButton(onClick = { copyToClipboard(context, item.text!!) }) {
                                                Icon(Icons.Rounded.ContentCopy, "Copy", tint = colors.accent, modifier = Modifier.size(18.dp))
                                            }
                                            item.location != null -> IconButton(onClick = { openReceivedFile(context, item.location!!, item.mime, item.name) }) {
                                                Icon(Icons.Rounded.OpenInNew, "Open", tint = colors.accent, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = colors.outline, modifier = Modifier.padding(vertical = 6.dp))
                                TextButton(onClick = { onRemove(entry.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, null, tint = colors.danger, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Remove from history", color = colors.danger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        SyncroAlertDialog(
            title = "Clear history?",
            confirmText = "Clear",
            onConfirm = {
                confirmClear = false
                onClear()
            },
            onDismiss = { confirmClear = false },
        ) {
            Text("Received files stay in Downloads/Syncro. Only the list is cleared.", style = MaterialTheme.typography.bodyMedium, color = colors.text)
        }
    }
}
