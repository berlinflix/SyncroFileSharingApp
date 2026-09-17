package com.syncro.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syncro.app.ui.components.GradientButton
import com.syncro.app.ui.components.IconBubble
import com.syncro.app.ui.components.Pill
import com.syncro.app.ui.components.ScreenTopBar
import com.syncro.app.ui.qrBitmap
import com.syncro.app.ui.theme.Syncro
import com.syncro.core.link.ConnectLink

@Composable
fun ReceiveQrScreen(
    link: ConnectLink?,
    visible: Boolean,
    fingerprint: String,
    onBack: () -> Unit,
    onMakeVisible: () -> Unit,
) {
    val colors = Syncro.colors
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("My QR code", onBack = onBack, subtitle = "Scan to send files to this phone")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            when {
                !visible -> EmptyState(
                    title = "You're hidden",
                    body = "Turn on visibility so other devices can connect to you.",
                    icon = Icons.Rounded.Visibility,
                    action = { GradientButton("Become visible", onClick = onMakeVisible) },
                )
                link == null -> EmptyState(
                    title = "No local network",
                    body = "Connect to Wi-Fi or turn on your hotspot to share a QR code. Phones can still find you via Nearby.",
                    icon = Icons.Rounded.WifiOff,
                )
                else -> {
                    val bitmap = remember(link) { qrBitmap(link.toUri()) }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(32.dp))
                            .background(colors.accentBrush)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.White)
                                .padding(14.dp),
                        ) {
                            Image(bitmap, contentDescription = "Syncro QR code", modifier = Modifier.size(250.dp))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(link.name, style = MaterialTheme.typography.headlineSmall, color = colors.text)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        link.hosts.take(2).forEach { Pill("$it:${link.port}") }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Open Syncro on the other phone and tap Scan, or use Send → Address with the IP above. " +
                            "The code pins this phone's identity, so nobody else on the network can pose as it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text("Key fingerprint", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    Text(fingerprint, style = MaterialTheme.typography.labelLarge, color = colors.text)
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = Syncro.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBubble(icon, size = 72.dp)
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Row { Spacer(Modifier.width(0.dp)); action() }
        }
    }
}
