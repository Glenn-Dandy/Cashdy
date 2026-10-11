package de.kewl.cashdy.domain

import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.CashMode
import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.data.DefaultCategories
import de.kewl.cashdy.data.Entry
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.data.PeriodMode
import de.kewl.cashdy.data.Recurring
import java.time.LocalDate

data class CategorySum(val categoryId: String, val amount: Cents)

data class Summary(
    val period: Period,
    val income: Cents,
    val fixed: Cents,
    val reserve: Cents,
    val spent: Cents,
    val free: Cents,
    val perDay: Cents,
    val daysLeft: Int,
    val incomeItems: List<Occurrence>,
    val fixedItems: List<Occurrence>,
    val reserveItems: List<Occurrence>,
    val entries: List<Entry>,
    /** Ausgaben nach Kategorie, inklusive fester Kosten und Rücklagen. */
    val byCategory: List<CategorySum>,
)

object Budget {

    /** Wie die Monatsbilanz einen Eintrag zählt: positiv = Ausgabe, negativ = mindert Ausgaben. */
    fun spendingValue(e: Entry, cashMode: CashMode): Cents = when (e.kind) {
        EntryKind.EXPENSE -> e.amount
        EntryKind.REFUND -> -e.amount
        EntryKind.CASH_ADJUST -> if (cashMode == CashMode.WALLET) e.amount else 0
        EntryKind.CASH_WITHDRAWAL -> if (cashMode == CashMode.SIMPLE) e.amount else 0
        // Im Einfach-Modus ist Bargeld mit dem Abheben schon "ausgegeben".
        EntryKind.INCOME, EntryKind.CASH_START -> 0
    }.let { v ->
        if (cashMode == CashMode.SIMPLE && e.kind == EntryKind.EXPENSE && e.method == PayMethod.CASH) 0 else v
    }

    fun summary(d: AppData, p: Period, today: LocalDate = LocalDate.now()): Summary {
        val s = d.settings
        val incomeItems = mutableListOf<Occurrence>()
        val fixedItems = mutableListOf<Occurrence>()
        val reserveItems = mutableListOf<Occurrence>()

        for (r in d.recurring) {
            if (r.income) {
                incomeItems += incomeOccurrences(r, p, s.periodMode)
            } else if (r.spread && r.intervalMonths > 1) {
                if (Recurrence.spreadActive(r, p)) {
                    val share = r.overrides[p.key] ?: Recurrence.monthlyShare(r, p.start)
                    reserveItems += Occurrence(r, p.start, share)
                }
            } else {
                for (due in Recurrence.dueDates(r, p.start, p.end)) {
                    fixedItems += Occurrence(r, due, r.overrides[p.key] ?: Recurrence.priceAt(r, due))
                }
            }
        }

        val entries = d.entries.filter { it.date in p }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
        val oneOffIncome = entries.filter { it.kind == EntryKind.INCOME }.sumOf { it.amount }
        val spent = entries.sumOf { spendingValue(it, s.cashMode) }

        val income = incomeItems.sumOf { it.amount } + oneOffIncome
        val fixed = fixedItems.sumOf { it.amount }
        val reserve = reserveItems.sumOf { it.amount }
        val free = income - fixed - reserve - spent
        val daysLeft = p.daysLeft(today)
        val perDay = if (daysLeft > 0 && free > 0) free / daysLeft else 0

        val cat = linkedMapOf<String, Long>()
        fun add(id: String?, v: Cents) {
            val k = id ?: DefaultCategories.OTHER
            cat[k] = (cat[k] ?: 0) + v
        }
        entries.forEach { e ->
            val v = spendingValue(e, s.cashMode)
            if (v != 0L) add(
                when (e.kind) {
                    EntryKind.CASH_ADJUST, EntryKind.CASH_WITHDRAWAL -> DefaultCategories.CASH_UNKNOWN
                    else -> e.categoryId
                }, v
            )
        }
        (fixedItems + reserveItems).forEach { add(it.recurring.categoryId, it.amount) }
        val byCategory = cat.filter { it.value > 0 }.map { CategorySum(it.key, it.value) }.sortedByDescending { it.amount }

        return Summary(p, income, fixed, reserve, spent, free, perDay, daysLeft,
            incomeItems, fixedItems, reserveItems, entries, byCategory)
    }

    /**
     * Gehalt im Modus "ab Geldeingang" zählt genau einmal je Zeitraum, egal ob es zwei Tage
     * früher oder später kam als geplant. Sonst hätte ein verspätetes Gehalt den alten Monat
     * doppelt und den neuen leer gelassen.
     */
    private fun incomeOccurrences(r: Recurring, p: Period, mode: PeriodMode): List<Occurrence> {
        if (r.salary && mode == PeriodMode.INCOME && r.intervalMonths == 1) {
            if (p.end.isBefore(r.firstDue.minusDays(10))) return emptyList()
            if (r.endDate != null && p.start.isAfter(r.endDate)) return emptyList()
            return listOf(Occurrence(r, p.start, r.overrides[p.key] ?: Recurrence.priceAt(r, p.start)))
        }
        return Recurrence.dueDates(r, p.start, p.end).map { Occurrence(r, it, r.overrides[p.key] ?: Recurrence.priceAt(r, it)) }
    }

    /** Bargeld laut App. */
    fun wallet(d: AppData): Cents = d.entries.sumOf { e ->
        when (e.kind) {
            EntryKind.CASH_START, EntryKind.CASH_WITHDRAWAL -> e.amount
            EntryKind.CASH_ADJUST -> -e.amount
            EntryKind.EXPENSE -> if (e.method == PayMethod.CASH) -e.amount else 0
            EntryKind.INCOME, EntryKind.REFUND -> if (e.method == PayMethod.CASH) e.amount else 0
        }
    }

    /** Tage am Stück (bis heute oder gestern) mit mindestens einem Eintrag. */
    fun streak(d: AppData, today: LocalDate = LocalDate.now()): Int {
        val days = d.entries.map { java.time.Instant.ofEpochMilli(it.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.toSet()
        var day = if (today in days) today else today.minusDays(1)
        var n = 0
        while (day in days) { n++; day = day.minusDays(1) }
        return n
    }

    /** Ausgaben (ohne Fixkosten) bis zum gleichen Tag im Zeitraum, für den Vergleich mit dem Vormonat. */
    fun spentUntilDay(d: AppData, p: Period, dayIndex: Long): Cents {
        val until = p.start.plusDays(dayIndex)
        return d.entries.filter { it.date in p && !it.date.isAfter(until) }.sumOf { spendingValue(it, d.settings.cashMode) }
    }
}
