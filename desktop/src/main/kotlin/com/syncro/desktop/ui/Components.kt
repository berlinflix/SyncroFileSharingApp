package com.syncro.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.LaptopWindows
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncro.core.DeviceType

@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable () -> Unit,
) {
    val c = Theme.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = c.surface,
        border = BorderStroke(1.dp, c.outline.copy(alpha = if (c.isDark) 0.8f else 1f)),
    ) {
        Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) c.accentBrush else Brush.linearGradient(listOf(c.surfaceHigh, c.surfaceHigh)))
            .background(if (hovered && enabled) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) c.onAccent else c.textMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) c.onAccent else c.textMuted)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = Theme.colors.text) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered) c.outline else c.surfaceHigh)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

fun DeviceType.icon(): ImageVector = when (this) {
    DeviceType.PHONE -> Icons.Rounded.Smartphone
    DeviceType.TABLET -> Icons.Rounded.TabletAndroid
    DeviceType.LAPTOP -> Icons.Rounded.LaptopWindows
    DeviceType.DESKTOP -> Icons.Rounded.DesktopWindows
}

@Composable
fun DeviceAvatar(type: DeviceType, size: Dp = 48.dp, highlighted: Boolean = false, trusted: Boolean = false) {
    val c = Theme.colors
    Box(Modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (highlighted) c.accentBrush else c.softAccentBrush)
                .padding(2.dp)
                .clip(CircleShape)
                .background(if (highlighted) Color.Transparent else c.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(type.icon(), null, tint = if (highlighted) c.onAccent else c.accent, modifier = Modifier.size(size * 0.46f))
        }
        if (trusted) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size * 0.38f).clip(CircleShape).background(c.surface).padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Verified, "Trusted", tint = c.success, modifier = Modifier.size(size * 0.3f))
            }
        }
    }
}

@Composable
fun IconBubble(icon: ImageVector, size: Dp = 40.dp, tint: Color = Theme.colors.accent, background: Color = Theme.colors.accent.copy(alpha = 0.12f)) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Pill(text: String, color: Color = Theme.colors.accent, icon: ImageVector? = null) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.13f)).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Theme.colors.textMuted, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun PinDigits(pin: String) {
    val c = Theme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pin.forEach { digit ->
            Box(
                Modifier
                    .size(width = 42.dp, height = 50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surfaceHigh)
                    .border(1.dp, c.outline, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(digit.toString(), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            }
        }
    }
}

@Composable
fun Progress(fraction: Float, modifier: Modifier = Modifier) {
    val c = Theme.colors
    LinearProgressIndicator(
        progress = { fraction },
        modifier = modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        color = c.accent,
        trackColor = c.surfaceHigh,
        drawStopIndicator = {},
    )
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, icon: ImageVector? = null) {
    val c = Theme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBubble(icon, size = 36.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent,
                checkedThumbColor = c.onAccent,
                uncheckedTrackColor = c.surfaceHigh,
                uncheckedBorderColor = c.outline,
                uncheckedThumbColor = c.textMuted,
            ),
        )
    }
}

@Composable
fun PulseRings(modifier: Modifier = Modifier, color: Color = Theme.colors.accent) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(modifier) {
        val max = size.minDimension / 2
        repeat(3) { i ->
            val t = (phase + i / 3f) % 1f
            drawCircle(color.copy(alpha = (1f - t) * 0.35f), radius = max * (0.25f + 0.75f * t), style = Stroke(1.5.dp.toPx()))
        }
    }
}
