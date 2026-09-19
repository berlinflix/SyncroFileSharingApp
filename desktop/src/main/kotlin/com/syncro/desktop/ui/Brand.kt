package com.syncro.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Brand violet — the mark stays this colour in both themes. */
val BrandMark = Color(0xFF7C5CFF)

/** The Syncro mark: two arrows cycling, one flat colour (design system assets/logo.svg). */
@Composable
fun SyncroMark(size: Dp, modifier: Modifier = Modifier, color: Color = BrandMark) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 108f
        val arc = Path().apply { arcTo(Rect(Offset(24f * s, 24f * s), Size(60f * s, 60f * s)), 200f, 130f, true) }
        val head = Path().apply {
            moveTo(87.98f * s, 52.86f * s)
            lineTo(89.07f * s, 33.75f * s)
            lineTo(70.89f * s, 44.25f * s)
            close()
        }
        val stroke = Stroke(width = 9f * s, cap = StrokeCap.Butt)
        drawPath(arc, color, style = stroke)
        drawPath(head, color)
        rotate(180f, pivot = center) {
            drawPath(arc, color, style = stroke)
            drawPath(head, color)
        }
    }
}

/** Mark + "Syncro" lockup for the sidebar. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SyncroMark(32.dp)
        Text("Syncro", style = MaterialTheme.typography.headlineSmall, color = Theme.colors.text)
    }
}
