package de.kewl.cashdy.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** HOPE: Geld wird knapp. Die Münze jubelt dir zu, statt sich Sorgen zu machen. */
enum class Mood { HAPPY, OK, HOPE, PARTY, SLEEPY }

/** Kleine Anstöße von außen, z. B. ein Hüpfer, wenn gerade etwas eingetragen wurde. */
object MascotEvents {
    val hop = mutableIntStateOf(0)
    fun hop() { hop.intValue++ }
}

// Honiggold, mit dunklerem Rand und Riffelkante, damit man die Münze sofort erkennt.
private val Face = Color(0xFFFFC94D)
private val Rim = Color(0xFFF0A52A)
private val Edge = Color(0xFFD98E1A)
private val Ring = Color(0xFFE9A12E)
private val Ink = Color(0xFF2A2150)
private val Cheek = Color(0x73FF7A7A)
private val Spark = Color(0xFFFFB020)
private val Heart = Color(0xFFFF6F91)
private val Tongue = Color(0xFFFF7A8A)

/**
 * Cashdy, die Münze. Gezeichnet statt als Bild, damit sie je nach Lage das Gesicht
 * wechseln kann und in jeder Größe scharf bleibt.
 *
 * Immer: blinzeln, leise atmen, ab und zu ein Glanz. Antippen: einmal drehen mit Herz.
 * Nach [MascotEvents.hop]: ein Hüpfer. [wave]: mit einem Ärmchen winken (Begrüßung).
 * Sind die Animationen in Android ausgeschaltet, bleibt sie ruhig.
 */
@Composable
fun Mascot(mood: Mood, modifier: Modifier = Modifier, size: Dp = 96.dp, animate: Boolean = true, wave: Boolean = false) {
    val ctx = LocalContext.current
    val motion = animate && remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) > 0f
    }
    val t = rememberInfiniteTransition(label = "coin")
    fun loop(ms: Int) = infiniteRepeatable<Float>(tween(ms, easing = LinearEasing), RepeatMode.Restart)
    // Eine Uhr pro Bewegung, jeweils 0..1. Ohne Bewegung bleiben alle auf ihrem Ruhewert.
    val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), "breathe")
    val blinkT by t.animateFloat(0f, 1f, loop(4200), "blink")
    val shineT by t.animateFloat(0f, 1f, loop(7000), "shine")
    val beat by t.animateFloat(0f, 1f, loop(if (mood == Mood.PARTY) 1100 else 2800), "beat")
    val waveT by t.animateFloat(0f, 1f, loop(1600), "wave")
    val zT by t.animateFloat(0f, 1f, loop(3000), "z")

    val flip = remember { Animatable(0f) }
    val heart = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val hopCount = MascotEvents.hop.intValue
    // Nur Hüpfer, die passieren, während die Münze zu sehen ist, nicht ältere beim Öffnen.
    val seen = remember { intArrayOf(hopCount) }
    LaunchedEffect(hopCount) {
        if (hopCount != seen[0] && motion) {
            seen[0] = hopCount
            hop.snapTo(0f)
            hop.animateTo(1f, tween(700, easing = LinearEasing))
            hop.snapTo(0f)
        }
    }

    Canvas(
        modifier.size(size)
            .graphicsLayer {
                rotationY = flip.value * 360f
                cameraDistance = 12f * density
            }
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                if (!motion) return@clickable
                scope.launch { flip.snapTo(0f); flip.animateTo(1f, tween(900, easing = FastOutSlowInEasing)); flip.snapTo(0f) }
                scope.launch { heart.snapTo(0f); heart.animateTo(1f, tween(1000)); heart.snapTo(0f) }
            },
    ) {
        val s = this.size.minDimension / 100f
        val m = if (motion) 1f else 0f
        // Atmen, Hüpfen (mit Stauchen und Strecken), Stimmungs-Bewegung
        var dy = -3f * breathe * m
        var sx = 1f + 0.02f * breathe * m
        var sy = sx
        var rot = 0f
        if (motion) {
            hopCurve(hop.value).let { (y, ax, ay) -> dy += y; sx *= ax; sy *= ay }
            when (mood) {
                Mood.PARTY -> { val p = sin(beat * 2 * PI).toFloat(); dy -= 8f * kotlin.math.abs(p); rot = 7f * p }
                Mood.HOPE -> { val k = pulse(beat, 0.6f, 0.86f); dy -= 5f * k; rot = -2f * k }
                Mood.SLEEPY -> rot = 4f * sin(zT * 2 * PI).toFloat()
                else -> {}
            }
            dy -= 16f * sin(flip.value * PI).toFloat()
        }
        val shadow = 1f - (-dy).coerceIn(0f, 20f) / 30f
        drawOval(Ink.copy(alpha = 0.18f * shadow), Offset((50 - 26 * shadow) * s, 100 * s), Size(52 * shadow * s, 9 * s))

        translate(top = dy * s) {
            rotate(rot, Offset(50 * s, 50 * s)) {
                scale(sx, sy, Offset(50 * s, 96 * s)) {
                    if (mood == Mood.HOPE) cheerArms(s, if (motion) pulse(beat, 0.6f, 0.86f) else 0f)
                    if (wave) waveArm(s, if (motion) sin(waveT * 2 * PI).toFloat() else 0f)
                    coinBody(s, if (motion) shineT else 0f)
                    face(s, mood, if (motion) blink(blinkT) else 1f)
                    if (mood == Mood.HOPE) sparkles(s, if (motion) pulse(beat, 0.62f, 0.9f) else 0.7f)
                }
            }
        }
        if (mood == Mood.SLEEPY && motion) sleepyZ(s, zT)
        if (heart.value > 0f) {
            val h = heart.value
            val a = if (h < 0.25f) h / 0.25f else 1f - (h - 0.25f) / 0.75f
            drawHeart(Offset(50 * s, (4 - 30 * h) * s), (0.6f + 0.6f * h) * s, Heart.copy(alpha = a.coerceIn(0f, 1f)))
        }
    }
}

