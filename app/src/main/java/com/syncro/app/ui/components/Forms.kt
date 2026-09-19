package com.syncro.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.syncro.app.ui.theme.Syncro

/**
 * Outlined text field from the design system: 56dp tall (140dp multiline), 16dp radius,
 * 1dp outline that turns into a 2dp accent border on focus, optional notched label.
 */
@Composable
fun SyncroInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    labelBackground: Color = Syncro.colors.surface,
    multiline: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = Syncro.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = !multiline,
            interactionSource = interaction,
            keyboardOptions = keyboardOptions,
            cursorBrush = SolidColor(colors.accent),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (multiline) Modifier.heightIn(min = 140.dp) else Modifier.height(56.dp))
                .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else colors.outline, shape),
            decorationBox = { inner ->
                Box(
                    Modifier.padding(horizontal = 16.dp, vertical = if (multiline) 14.dp else 0.dp),
                    contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
                    }
                    inner()
                }
            },
        )
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                color = if (focused) colors.accent else colors.textMuted,
                modifier = Modifier
                    .offset(x = 12.dp, y = (-7).dp)
                    .background(labelBackground)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

/**
 * The design system's alert sheet: surface, 28dp radius, 24dp padding, 22sp title and
 * accent text actions aligned to the end.
 */
@Composable
fun SyncroAlertDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String? = "Cancel",
    confirmEnabled: Boolean = true,
    confirmColor: Color = Syncro.colors.accent,
    content: @Composable () -> Unit,
) {
    val colors = Syncro.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surface)
                .padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.text)
            Spacer(Modifier.height(16.dp))
            content()
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (dismissText != null) DialogAction(dismissText, colors.accent, true, onDismiss)
                DialogAction(confirmText, if (confirmEnabled) confirmColor else colors.textMuted, confirmEnabled, onConfirm)
            }
        }
    }
}

@Composable
private fun DialogAction(text: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
