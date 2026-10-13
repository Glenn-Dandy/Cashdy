package de.kewl.cashdy.domain

import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.data.Recurring
import java.time.LocalDate

/** Stand einer verteilten Position: nächste Rechnung und wie viel dafür im Topf liegt. */
data class PotStatus(
    val recurring: Recurring,
    val nextDue: LocalDate,
    val bill: Cents,
    /** Bis einschließlich zum laufenden Zeitraum angespart. */
    val savedNow: Cents,
    /** Was bis zur Fälligkeit angespart sein wird. */
    val savedAtDue: Cents,
) {
    val missing: Cents get() = (bill - savedAtDue).let { if (it > recurring.intervalMonths) it else 0 }
}

/**
 * Rücklage-Topf für Kosten, die seltener als monatlich kommen und verteilt werden.
 *
 * Jeder Zeitraum legt einen Anteil zurück. Die Rechnung wird aus den Anteilen der letzten
 * [Recurring.intervalMonths] Zeiträume bezahlt (einschließlich des Fälligkeitszeitraums).
 * Zeiträume vor [de.kewl.cashdy.data.Settings.potStart] haben nichts zurückgelegt, im ersten
 * Durchlauf fehlt deshalb meist etwas, das der Fälligkeitsmonat selbst trägt.
 */
object Reserve {

    private fun startPeriod(d: AppData): Period? = d.settings.potStart?.let { Periods.periodFor(it, d.settings) }

    /** Anteil, den Zeitraum [p] für [r] zurücklegt (0, wenn er nicht spart). */
    fun shareIn(d: AppData, r: Recurring, p: Period): Cents {
        if (!r.spread || r.intervalMonths <= 1 || r.income) return 0
        if (!Recurrence.spreadActive(r, p)) return 0
        val start = startPeriod(d)
        if (start != null && p.start.isBefore(start.start)) return 0
        return r.overrides[p.key] ?: Recurrence.monthlyShare(r, p.start)
    }

    /** Für die Rechnung am [due] zurückgelegt, nur Zeiträume bis einschließlich [until]. */
    fun savedFor(d: AppData, r: Recurring, due: LocalDate, until: LocalDate = due): Cents {
        val seen = HashSet<LocalDate>()
        var sum = 0L
        for (k in 0 until r.intervalMonths) {
            val p = Periods.periodFor(due.minusMonths(k.toLong()), d.settings)
            if (!seen.add(p.start) || p.start.isAfter(until)) continue
            sum += shareIn(d, r, p)
        }
        return sum
    }

    fun status(d: AppData, r: Recurring, today: LocalDate = LocalDate.now()): PotStatus? {
        if (!r.spread || r.intervalMonths <= 1 || r.income) return null
        val next = Recurrence.nextDue(r, today) ?: return null
        val nowEnd = Periods.periodFor(today, d.settings).start
        return PotStatus(r, next, Recurrence.priceAt(r, next), savedFor(d, r, next, nowEnd), savedFor(d, r, next))
    }

    /** Alle laufenden Positionen, nächste Rechnung zuerst. */
    fun all(d: AppData, today: LocalDate = LocalDate.now()): List<PotStatus> =
        d.recurring.mapNotNull { status(d, it, today) }.sortedBy { it.nextDue }

    /** Was gerade im Topf liegt. */
    fun total(d: AppData, today: LocalDate = LocalDate.now()): Cents = all(d, today).sumOf { it.savedNow }

    /**
     * Was am Ende von [p] im Topf liegt und deshalb auf dem Konto bleiben muss. Rechnungen, die
     * in [p] schon bezahlt wurden, zählen nicht mehr mit.
     */
    fun heldAfter(d: AppData, p: Period): Cents = d.recurring.sumOf { r ->
        if (!r.spread || r.intervalMonths <= 1 || r.income) 0L
        else Recurrence.nextDue(r, p.end.plusDays(1))?.let { savedFor(d, r, it, p.start) } ?: 0L
    }
}