/** Hüpfer: kurz einfedern, hoch, landen, nachfedern. Gibt (dy, scaleX, scaleY). */
private fun hopCurve(v: Float): Triple<Float, Float, Float> = when {
    v <= 0f -> Triple(0f, 1f, 1f)
    v < 0.15f -> { val k = v / 0.15f; Triple(2f * k, 1f + 0.08f * k, 1f - 0.1f * k) }
    v < 0.45f -> { val k = (v - 0.15f) / 0.3f; Triple(2f - 22f * sin(k * PI / 2).toFloat(), 1.08f - 0.13f * k, 0.9f + 0.16f * k) }
    v < 0.7f -> { val k = (v - 0.45f) / 0.25f; Triple(-20f + 20f * k * k, 0.95f, 1.06f) }
    v < 0.85f -> { val k = (v - 0.7f) / 0.15f; Triple(0f, 0.95f + 0.11f * sin(k * PI).toFloat(), 1.06f - 0.14f * sin(k * PI).toFloat()) }
    else -> Triple(0f, 1f, 1f)
}

/** Zwinkern: kurz vor Schluss jeder Runde gehen die Augen zu. 1 = offen. */
private fun blink(t: Float): Float = when {
    t < 0.9f -> 1f
    t < 0.93f -> 1f - (t - 0.9f) / 0.03f * 0.92f
    t < 0.96f -> 0.08f + (t - 0.93f) / 0.03f * 0.92f
    else -> 1f
}

/** 0 außerhalb von [from]..[to], dazwischen ein weicher Buckel bis 1. */
private fun pulse(t: Float, from: Float, to: Float): Float =
    if (t < from || t > to) 0f else sin((t - from) / (to - from) * PI).toFloat()

