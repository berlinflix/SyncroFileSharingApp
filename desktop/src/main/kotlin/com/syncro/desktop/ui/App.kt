package com.syncro.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.syncro.core.transfer.TransferPhase
import com.syncro.desktop.DesktopController
import com.syncro.desktop.Page

@Composable
fun App(controller: DesktopController, window: java.awt.Window) {
    val settings by controller.graph.settings.state.collectAsState()
    val page by controller.page.collectAsState()
    val transfers by controller.engine.transfers.collectAsState()
    val message by controller.message.collectAsState()

    SyncroDesktopTheme(settings.theme) {
        val c = Theme.colors
        Box(Modifier.fillMaxSize().background(c.background)) {
            Row(Modifier.fillMaxSize()) {
                Sidebar(
                    page = page,
                    onPage = controller::open,
                    deviceName = settings.deviceName,
                    visible = settings.visible,
                    activeCount = transfers.count { it.isActive },
                    onVisible = { value -> controller.graph.settings.update { it.copy(visible = value) } },
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Crossfade(page, label = "page") { current ->
                        when (current) {
                            Page.SHARE -> SharePage(controller, window)
                            Page.ACTIVITY -> ActivityPage(controller)
                            Page.DEVICES -> DevicesPage(controller)
                            Page.SETTINGS -> SettingsPage(controller, window)
                        }
                    }
                }
            }

            transfers.firstOrNull { it.phase == TransferPhase.AWAITING_DECISION }?.let { request ->
                IncomingRequestOverlay(request, onRespond = { controller.respond(request.id, it) })
            }

            AnimatedVisibility(
                visible = message != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surfaceHigh)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(message.orEmpty(), color = c.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    page: Page,
    onPage: (Page) -> Unit,
    deviceName: String,
    visible: Boolean,
    activeCount: Int,
    onVisible: (Boolean) -> Unit,
) {
    val c = Theme.colors
    Column(
        Modifier
            .width(236.dp)
            .fillMaxHeight()
            .background(c.sidebar)
            .padding(horizontal = 14.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 26.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.accentBrush), contentAlignment = Alignment.Center) {
                Text("S", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text("Syncro", style = MaterialTheme.typography.headlineSmall, color = c.text)
        }
        NavItem("Share", Icons.Rounded.Send, page == Page.SHARE) { onPage(Page.SHARE) }
        NavItem("Activity", Icons.Rounded.History, page == Page.ACTIVITY, badge = activeCount.takeIf { it > 0 }) { onPage(Page.ACTIVITY) }
        NavItem("Devices", Icons.Rounded.Devices, page == Page.DEVICES) { onPage(Page.DEVICES) }
        NavItem("Settings", Icons.Rounded.Settings, page == Page.SETTINGS) { onPage(Page.SETTINGS) }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.surface)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(deviceName, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (visible) c.success else c.textMuted))
                        Spacer(Modifier.width(6.dp))
                        Text(if (visible) "Visible" else "Hidden", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                }
                Switch(
                    checked = visible,
                    onCheckedChange = onVisible,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = c.accent,
                        checkedThumbColor = c.onAccent,
                        uncheckedTrackColor = c.surfaceHigh,
                        uncheckedBorderColor = c.outline,
                        uncheckedThumbColor = c.textMuted,
                    ),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = c.success, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text("End-to-end encrypted", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, badge: Int? = null, onClick: () -> Unit) {
    val c = Theme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(icon, null, tint = if (selected) c.accent else c.textMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = if (selected) c.text else c.textMuted, modifier = Modifier.weight(1f))
        if (badge != null) {
            Box(Modifier.clip(CircleShape).background(c.accent).padding(horizontal = 7.dp, vertical = 1.dp)) {
                Text(badge.toString(), color = c.onAccent, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun PageHeader(title: String, subtitle: String, trailing: @Composable () -> Unit = {}) {
    val c = Theme.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.displaySmall, color = c.text)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
        }
        trailing()
    }
}
