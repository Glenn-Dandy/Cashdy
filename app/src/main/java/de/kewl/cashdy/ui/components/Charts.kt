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

data class Slice(val value: Long, val color: Color)

@Composable
fun Donut(slices: List<Slice>, modifier: Modifier = Modifier, size: Dp = 180.dp, center: @Composable () -> Unit = {}) {
    val track = C.colors.line
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.16f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val total = slices.sumOf { it.value }.toFloat()
            if (total <= 0f) {
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                return@Canvas
            }
            var start = -90f
            val gap = if (slices.size > 1) 3f else 0f
            slices.forEach { s ->
                val sweep = s.value / total * 360f
                if (sweep > gap) drawArc(s.color, start + gap / 2, sweep - gap, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        center()
    }
}

data class BarValue(val label: String, val income: Long, val spent: Long, val highlight: Boolean = false)

/** Jahresansicht: je Monat zwei Balken (rein / raus), auf einen gemeinsamen Maßstab gezeichnet. */
@Composable
fun MonthBars(values: List<BarValue>, modifier: Modifier = Modifier, height: Dp = 150.dp) {
    val c = C.colors
    val max = values.maxOfOrNull { maxOf(it.income, it.spent) }?.takeIf { it > 0 } ?: 1L
    Row(modifier.fillMaxWidth().height(height + 22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        values.forEach { v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.height(height), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Bar(v.income.toFloat() / max, c.primary.copy(alpha = if (v.highlight) 1f else 0.55f))
                    Bar(v.spent.toFloat() / max, c.coin.copy(alpha = if (v.highlight) 1f else 0.7f))
                }
                Text(v.label, style = MaterialTheme.typography.labelSmall, color = if (v.highlight) c.fg else c.muted, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(
        Modifier.width(7.dp).fillMaxHeight(fraction.coerceIn(0.015f, 1f))
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
    ) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) { drawRect(color) }
    }
}

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