private fun DrawScope.coinBody(s: Float, shineT: Float) {
    val c = Offset(50 * s, 50 * s)
    drawCircle(Rim, 46 * s, c)
    // Riffelkante: ein fein gestrichelter Kreis
    val dash = (2 * PI * 45.8 * s / 120).toFloat()
    drawCircle(Edge.copy(alpha = 0.8f), 45.8f * s, c, style = Stroke(2f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * 0.5f, dash * 0.5f))))
    drawCircle(Face, 39 * s, c)
    drawCircle(Ring.copy(alpha = 0.7f), 35 * s, c, style = Stroke(2 * s))
    // Glanz: ein schräger Lichtstreifen, der ab und zu über die Münze wandert.
    if (shineT > 0.6f) {
        val k = ((shineT - 0.6f) / 0.25f).coerceIn(0f, 1f)
        val x = (-20 + 140 * k) * s
        val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, 46 * s)) }
        clipPath(clip) {
            rotate(25f, Offset(x, 50 * s)) {
                drawRect(Color.White.copy(alpha = 0.35f), Offset(x - 5 * s, -20 * s), Size(10 * s, 140 * s))
            }
        }
    }
}

private fun DrawScope.face(s: Float, mood: Mood, open: Float) {
    drawOval(Cheek, Offset(24 * s, 54 * s), Size(12 * s, 8 * s))
    drawOval(Cheek, Offset(64 * s, 54 * s), Size(12 * s, 8 * s))
    val stroke = { w: Float -> Stroke(w * s, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    // Augen
    when (mood) {
        Mood.SLEEPY -> {
            drawPath(Path().apply { moveTo(33 * s, 47 * s); quadraticTo(38 * s, 50 * s, 43 * s, 47 * s) }, Ink, style = stroke(3.5f))
            drawPath(Path().apply { moveTo(57 * s, 47 * s); quadraticTo(62 * s, 50 * s, 67 * s, 47 * s) }, Ink, style = stroke(3.5f))
        }
        Mood.PARTY -> {
            drawPath(Path().apply { moveTo(32 * s, 48 * s); lineTo(38 * s, 41 * s); lineTo(44 * s, 48 * s) }, Ink, style = stroke(4f))
            drawPath(Path().apply { moveTo(56 * s, 48 * s); lineTo(62 * s, 41 * s); lineTo(68 * s, 48 * s) }, Ink, style = stroke(4f))
        }
        else -> scale(1f, open, Offset(50 * s, 45 * s)) {
            val big = mood == Mood.HOPE
            val r = if (big) 6f else 5f
            drawCircle(Ink, r * s, Offset(38 * s, 45 * s))
            drawCircle(Ink, r * s, Offset(62 * s, 45 * s))
            val hl = if (big) 2.4f else 1.6f
            drawCircle(Color.White, hl * s, Offset((if (big) 40.5f else 40f) * s, (if (big) 42.5f else 43f) * s))
            drawCircle(Color.White, hl * s, Offset((if (big) 64.5f else 64f) * s, (if (big) 42.5f else 43f) * s))
            if (big) {
                drawCircle(Color.White, 1.1f * s, Offset(36 * s, 47.5f * s))
                drawCircle(Color.White, 1.1f * s, Offset(60 * s, 47.5f * s))
            }
        }
    }
    // Mund
    when (mood) {
        Mood.HAPPY -> drawPath(Path().apply { moveTo(40 * s, 60 * s); quadraticTo(50 * s, 70 * s, 60 * s, 60 * s) }, Ink, style = stroke(4.5f))
        Mood.OK -> drawPath(Path().apply { moveTo(42 * s, 62 * s); quadraticTo(50 * s, 66 * s, 58 * s, 62 * s) }, Ink, style = stroke(4.5f))
        Mood.SLEEPY -> drawOval(Ink, Offset(46.5f * s, 60 * s), Size(7 * s, 6 * s))
        Mood.PARTY -> drawPath(Path().apply { moveTo(38 * s, 59 * s); quadraticTo(50 * s, 76 * s, 62 * s, 59 * s); close() }, Ink)
        Mood.HOPE -> {
            val mouth = Path().apply { moveTo(36 * s, 57 * s); quadraticTo(50 * s, 77 * s, 64 * s, 57 * s); quadraticTo(50 * s, 61 * s, 36 * s, 57 * s); close() }
            drawPath(mouth, Ink)
            drawPath(mouth, Ink, style = stroke(2f))
            drawPath(Path().apply { moveTo(44 * s, 66.5f * s); quadraticTo(50 * s, 64 * s, 56 * s, 66.5f * s); quadraticTo(50 * s, 71 * s, 44 * s, 66.5f * s); close() }, Tongue)
        }
    }
}

/** Beide Ärmchen jubelnd nach oben, mit runden Fäusten. [k] 0..1 lässt sie mitwippen. */
private fun DrawScope.cheerArms(s: Float, k: Float) {
    fun arm(side: Float) {
        val pivot = Offset((50 + 38 * side) * s, 42 * s)
        rotate(12f * k * side, pivot) {
            val p = Path().apply {
                moveTo((50 + 38 * side) * s, 42 * s)
                quadraticTo((50 + 54 * side) * s, 34 * s, (50 + 56 * side) * s, 12 * s)
            }
            drawPath(p, Edge, style = Stroke(6 * s, cap = StrokeCap.Round))
            drawCircle(Rim, 6.5f * s, Offset((50 + 56 * side) * s, 8 * s))
            drawCircle(Edge, 6.5f * s, Offset((50 + 56 * side) * s, 8 * s), style = Stroke(2.5f * s))
        }
    }
    arm(-1f); arm(1f)
}

/** Links ein Ärmchen locker unten, rechts eins hoch, das winkt. [w] -1..1. */
private fun DrawScope.waveArm(s: Float, w: Float) {
    fun fist(x: Float, y: Float) {
        drawCircle(Rim, 6.5f * s, Offset(x * s, y * s))
        drawCircle(Edge, 6.5f * s, Offset(x * s, y * s), style = Stroke(2.5f * s))
    }
    drawPath(Path().apply { moveTo(12 * s, 64 * s); quadraticTo(-2 * s, 68 * s, -3 * s, 82 * s) }, Edge, style = Stroke(6 * s, cap = StrokeCap.Round))
    fist(-3f, 85f)
    rotate(16f * w, Offset(88 * s, 42 * s)) {
        drawPath(Path().apply { moveTo(88 * s, 42 * s); quadraticTo(104 * s, 34 * s, 106 * s, 12 * s) }, Edge, style = Stroke(6 * s, cap = StrokeCap.Round))
        fist(106f, 8f)
    }
}

private fun DrawScope.sparkles(s: Float, k: Float) {
    fun star(cx: Float, cy: Float, r: Float, a: Float) {
        if (a <= 0f) return
        val p = Path().apply {
            for (i in 0 until 8) {
                val ang = i * PI / 4 - PI / 2
                val rr = if (i % 2 == 0) r else r * 0.35f
                val x = cx + rr * cos(ang).toFloat(); val y = cy + rr * sin(ang).toFloat()
                if (i == 0) moveTo(x * s, y * s) else lineTo(x * s, y * s)
            }
            close()
        }
        drawPath(p, Spark.copy(alpha = a))
    }
    star(18f, -2f, 6f * (0.5f + k), k.coerceAtLeast(0.15f))
    star(84f, 2f, 4.5f * (0.5f + k), k.coerceAtLeast(0.15f))
}

private fun DrawScope.sleepyZ(s: Float, t: Float) {
    fun z(phase: Float, size: Float) {
        val v = (t + phase) % 1f
        val a = if (v < 0.2f) v / 0.2f else 1f - (v - 0.2f) / 0.8f
        val x = (74 + 12 * v) * s; val y = (26 - 24 * v) * s; val w = size * (0.6f + 0.5f * v) * s
        val p = Path().apply { moveTo(x, y); lineTo(x + w, y); lineTo(x, y + w); lineTo(x + w, y + w) }
        drawPath(p, Color(0xFF6C4DF6).copy(alpha = a.coerceIn(0f, 1f)), style = Stroke(1.8f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    z(0f, 8f); z(0.5f, 6f)
}

private fun DrawScope.drawHeart(c: Offset, k: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + 6 * k)
        cubicTo(c.x - 10 * k, c.y - 1 * k, c.x - 6 * k, c.y - 9 * k, c.x, c.y - 4 * k)
        cubicTo(c.x + 6 * k, c.y - 9 * k, c.x + 10 * k, c.y - 1 * k, c.x, c.y + 6 * k)
        close()
    }
    drawPath(p, color)
}
