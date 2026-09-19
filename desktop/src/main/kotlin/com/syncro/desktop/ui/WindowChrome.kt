package com.syncro.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Minimize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window

/**
 * Frameless window shell from the design: a 36dp title bar in the sidebar colour carrying the mark,
 * a muted "Syncro" label and minimise / maximise / close, plus invisible resize edges.
 */
@Composable
fun FrameWindowScope.WindowChrome(
    state: WindowState,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = Theme.colors
    val maximized = state.placement == WindowPlacement.Maximized
    val toggleMaximize = {
        if (!maximized) window.fitMaximizedBoundsToScreen()
        state.placement = if (maximized) WindowPlacement.Floating else WindowPlacement.Maximized
    }
    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            WindowDraggableArea(Modifier.fillMaxWidth().height(36.dp)) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(c.sidebar)
                        .pointerInput(maximized) { detectTapGestures(onDoubleTap = { toggleMaximize() }) }
                        .padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SyncroMark(15.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Syncro", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.textMuted, modifier = Modifier.weight(1f))
                    WindowButton(onClick = { state.isMinimized = true }) { tint ->
                        Icon(Icons.Rounded.Minimize, "Minimise", tint = tint, modifier = Modifier.size(14.dp))
                    }
                    WindowButton(onClick = toggleMaximize) { tint ->
                        // Material Symbols' filled crop_square, as drawn in the design.
                        Canvas(Modifier.size(12.dp)) {
                            val inset = size.minDimension * 0.125f
                            drawRoundRect(
                                tint,
                                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                                size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                                cornerRadius = CornerRadius(size.minDimension * 0.14f),
                            )
                        }
                    }
                    WindowButton(onClick = onClose, danger = true) { tint ->
                        Icon(Icons.Rounded.Close, "Close", tint = tint, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
        if (!maximized) ResizeEdges(window)
    }
}

@Composable
private fun WindowButton(onClick: () -> Unit, danger: Boolean = false, icon: @Composable (Color) -> Unit) {
    val c = Theme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        !hovered -> Color.Transparent
        danger -> c.danger
        else -> c.surfaceHigh
    }
    Box(
        Modifier
            .size(width = 28.dp, height = 26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon(if (hovered && danger) Color.White else if (hovered) c.text else c.textMuted)
    }
}

/** Makes an undecorated window maximise to the work area instead of covering the taskbar. */
fun Window.fitMaximizedBoundsToScreen() {
    val frame = this as? java.awt.Frame ?: return
    val config = graphicsConfiguration ?: return
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
    val screen = config.bounds
    frame.maximizedBounds = Rectangle(
        screen.x + insets.left,
        screen.y + insets.top,
        screen.width - insets.left - insets.right,
        screen.height - insets.top - insets.bottom,
    )
}

private enum class Edge(val cursor: Int, val left: Boolean = false, val top: Boolean = false, val right: Boolean = false, val bottom: Boolean = false) {
    N(Cursor.N_RESIZE_CURSOR, top = true),
    S(Cursor.S_RESIZE_CURSOR, bottom = true),
    W(Cursor.W_RESIZE_CURSOR, left = true),
    E(Cursor.E_RESIZE_CURSOR, right = true),
    NW(Cursor.NW_RESIZE_CURSOR, left = true, top = true),
    NE(Cursor.NE_RESIZE_CURSOR, right = true, top = true),
    SW(Cursor.SW_RESIZE_CURSOR, left = true, bottom = true),
    SE(Cursor.SE_RESIZE_CURSOR, right = true, bottom = true),
}

@Composable
private fun BoxScope.ResizeEdges(window: Window) {
    val thickness = 5.dp
    val corner = 10.dp
    ResizeHandle(window, Edge.N, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(thickness).padding(horizontal = corner))
    ResizeHandle(window, Edge.S, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(thickness).padding(horizontal = corner))
    ResizeHandle(window, Edge.W, Modifier.align(Alignment.CenterStart).fillMaxHeight().width(thickness).padding(vertical = corner))
    ResizeHandle(window, Edge.E, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(thickness).padding(vertical = corner))
    ResizeHandle(window, Edge.NW, Modifier.align(Alignment.TopStart).size(corner))
    ResizeHandle(window, Edge.NE, Modifier.align(Alignment.TopEnd).size(corner))
    ResizeHandle(window, Edge.SW, Modifier.align(Alignment.BottomStart).size(corner))
    ResizeHandle(window, Edge.SE, Modifier.align(Alignment.BottomEnd).size(corner))
}

@Composable
private fun ResizeHandle(window: Window, edge: Edge, modifier: Modifier) {
    Box(
        modifier
            .pointerHoverIcon(PointerIcon(Cursor(edge.cursor)))
            .pointerInput(edge) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val start = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
                    val bounds = window.bounds
                    val min = window.minimumSize
                    while (true) {
                        val event = awaitPointerEvent()
                        val now = MouseInfo.getPointerInfo()?.location ?: break
                        val dx = now.x - start.x
                        val dy = now.y - start.y
                        var x = bounds.x
                        var y = bounds.y
                        var w = bounds.width
                        var h = bounds.height
                        if (edge.right) w = maxOf(min.width, bounds.width + dx)
                        if (edge.bottom) h = maxOf(min.height, bounds.height + dy)
                        if (edge.left) {
                            w = maxOf(min.width, bounds.width - dx)
                            x = bounds.x + bounds.width - w
                        }
                        if (edge.top) {
                            h = maxOf(min.height, bounds.height - dy)
                            y = bounds.y + bounds.height - h
                        }
                        window.setBounds(x, y, w, h)
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            },
    )
}
