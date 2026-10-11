package de.kewl.cashdy.domain

import de.kewl.cashdy.data.Cents
import kotlin.math.abs

object Money {
    /** 128449 -> "1.284,49 €" */
    fun format(c: Cents, withSign: Boolean = false): String {
        val sign = when {
            c < 0 -> "−"
            withSign && c > 0 -> "+"
            else -> ""
        }
        return sign + euros(abs(c)) + "," + (abs(c) % 100).toString().padStart(2, '0') + " €"
    }

    /** Nur Euro, ohne Cent: 128449 -> "1.284" */
    fun euros(c: Cents): String {
        val e = abs(c) / 100
        val s = e.toString().reversed().chunked(3).joinToString(".").reversed()
        return (if (c < 0) "−" else "") + s
    }

    /** ",49 €" */
    fun centsPart(c: Cents): String = "," + (abs(c) % 100).toString().padStart(2, '0') + " €"

    /** Ganze Euro für knappe Anzeigen: "1.284 €" */
    fun short(c: Cents): String = euros(c) + " €"

    /**
     * Liest Beträge so, wie Menschen und Banken sie schreiben:
     * "12,49", "1.234,56", "12.49", "1,234.56", "12", "-12,49 €", "12,49-".
     * Gibt Cent zurück oder null.
     */
    fun parse(input: String): Cents? {
        var s = input.trim()
            .replace("€", "").replace("EUR", "", ignoreCase = true)
            .replace(" ", "").replace(" ", "").replace("−", "-")
        if (s.isEmpty()) return null
        var neg = false
        if (s.startsWith("-")) { neg = true; s = s.drop(1) }
        if (s.endsWith("-")) { neg = true; s = s.dropLast(1) }
        if (s.startsWith("+")) s = s.drop(1)
        if (s.isEmpty() || !s.all { it.isDigit() || it == ',' || it == '.' }) return null
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        val decimalSep: Char? = when {
            lastComma >= 0 && lastDot >= 0 -> if (lastComma > lastDot) ',' else '.'
            lastComma >= 0 -> if (s.length - lastComma - 1 in 1..2) ',' else null
            lastDot >= 0 -> if (s.length - lastDot - 1 in 1..2 && s.count { it == '.' } == 1) '.' else null
            else -> null
        }
        val (whole, frac) = if (decimalSep != null) {
            val i = s.lastIndexOf(decimalSep)
            s.substring(0, i) to s.substring(i + 1)
        } else s to ""
        val wholeDigits = whole.filter { it.isDigit() }
        if (wholeDigits.isEmpty() && frac.isEmpty()) return null
        val cents = (wholeDigits.ifEmpty { "0" }.toLongOrNull() ?: return null) * 100 +
            frac.padEnd(2, '0').take(2).toLong()
        return if (neg) -cents else cents
    }
}
