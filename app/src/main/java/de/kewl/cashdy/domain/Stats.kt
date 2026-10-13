package de.kewl.cashdy.domain

import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.data.DefaultCategories
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.category
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ein Monat in der Statistik. [done]: schon vorbei. [used]: Cashdy lief da schon. */
data class MonthStat(val period: Period, val sum: Summary, val used: Boolean, val done: Boolean) {
    val current: Boolean get() = used && !done
}

/** Eigene Ausgaben einer Kategorie: [values] je Monat (wie die Monatsliste), [avg] über die fertigen Monate. */
data class CategoryStat(val categoryId: String, val avg: Cents, val values: List<Cents>)

/** Fixkosten einer Kategorie, umgerechnet auf einen Monat und ein Jahr. */
data class FixedStat(val categoryId: String?, val perMonth: Cents, val perYear: Cents)

enum class Trend { LESS, MORE, SAME }

/**
 * Zahlen für die Statistik-Seite. Alles hier rechnet nur mit [Budget.summary], damit
 * Statistik und Monatsseite nie verschiedene Beträge zeigen.
 */
object Stats {

    private val monthFmt = DateTimeFormatter.ofPattern("LLLL", Locale.GERMAN)

    fun months(d: AppData, periods: List<Period>, today: LocalDate): List<MonthStat> =
        of(d, periods.map { Budget.summary(d, it, today) }, today)

    fun of(d: AppData, sums: List<Summary>, today: LocalDate): List<MonthStat> = sums.map { sum ->
        val p = sum.period
        val done = p.end.isBefore(today)
        val started = d.settings.startDay?.let { !p.end.isBefore(it) } ?: true
        val used = today in p || (!p.start.isAfter(today) && sum.entries.isNotEmpty() && started)
        MonthStat(p, sum, used, done)
    }

    /** Die letzten [n] Monate bis heute, vorne ohne Monate, in denen Cashdy noch nicht lief. */
    fun lastMonths(d: AppData, today: LocalDate, n: Int = 6): List<MonthStat> =
        months(d, Periods.lastN(today, d.settings, n), today).dropWhile { !it.used }

    /** Eigene Ausgaben je Kategorie in einem Monat, ohne Fixkosten und Rücklage. */
    fun spendingByCategory(d: AppData, sum: Summary): Map<String, Cents> {
        val out = HashMap<String, Cents>()
        for (e in sum.entries) {
            if (d.settings.startDay?.let { e.date.isBefore(it) && it in sum.period } == true) continue
            val v = Budget.spendingValue(e, d.settings.cashMode)
            if (v == 0L) continue
            val k = when (e.kind) {
                EntryKind.CASH_ADJUST, EntryKind.CASH_WITHDRAWAL -> DefaultCategories.CASH_UNKNOWN
                else -> e.categoryId?.takeIf { d.category(it) != null } ?: DefaultCategories.OTHER
            }
            out[k] = (out[k] ?: 0) + v
        }
        return out
    }

    /** Größte Kategorien im Schnitt der fertigen Monate (oder des laufenden, wenn es noch keinen gibt). */
    fun categories(d: AppData, months: List<MonthStat>): List<CategoryStat> {
        val used = months.filter { it.used }
        val base = used.filter { it.done }.ifEmpty { used }
        if (base.isEmpty()) return emptyList()
        val per = used.associateWith { spendingByCategory(d, it.sum) }
        val ids = per.values.flatMap { it.keys }.toSet()
        return ids.map { id ->
            val avg = base.sumOf { per[it]?.get(id) ?: 0L } / base.size
            CategoryStat(id, avg, used.map { per[it]?.get(id) ?: 0L })
        }.filter { it.avg > 0 }.sortedByDescending { it.avg }
    }

    /** Vergleicht die ersten und die letzten fertigen Monate. */
    fun trend(values: List<Cents>): Trend {
        if (values.size < 3) return Trend.SAME
        val k = values.size / 2
        val a = values.take(k).average()
        val b = values.takeLast(k).average()
        return when {
            b < a * 0.85 -> Trend.LESS
            b > a * 1.15 -> Trend.MORE
            else -> Trend.SAME
        }
    }

