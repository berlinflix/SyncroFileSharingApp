package com.syncro.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syncro.app.BuildConfig
import com.syncro.app.data.ThemeMode
import com.syncro.app.ui.components.DeviceAvatar
import com.syncro.app.ui.components.IconBubble
import com.syncro.app.ui.components.Pill
import com.syncro.app.ui.components.ScreenTopBar
import com.syncro.app.ui.components.SectionLabel
import com.syncro.app.ui.components.SettingSwitch
import com.syncro.app.ui.components.SyncroCard
import com.syncro.app.ui.relativeTime
import com.syncro.app.ui.theme.Syncro
import com.syncro.core.DeviceType
import com.syncro.core.store.KnownDevice

data class SettingsState(
    val deviceName: String,
    val deviceType: DeviceType,
    val fingerprint: String,
    val visible: Boolean,
    val backgroundVisible: Boolean,
    val autoAcceptTrusted: Boolean,
    val theme: ThemeMode,
    val nearbySupported: Boolean,
    val nearbyAvailable: Boolean,
    val devices: List<KnownDevice>,
)

data class SettingsActions(
    val onBack: () -> Unit,
    val onRename: (String) -> Unit,
    val onVisible: (Boolean) -> Unit,
    val onBackgroundVisible: (Boolean) -> Unit,
    val onAutoAccept: (Boolean) -> Unit,
    val onTheme: (ThemeMode) -> Unit,
    val onGrantNearby: () -> Unit,
    val onTrust: (String, Boolean) -> Unit,
    val onForget: (String) -> Unit,
)

@Composable
fun SettingsScreen(state: SettingsState, actions: SettingsActions) {
    val colors = Syncro.colors
    var renaming by remember { mutableStateOf(false) }
    var themeMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Settings", onBack = actions.onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item {
                SyncroCard(Modifier.fillMaxWidth(), onClick = { renaming = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DeviceAvatar(state.deviceType, size = 56.dp, highlighted = true)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(state.deviceName, style = MaterialTheme.typography.titleMedium, color = colors.text)
                            Text("Tap to rename", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        }
                        Icon(Icons.Rounded.Edit, null, tint = colors.textMuted)
                    }
                }
            }

            item { SectionLabel("Sharing") }
            item {
                SyncroCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Column {
                        SettingSwitch("Visible to nearby devices", state.visible, actions.onVisible, subtitle = "Others can find you and send requests", icon = Icons.Rounded.Visibility)
                        SettingSwitch(
                            "Receive in background",
                            state.backgroundVisible,
                            actions.onBackgroundVisible,
                            subtitle = "Stay visible after leaving the app (shows a notification)",
                            icon = Icons.Rounded.NightsStay,
                        )
                        SettingSwitch(
                            "Auto-accept trusted devices",
                            state.autoAcceptTrusted,
                            actions.onAutoAccept,
                            subtitle = "Skip the prompt for devices you've trusted",
                            icon = Icons.Rounded.AutoAwesome,
                        )
                    }
                }
            }

            if (state.nearbySupported) {
                item {
                    SyncroCard(Modifier.fillMaxWidth(), onClick = if (state.nearbyAvailable) null else actions.onGrantNearby) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Rounded.WifiTethering, size = 38.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Direct connections", style = MaterialTheme.typography.titleSmall, color = colors.text)
                                Text(
                                    if (state.nearbyAvailable) "Phones connect over Bluetooth + Wi-Fi Direct when there's no shared network"
                                    else "Needs nearby devices + precise location — tap to allow",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                            Pill(if (state.nearbyAvailable) "On" else "Off", color = if (state.nearbyAvailable) colors.success else colors.warning)
                        }
                    }
                }
            }

            item { SectionLabel("Appearance") }
            item {
                SyncroCard(Modifier.fillMaxWidth(), onClick = { themeMenu = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.DarkMode, size = 38.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Theme", style = MaterialTheme.typography.titleSmall, color = colors.text)
                            Text(state.theme.label(), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                        }
                        DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                            ThemeMode.entries.forEach { mode ->
                                DropdownMenuItem(text = { Text(mode.label()) }, onClick = {
                                    themeMenu = false
                                    actions.onTheme(mode)
                                })
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Devices") }
            if (state.devices.isEmpty()) {
                item {
                    Text(
                        "Devices you exchange files with appear here. Trusted devices can send without a prompt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
            } else {
                items(state.devices, key = { it.id }) { device ->
                    DeviceRow(device, actions)
                }
            }

            item { SectionLabel("Security & storage") }
            item {
                SyncroCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoRow(Icons.Rounded.Key, "Your key fingerprint", state.fingerprint)
                        InfoRow(Icons.Rounded.Folder, "Received files", "Downloads/Syncro")
                        Text(
                            "Every transfer uses a fresh ECDH P-256 key exchange, device signatures and AES-256-GCM. " +
                                "Nothing leaves the local link and no server is involved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }
            item {
                Text(
                    "Syncro ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }

    if (renaming) {
        RenameDialog(state.deviceName, onDismiss = { renaming = false }, onSave = {
            renaming = false
            actions.onRename(it)
        })
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> "Match system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    val colors = Syncro.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon, size = 38.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text(value, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun DeviceRow(device: KnownDevice, actions: SettingsActions) {
    val colors = Syncro.colors
    SyncroCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeviceAvatar(device.deviceType, size = 44.dp, trusted = device.trusted)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (device.trusted) "Trusted · " else "") + "seen ${relativeTime(device.lastSeen)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            TextButton(onClick = { actions.onTrust(device.id, !device.trusted) }) {
                Text(if (device.trusted) "Untrust" else "Trust", color = colors.accent)
            }
            IconButton(onClick = { actions.onForget(device.id) }) {
                Icon(Icons.Rounded.DeleteOutline, "Forget", tint = colors.textMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}
