package com.syncro.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.syncro.desktop.DesktopSettings
import com.syncro.desktop.Platform
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/** Outlined field from the design: 52dp tall (140dp multiline), 12dp radius, 2dp accent border on focus. */
@Composable
fun SyncroInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    multiline: Boolean = false,
) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Box(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = !multiline,
            interactionSource = interaction,
            cursorBrush = SolidColor(c.accent),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (multiline) Modifier.heightIn(min = 140.dp) else Modifier.height(52.dp))
                .border(if (focused) 2.dp else 1.dp, if (focused) c.accent else c.outline, shape),
            decorationBox = { inner ->
                Box(
                    Modifier.padding(horizontal = 14.dp, vertical = if (multiline) 14.dp else 0.dp),
                    contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.textMuted.copy(alpha = 0.75f))
                    }
                    inner()
                }
            },
        )
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                color = if (focused) c.accent else c.textMuted,
                modifier = Modifier
                    .offset(x = 12.dp, y = (-7).dp)
                    .background(c.surface)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

/** Bare accent text action (desktop scale). */
@Composable
fun TextAction(text: String, onClick: () -> Unit, color: Color = Theme.colors.accent, icon: ImageVector? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** Scrim + 28dp-radius surface card, the frame every desktop dialog shares. */
@Composable
fun DialogFrame(width: Dp, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Theme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(width)
                .clip(RoundedCornerShape(28.dp))
                .background(c.surface)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(26.dp),
            content = content,
        )
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = Theme.colors.text)
}

@Composable
fun SendTextDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    DialogFrame(420.dp, onDismiss) {
        DialogTitle("Send text")
        Spacer(Modifier.height(16.dp))
        SyncroInput(text, { text = it }, Modifier.fillMaxWidth(), placeholder = "A note, link or code…", multiline = true)
        Spacer(Modifier.height(6.dp))
        TextAction("Paste clipboard", { Platform.clipboardText()?.let { text = it } }, icon = Icons.Rounded.ContentPaste)
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            SecondaryButton("Cancel", onClick = onDismiss)
            PrimaryButton("Add", onClick = { onAdd(text) }, enabled = text.isNotBlank())
        }
    }
}

@Composable
fun ChooseFolderDialog(current: String, window: java.awt.Window, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val c = Theme.colors
    val home = System.getProperty("user.home")
    val options = remember(current) {
        listOf(current, DesktopSettings.defaultDownloads().absolutePath, File(home, "Documents").absolutePath, File(home, "Desktop").absolutePath)
            .distinct()
            .filter { File(it).let { f -> f.isDirectory || it == current || f.parentFile?.isDirectory == true } }
    }
    var selected by remember(current) { mutableStateOf(current) }
    DialogFrame(460.dp, onDismiss) {
        DialogTitle("Choose where Syncro saves files")
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.surfaceHigh)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { path ->
                val isSelected = path == selected
                FolderRow(Icons.Rounded.Folder, path, if (isSelected) c.accent else c.text, if (isSelected) c.accent else c.textMuted) { selected = path }
            }
            FolderRow(Icons.Rounded.FolderOpen, "Browse…", c.textMuted, c.textMuted) {
                val previous = UIManager.getLookAndFeel()
                runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
                val chooser = JFileChooser(selected).apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Choose where Syncro saves files"
                }
                if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) onSelect(chooser.selectedFile.absolutePath)
                runCatching { UIManager.setLookAndFeel(previous) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            SecondaryButton("Cancel", onClick = onDismiss)
            PrimaryButton("Select folder", onClick = { onSelect(selected) })
        }
    }
}

@Composable
private fun FolderRow(icon: ImageVector, text: String, textColor: Color, iconColor: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
