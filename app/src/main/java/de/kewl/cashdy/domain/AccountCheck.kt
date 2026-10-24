package de.kewl.cashdy.domain

import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.data.Detected
import de.kewl.cashdy.data.DetectedStatus
import de.kewl.cashdy.data.Entry
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.PayMethod
import java.time.LocalDate

/**
 * Kontostand prüfen: Was müsste laut Cashdy gerade auf dem Konto sein? Gerechnet wird vom
 * letzten bekannten Kontostand aus, also der letzten Prüfung oder dem Start mit Kontostand.
 * Bargeld zählt nicht, nur was übers Konto läuft.
 */
object AccountCheck {

    /**
     * Ab hier wird gerechnet. Einträge am [date] zählen nur, wenn sie nach [after] angelegt wurden
     * (null = alle Einträge dieses Tages), Fixkosten erst ab dem Tag danach.
     */
    data class Anchor(val date: LocalDate, val balance: Cents, val after: Long?, val fromStart: Boolean)

    sealed interface Match {
        /** Steht auf bar, ging aber vielleicht vom Konto (oder umgekehrt). */
        data class WrongMethod(val entry: Entry) : Match
        /** Erkannte Zahlung, die noch auf Bestätigung wartet. */
        data class Pending(val detected: Detected) : Match
    }

    fun anchor(d: AppData): Anchor? {
        val s = d.settings
        s.bankCheck?.let { return Anchor(it.date, it.balance, it.at, fromStart = false) }
        val day = s.startDay ?: return null
        val balance = s.startBalance ?: return null
        return Anchor(day, balance, null, fromStart = true)
    }

    /** Wie viel bewegt dieser Eintrag auf dem Konto? Bargeld bleibt außen vor. */
    fun bankValue(e: Entry): Cents = when (e.kind) {
        EntryKind.EXPENSE -> if (e.method == PayMethod.CASH) 0 else -e.amount
        EntryKind.REFUND, EntryKind.INCOME -> if (e.method == PayMethod.CASH) 0 else e.amount
        EntryKind.SAVING, EntryKind.CASH_WITHDRAWAL -> -e.amount
        EntryKind.CASH_ADJUST, EntryKind.CASH_START -> 0
    }

    private fun counts(e: Entry, a: Anchor, today: LocalDate): Boolean = when {
        e.date.isBefore(a.date) || e.date.isAfter(today) -> false
        e.date == a.date && a.after != null -> e.createdAt > a.after
        else -> true
    }

    /** Laut Cashdy auf dem Konto am [today], oder null, wenn noch kein Kontostand bekannt ist. */
    fun expected(d: AppData, today: LocalDate = LocalDate.now()): Cents? {
        val a = anchor(d) ?: return null
        var bal = a.balance
        bal += d.entries.filter { counts(it, a, today) }.sumOf { bankValue(it) }
        if (today.isAfter(a.date)) {
            for (r in d.recurring) {
                for (due in Recurrence.dueDates(r, a.date.plusDays(1), today)) {
                    val price = r.overrides[Periods.periodFor(due, d.settings).key] ?: Recurrence.priceAt(r, due)
                    bal += if (r.income) price else -price
                }
            }
        }
        return bal
    }

    /**
     * Einträge, die genau die Differenz erklären könnten. [diff] = laut Cashdy minus echt,
     * also positiv, wenn auf dem Konto weniger ist.
     */
    fun matches(d: AppData, diff: Cents, today: LocalDate = LocalDate.now()): List<Match> {
        if (diff == 0L) return emptyList()
        val since = (anchor(d)?.date ?: today).minusDays(1)
        val wrong = d.entries.filter { e ->
            e.kind == EntryKind.EXPENSE && !e.date.isBefore(since) && !e.date.isAfter(today) &&
                // Konto hat weniger: ein Bar-Eintrag ging wohl vom Konto. Konto hat mehr: umgekehrt.
                (if (diff > 0) e.method == PayMethod.CASH && e.amount == diff else e.method != PayMethod.CASH && e.amount == -diff)
        }.sortedByDescending { it.date }.map { Match.WrongMethod(it) }
        val pending = if (diff > 0) d.detected.filter { it.status == DetectedStatus.PENDING && it.amount == diff }.map { Match.Pending(it) } else emptyList()
        return (pending + wrong).take(3)
    }

    /** Bargeld-Abgleich: Mehr Bargeld als gedacht? Dann war vielleicht ein Bar-Eintrag gar nicht bar. */
    fun notCash(d: AppData, extra: Cents, today: LocalDate = LocalDate.now()): Entry? =
        d.entries.filter { it.kind == EntryKind.EXPENSE && it.method == PayMethod.CASH && it.amount == extra && !it.date.isBefore(today.minusDays(60)) }
            .maxByOrNull { it.date }
}
