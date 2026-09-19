package com.syncro.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.LaptopWindows
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncro.app.ui.theme.Syncro
import com.syncro.core.DeviceType

@Composable
fun SyncroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable () -> Unit,
) {
    val colors = Syncro.colors
    val shape = RoundedCornerShape(24.dp)
    val border = BorderStroke(1.dp, colors.outline.copy(alpha = if (colors.isDark) 0.7f else 1f))
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = colors.surface, border = border) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = colors.surface, border = border) {
            Box(Modifier.padding(contentPadding)) { content() }
        }
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = Syncro.colors
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) colors.accentBrush else Brush.linearGradient(listOf(colors.surfaceHigh, colors.surfaceHigh)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) colors.onAccent else colors.textMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) colors.onAccent else colors.textMuted)
    }
}

@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Syncro.colors.text,
) {
    val colors = Syncro.colors
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
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

/** Circular avatar with a gradient ring, device glyph and optional trusted badge. */
@Composable
fun DeviceAvatar(
    type: DeviceType,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    trusted: Boolean = false,
    highlighted: Boolean = false,
) {
    val colors = Syncro.colors
    Box(modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (highlighted) colors.accentBrush else colors.softAccentBrush)
                .padding(2.dp)
                .clip(CircleShape)
                .background(if (highlighted) Color.Transparent else colors.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                type.icon(),
                contentDescription = null,
                tint = if (highlighted) colors.onAccent else colors.accent,
                modifier = Modifier.size(size * 0.44f),
            )
        }
        if (trusted) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.36f)
                    .clip(CircleShape)
                    .background(colors.background)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Verified, contentDescription = "Trusted", tint = colors.success, modifier = Modifier.size(size * 0.3f))
            }
        }
    }
}

/** Expanding rings used as the "looking for devices" radar. */
@Composable
fun PulseRings(modifier: Modifier = Modifier, color: Color = Syncro.colors.accent, rings: Int = 3, periodMs: Int = 2600) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier) {
        val maxRadius = size.minDimension / 2
        repeat(rings) { index ->
            val t = (phase + index.toFloat() / rings) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - t) * 0.35f),
                radius = maxRadius * (0.25f + 0.75f * t),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        drawCircle(color = color.copy(alpha = 0.06f), radius = maxRadius * 0.62f)
    }
}

/** Gradient progress ring; spins when [fraction] is null. */
@Composable
fun ProgressRing(
    fraction: Float?,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp,
    content: @Composable () -> Unit = {},
) {
    val colors = Syncro.colors
    val animated by animateFloatAsState(targetValue = fraction ?: 0.25f, animationSpec = tween(350), label = "ring")
    val transition = rememberInfiniteTransition(label = "spin")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "rotation")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            drawArc(colors.surfaceHigh, 0f, 360f, false, topLeft, Size(diameter, diameter), style = Stroke(stroke))
            val brush = Brush.sweepGradient(listOf(colors.accent, colors.accentAlt, colors.accent), center = Offset(size.width / 2, size.height / 2))
            rotate(if (fraction == null) rotation - 90f else -90f) {
                drawArc(brush, 0f, 360f * animated.coerceIn(0.002f, 1f), false, topLeft, Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

@Composable
fun IconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Syncro.colors.accent,
    background: Color = Syncro.colors.accent.copy(alpha = 0.12f),
    size: Dp = 44.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.34f))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Syncro.colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = Syncro.colors.accent, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun PinBadge(pin: String, modifier: Modifier = Modifier) {
    val colors = Syncro.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pin.forEach { digit ->
            Box(
                Modifier
                    .size(width = 44.dp, height = 54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceHigh)
                    .border(1.dp, colors.outline, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(digit.toString(), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
            }
        }
    }
}

@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Syncro.colors.text)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Syncro.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Syncro.colors.textMuted, maxLines = 1)
            }
        }
        actions()
    }
}

@Composable
fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    val colors = Syncro.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBubble(icon, size = 38.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Spacer(Modifier.width(12.dp))
        // The whole row is the touch target, so the switch itself needs no extra 48dp padding.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.accent,
                    checkedThumbColor = colors.onAccent,
                    uncheckedTrackColor = colors.surfaceHigh,
                    uncheckedBorderColor = colors.outline,
                    uncheckedThumbColor = colors.textMuted,
                ),
            )
        }
    }
}
