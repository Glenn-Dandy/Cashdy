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
    /** Rechnungen verteilter Kosten, die in diesem Zeitraum fällig sind (voller Betrag, Anteil aus dem Topf). */
    val potBills: List<Occurrence>,
    /** Ausgaben nach Kategorie, inklusive fester Kosten und Rücklagen. */
    val byCategory: List<CategorySum>,
    /** Nur im Startmonat: Kontostand plus Bargeld am Starttag. Steckt schon in [income]. */
    val startMoney: Cents? = null,
    /** Nur im Startmonat: davon schon Rücklage, die liegen bleibt. Ist von [income] schon abgezogen. */
    val startPot: Cents = 0,
)

data class YearPlan(val year: Int, val months: List<Summary>, val fixedTotal: Cents) {
    val fixedPerMonth: Cents get() = fixedTotal / 12
}

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
        val potBills = mutableListOf<Occurrence>()

        // Startmonat: alles bis einschließlich Starttag steckt schon im Kontostand.
        val startDay = s.startDay?.takeIf { s.startBalance != null && it in p }
        fun open(date: LocalDate) = startDay == null || date.isAfter(startDay)

        for (r in d.recurring) {
            if (r.income) {
                incomeItems += incomeOccurrences(r, p, s.periodMode).filter { open(it.date) }
            } else if (r.spread && r.intervalMonths > 1) {
                val share = Reserve.shareIn(d, r, p)
                if (share > 0) reserveItems += Occurrence(r, p.start, share)
                for (due in Recurrence.dueDates(r, p.start, p.end).filter(::open)) {
                    val bill = Recurrence.priceAt(r, due)
                    val saved = minOf(Reserve.savedFor(d, r, due), bill)
                    potBills += Occurrence(r, due, bill, saved)
                    // Reicht der Topf nicht (erster Durchlauf, Preis gestiegen), zahlt der Monat den Rest.
                    // Ein paar Cent Rundungsrest durch das Teilen bleiben unter dem Tisch.
                    if (bill - saved > r.intervalMonths) fixedItems += Occurrence(r, due, bill - saved, saved)
                }
            } else {
                for (due in Recurrence.dueDates(r, p.start, p.end).filter(::open)) {
                    fixedItems += Occurrence(r, due, r.overrides[p.key] ?: Recurrence.priceAt(r, due))
                }
            }
        }

        val entries = d.entries.filter { it.date in p }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt })
        // Nachgetragenes von vor dem Start ist im Kontostand schon abgezogen.
        val counted = if (startDay == null) entries else entries.filter { !it.date.isBefore(startDay) }
        val oneOffIncome = counted.filter { it.kind == EntryKind.INCOME }.sumOf { it.amount }
        val spent = counted.sumOf { spendingValue(it, s.cashMode) }
        // Bargeld zählt nur im Portemonnaie-Modus, im Einfach-Modus ist es schon ausgegeben.
        val startMoney = startDay?.let { s.startBalance!! + if (s.cashMode == CashMode.WALLET) walletAtStart(d, it) else 0 }
        // Was schon in der Rücklage liegt, steckt im Kontostand, ist aber nicht frei.
        val startPot = startDay?.let { day ->
            d.recurring.sumOf { r ->
                if (!r.spread || r.intervalMonths <= 1 || r.income) 0L
                else Recurrence.nextDue(r, day.plusDays(1))?.let { Reserve.savedFor(d, r, it, p.start.minusDays(1)) } ?: 0L
            }
        }

        val income = (startMoney ?: 0) - (startPot ?: 0) + incomeItems.sumOf { it.amount } + oneOffIncome
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
        counted.forEach { e ->
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
            incomeItems, fixedItems, reserveItems, entries, potBills, byCategory, startMoney, startPot ?: 0)
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

    /** Die zwölf Zeiträume, deren Mitte im Jahr [year] liegt, und was dort fest abgeht. */
    fun year(d: AppData, year: Int, today: LocalDate = LocalDate.now()): YearPlan {
        val periods = mutableListOf<Period>()
        var p = Periods.periodFor(LocalDate.of(year, 1, 15), d.settings)
        repeat(12) { periods += p; p = Periods.next(p, d.settings) }
        val sums = periods.map { summary(d, it, today) }
        // Echte Jahreskosten: jede Rechnung voll, egal ob verteilt oder nicht.
        val from = periods.first().start
        val to = periods.last().end
        val bills = d.recurring.filter { !it.income }.sumOf { r -> Recurrence.dueDates(r, from, to).sumOf { Recurrence.priceAt(r, it) } }
        return YearPlan(year, sums, bills)
    }

    /** Bargeld am Morgen des Starttags: alles davor plus der Startbestand von dem Tag. */
    private fun walletAtStart(d: AppData, day: LocalDate): Cents =
        wallet(d.copy(entries = d.entries.filter { it.date.isBefore(day) || (it.date == day && it.kind == EntryKind.CASH_START) }))

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
