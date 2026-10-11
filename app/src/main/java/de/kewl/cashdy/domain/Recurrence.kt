package de.kewl.cashdy.domain

import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.data.PriceStep
import de.kewl.cashdy.data.Recurring
import java.time.LocalDate

data class Occurrence(val recurring: Recurring, val date: LocalDate, val amount: Cents)

object Recurrence {

    fun intervalLabel(months: Int): String = when (months) {
        1 -> "monatlich"
        2 -> "alle 2 Monate"
        3 -> "vierteljährlich"
        6 -> "halbjährlich"
        12 -> "jährlich"
        else -> "alle $months Monate"
    }

    /** Preis, der am Datum [d] gilt: der jüngste Preisschritt mit from <= d (sonst der erste). */
    fun priceAt(r: Recurring, d: LocalDate): Cents {
        val sorted = r.prices.sortedBy { it.from }
        return (sorted.lastOrNull { !it.from.isAfter(d) } ?: sorted.firstOrNull())?.amount ?: 0
    }

    fun currentPrice(r: Recurring, today: LocalDate = LocalDate.now()) = priceAt(r, today)

    /** Alle Fälligkeiten im Bereich [from]..[to]. Basis ist immer firstDue, damit der 31. nicht wandert. */
    fun dueDates(r: Recurring, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (r.intervalMonths <= 0) return emptyList()
        val out = mutableListOf<LocalDate>()
        // Grob zum Bereich vorspulen statt ab firstDue jede Fälligkeit durchzugehen.
        val monthsBetween = (from.year - r.firstDue.year) * 12 + (from.monthValue - r.firstDue.monthValue)
        var k = maxOf(0, monthsBetween / r.intervalMonths - 1)
        while (true) {
            val d = r.firstDue.plusMonths((k * r.intervalMonths).toLong())
            if (d.isAfter(to)) break
            if (r.endDate != null && d.isAfter(r.endDate)) break
            if (!d.isBefore(from)) out += d
            k++
        }
        return out
    }

    /** Nächste Fälligkeit ab [today] (einschließlich). */
    fun nextDue(r: Recurring, today: LocalDate): LocalDate? =
        dueDates(r, today, today.plusMonths(r.intervalMonths.toLong() + 1)).firstOrNull()

    /** Monatlicher Anteil einer verteilten Zahlung (Rücklage). */
    fun monthlyShare(r: Recurring, at: LocalDate): Cents =
        if (r.intervalMonths <= 0) 0 else priceAt(r, at) / r.intervalMonths

    /** Läuft die verteilte Position in diesem Zeitraum? Gespart wird ab einem Intervall vor der ersten Fälligkeit. */
    fun spreadActive(r: Recurring, p: Period): Boolean {
        val saveFrom = r.firstDue.minusMonths(r.intervalMonths.toLong()).plusDays(1)
        if (p.end.isBefore(saveFrom)) return false
        if (r.endDate != null && p.start.isAfter(r.endDate)) return false
        return true
    }

    /** Hinweis wie "seit 2024 um 7,7 % teurer", wenn sich der Preis verändert hat. */
    fun changeHint(r: Recurring, today: LocalDate = LocalDate.now()): String? {
        val sorted = r.prices.sortedBy { it.from }
        if (sorted.size < 2) return null
        val first = sorted.first()
        val now = priceAt(r, today)
        if (first.amount <= 0 || now == first.amount) return null
        val pct = (now - first.amount) * 1000 / first.amount
        val pctText = "${kotlin.math.abs(pct) / 10},${kotlin.math.abs(pct) % 10} %"
        val word = if (now > first.amount) "teurer" else "günstiger"
        return "seit ${first.from.year} um $pctText $word"
    }

    fun withNewPrice(r: Recurring, from: LocalDate, amount: Cents): Recurring {
        val rest = r.prices.filter { it.from != from }
        return r.copy(prices = (rest + PriceStep(from, amount)).sortedBy { it.from })
    }
}
