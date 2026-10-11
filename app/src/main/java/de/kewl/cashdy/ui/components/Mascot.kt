package de.kewl.cashdy.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.kewl.cashdy.ui.theme.C

enum class Mood { HAPPY, OK, WORRIED, PARTY, SLEEPY }

/**
 * Cashdy, die Münze. Gezeichnet statt als Bild, damit sie je nach Lage das Gesicht
 * wechseln kann und in jeder Größe scharf bleibt.
 */
@Composable
fun Mascot(mood: Mood, modifier: Modifier = Modifier, size: Dp = 96.dp, animate: Boolean = true, tilt: Float = 10f) {
    val coin = C.colors.coin
    val ink = C.colors.ink
    val wobble = if (animate) {
        val t = rememberInfiniteTransition(label = "wobble")
        val v by t.animateFloat(
            initialValue = -1f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "w",
        )
        v
    } else 0f
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 100f
        val bounce = if (mood == Mood.PARTY) wobble * 6f else wobble * 3f
        rotate(tilt + wobble * 4f, Offset(50 * s, 50 * s)) {
            translate(top = bounce * s) {
                drawCircle(coin, 44 * s, Offset(50 * s, 50 * s))
                drawCircle(ink.copy(alpha = 0.18f), 35 * s, Offset(50 * s, 50 * s), style = Stroke(4 * s))
                // Bäckchen
                drawOval(Color(0x73FF7A7A), Offset(24 * s, 54 * s), Size(12 * s, 8 * s))
                drawOval(Color(0x73FF7A7A), Offset(64 * s, 54 * s), Size(12 * s, 8 * s))
                // Augen
                when (mood) {
                    Mood.SLEEPY -> {
                        drawLine(ink, Offset(33 * s, 46 * s), Offset(43 * s, 46 * s), 4 * s, StrokeCap.Round)
                        drawLine(ink, Offset(57 * s, 46 * s), Offset(67 * s, 46 * s), 4 * s, StrokeCap.Round)
                    }
                    Mood.PARTY -> {
                        // ^ ^
                        val l = Path().apply { moveTo(32 * s, 48 * s); lineTo(38 * s, 41 * s); lineTo(44 * s, 48 * s) }
                        val r = Path().apply { moveTo(56 * s, 48 * s); lineTo(62 * s, 41 * s); lineTo(68 * s, 48 * s) }
                        drawPath(l, ink, style = Stroke(4 * s, cap = StrokeCap.Round))
                        drawPath(r, ink, style = Stroke(4 * s, cap = StrokeCap.Round))
                    }
                    else -> {
                        drawCircle(ink, 5 * s, Offset(38 * s, 45 * s))
                        drawCircle(ink, 5 * s, Offset(62 * s, 45 * s))
                        drawCircle(Color.White, 1.6f * s, Offset(40 * s, 43 * s))
                        drawCircle(Color.White, 1.6f * s, Offset(64 * s, 43 * s))
                    }
                }
                if (mood == Mood.WORRIED) {
                    drawLine(ink, Offset(31 * s, 35 * s), Offset(41 * s, 32 * s), 3 * s, StrokeCap.Round)
                    drawLine(ink, Offset(69 * s, 35 * s), Offset(59 * s, 32 * s), 3 * s, StrokeCap.Round)
                }
                // Mund
                val mouth = Path()
                when (mood) {
                    Mood.HAPPY -> { mouth.moveTo(40 * s, 60 * s); mouth.quadraticTo(50 * s, 70 * s, 60 * s, 60 * s) }
                    Mood.PARTY -> {
                        val open = Path().apply {
                            moveTo(38 * s, 59 * s); quadraticTo(50 * s, 76 * s, 62 * s, 59 * s); close()
                        }
                        drawPath(open, ink)
                    }
                    Mood.OK -> { mouth.moveTo(42 * s, 62 * s); mouth.quadraticTo(50 * s, 66 * s, 58 * s, 62 * s) }
                    Mood.WORRIED -> { mouth.moveTo(40 * s, 66 * s); mouth.quadraticTo(50 * s, 58 * s, 60 * s, 66 * s) }
                    Mood.SLEEPY -> { mouth.moveTo(45 * s, 63 * s); mouth.quadraticTo(50 * s, 65 * s, 55 * s, 63 * s) }
                }
                if (mood != Mood.PARTY) drawPath(mouth, ink, style = Stroke(4.5f * s, cap = StrokeCap.Round))
            }
        }
    }
}
