package com.syncro.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.syncro.app.ui.components.GradientButton
import com.syncro.app.ui.components.IconBubble
import com.syncro.app.ui.theme.Syncro
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OnboardingScreen(initialName: String, onFinish: (String) -> Unit) {
    val colors = Syncro.colors
    var name by rememberSaveable { mutableStateOf(initialName) }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        OrbitHero(Modifier.fillMaxWidth().height(250.dp))
        Spacer(Modifier.height(18.dp))
        Text("Welcome to Syncro", style = MaterialTheme.typography.displaySmall, color = colors.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Share files with phones and PCs around you — fast, private and end-to-end encrypted.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(24.dp))
        Feature(Icons.Rounded.Bolt, "Full-speed transfers", "Direct over Wi-Fi, hotspot or Wi-Fi Direct. No internet, no size limits.")
        Feature(Icons.Rounded.Lock, "Private by design", "Fresh keys for every transfer and a PIN to verify who you're talking to.")
        Feature(Icons.Rounded.DesktopWindows, "Works with your PC", "Get Syncro for Windows and move files both ways.")
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("Your device name") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colors.accent, focusedLabelColor = colors.accent),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        GradientButton("Get started", onClick = { onFinish(name) }, modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank())
        Spacer(Modifier.height(10.dp))
        Text(
            "Next we'll ask for notification and nearby-device access so you can receive in the background and share without Wi-Fi.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    val colors = Syncro.colors
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        IconBubble(icon, size = 42.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        }
    }
}

@Composable
private fun OrbitHero(modifier: Modifier = Modifier) {
    val colors = Syncro.colors
    val transition = rememberInfiniteTransition(label = "orbit")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "angle")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radii = listOf(0.28f, 0.42f)
            radii.forEach { r ->
                drawCircle(colors.outline, radius = size.height * r, center = center, style = Stroke(1.5.dp.toPx()))
            }
            listOf(0f to 0.28f, 140f to 0.42f, 250f to 0.42f).forEachIndexed { index, (offset, r) ->
                val theta = Math.toRadians((angle * (if (index % 2 == 0) 1f else -0.7f) + offset).toDouble())
                val point = Offset(center.x + cos(theta).toFloat() * size.height * r, center.y + sin(theta).toFloat() * size.height * r)
                drawCircle(if (index == 1) colors.accentAlt else colors.accent, radius = 6.dp.toPx(), center = point)
                drawCircle((if (index == 1) colors.accentAlt else colors.accent).copy(alpha = 0.2f), radius = 14.dp.toPx(), center = point)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Smartphone, size = 58.dp, tint = colors.onAccent, background = colors.accent)
            Icon(Icons.Rounded.WifiTethering, null, tint = colors.accentAlt, modifier = Modifier.size(30.dp))
            IconBubble(Icons.Rounded.DesktopWindows, size = 58.dp, tint = colors.onAccent, background = colors.accentAlt)
        }
    }
}
