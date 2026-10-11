package de.kewl.cashdy.ocr

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Eine Textzeile, wie die Texterkennung sie liefert, mit Lage im Foto (Pixel). */
@Serializable
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val angle: Float = 0f)

/**
 * Setzt die Zeilen eines Kassenbons wieder zusammen.
 *
 * Die Texterkennung liefert Spalten getrennt: "SUMME" links und "69,15" rechts kommen als
 * zwei Blöcke, oft weit auseinander im Text. Hier wird nach der Höhe im Foto sortiert und
 * alles, was auf einer Höhe liegt, wieder zu einer Zeile verbunden. Ein schief gehaltenes
 * Foto wird vorher anhand der mittleren Schräglage der Zeilen gerade gedreht.
 */
object ReceiptLayout {

    fun rows(lines: List<OcrLine>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val angle = Math.toRadians(lines.map { it.angle.toDouble() }.sorted()[lines.size / 2])
        val height = lines.map { (it.bottom - it.top).toDouble() }.sorted()[lines.size / 2].coerceAtLeast(1.0)
        data class P(val line: OcrLine, val x: Double, val y: Double)
        val points = lines.map { l ->
            val cx = (l.left + l.right) / 2.0
            val cy = (l.top + l.bottom) / 2.0
            // Um -angle drehen, damit Zeilen waagerecht liegen.
            P(l, cx * cos(angle) + cy * sin(angle), -cx * sin(angle) + cy * cos(angle))
        }.sortedBy { it.y }

        val rows = mutableListOf<MutableList<P>>()
        for (p in points) {
            val row = rows.lastOrNull()
            if (row != null && abs(p.y - row.map { it.y }.average()) < height * 0.55) row += p else rows += mutableListOf(p)
        }
        return rows.map { r -> r.sortedBy { it.x - (it.line.right - it.line.left) / 2.0 }.joinToString("  ") { it.line.text.trim() } }
    }
}