    /** Laufende Fixkosten nach Kategorie, jede Rechnung auf Monat und Jahr umgerechnet. */
    fun fixed(d: AppData, today: LocalDate): List<FixedStat> =
        d.recurring.filter { !it.income && (it.endDate == null || !it.endDate.isBefore(today)) && !it.firstDue.isAfter(today.plusMonths(1)) }
            .groupBy { it.categoryId?.takeIf { id -> d.category(id) != null } }
            .map { (cat, rs) ->
                val year = rs.sumOf { Recurrence.currentPrice(it, today) * 12 / it.intervalMonths }
                FixedStat(cat, year / 12, year)
            }
            .sortedByDescending { it.perMonth }

    /**
     * Höchstens zwei freundliche Sätze: was gut lief, was teurer wurde, der beste Monat.
     * Nie ein Vorwurf.
     */
    fun insights(d: AppData, months: List<MonthStat>, cats: List<CategoryStat>, today: LocalDate): List<String> {
        val out = mutableListOf<String>()
        val used = months.filter { it.used }
        val done = used.filter { it.done }

        // Eine Kategorie war im letzten fertigen Monat deutlich weniger als sonst.
        val last = done.lastOrNull()
        if (last != null && done.size >= 3) {
            val li = used.indexOf(last)
            cats.mapNotNull { c ->
                val others = done.filter { it != last }.map { c.values[used.indexOf(it)] }
                val avg = others.average()
                val v = c.values[li]
                (c to (avg - v).toLong()).takeIf { avg >= 2000 && v < avg * 0.8 && avg - v >= 1000 }
            }.maxByOrNull { it.second }?.let { (c, diff) ->
                val name = d.category(c.categoryId)?.name ?: "Sonstiges"
                out += "Für $name hast du im ${last.period.shortLabel} ${Money.format(diff)} weniger ausgegeben als sonst. Stark!"
            }
        }

        // Fixkosten, die im letzten Jahr teurer geworden sind.
        d.recurring.filter { !it.income && (it.endDate == null || !it.endDate.isBefore(today)) && it.prices.size > 1 }
            .mapNotNull { r ->
                val steps = r.prices.sortedBy { it.from }.filter { !it.from.isAfter(today) }
                if (steps.size < 2) return@mapNotNull null
                val now = steps.last()
                val before = steps[steps.size - 2]
                (Triple(r, now, now.amount - before.amount)).takeIf { it.third > 0 && now.from.isAfter(today.minusYears(1)) }
            }
            .maxByOrNull { it.third }
            ?.let { (r, step, diff) ->
                out += "${r.name} kostet seit ${step.from.format(monthFmt)} ${Money.format(diff)} mehr. Vielleicht lohnt sich ein Vergleich."
            }

        // Der beste Monat.
        if (out.size < 2 && done.size >= 2) {
            done.maxByOrNull { it.sum.free }?.takeIf { it.sum.free > 0 }?.let {
                out += "Im ${it.period.shortLabel} ist dir am meisten übrig geblieben: ${Money.format(it.sum.free)}."
            }
        }
        return out.take(2)
    }

    /** Wo am meisten: Händler einer Kategorie mit Summe und Anzahl, größte zuerst. */
    fun topMerchants(d: AppData, categoryId: String, months: List<MonthStat>, n: Int = 3): List<Triple<String, Cents, Int>> =
        months.filter { it.used }.flatMap { it.sum.entries }
            .filter { it.kind == EntryKind.EXPENSE && it.categoryId == categoryId && it.merchant.isNotBlank() }
            .groupBy { Merchants.normalize(it.merchant) }
            .map { (_, es) -> Triple(es.first().merchant, es.sumOf { it.amount }, es.size) }
            .sortedByDescending { it.second }
            .take(n)
}
