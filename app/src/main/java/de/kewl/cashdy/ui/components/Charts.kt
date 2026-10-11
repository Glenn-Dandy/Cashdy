package de.kewl.cashdy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.kewl.cashdy.ui.theme.C

/** Dicke, runde Fortschrittsleiste mit kleiner Münze am Ende. */
@Composable
fun CoinBar(fraction: Float, track: Color, fill: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val h = size.height
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
        val w = (size.width * fraction.coerceIn(0f, 1f)).coerceAtLeast(h)
        drawRoundRect(fill, size = Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
    }
}
