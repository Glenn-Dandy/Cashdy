package de.kewl.cashdy.importer

import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.data.DefaultCategories
import de.kewl.cashdy.data.PriceStep
import de.kewl.cashdy.data.Recurring
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.domain.Merchants
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class RecurringSuggestion(
    val recurring: Recurring,
    /** Die Umsätze, aus denen der Vorschlag entstand (werden beim Import nicht doppelt angelegt). */
    val rows: List<CsvRow>,
)

/**
 * Findet regelmäßige Zahlungen in einem Kontoauszug: gleicher Empfänger, ähnlicher Betrag,
 * gleichmäßiger Abstand. Aus Betragsänderungen entsteht gleich die Preis-Historie.
 */
object RecurringDetector {

    private val intervals = listOf(1 to 25..35, 2 to 55..66, 3 to 85..97, 6 to 170..195, 12 to 350..380)

    fun detect(rows: List<CsvRow>, existing: List<Recurring> = emptyList()): List<RecurringSuggestion> {
        val groups = rows.filter { it.counterparty.isNotBlank() && it.method != de.kewl.cashdy.data.PayMethod.CASH }
            .groupBy { Merchants.normalize(it.counterparty) to (it.amount < 0) }
        val out = mutableListOf<RecurringSuggestion>()
        for ((key, list) in groups) {
            if (key.first.isBlank() || list.size < 2) continue
            val sorted = list.sortedBy { it.date }
            val gaps = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date).toInt() }
            if (gaps.any { it < 20 }) continue // mehrfach im Monat = Einkäufe, nicht regelmäßig
            val median = gaps.sorted()[gaps.size / 2]
            val months = intervals.firstOrNull { median in it.second }?.first ?: continue
            if (gaps.any { g -> intervals.first { it.first == months }.second.let { g !in (it.first - 5)..(it.last + 5) } }) continue
            // Monatliche Zahlungen erst ab drei Buchungen glauben, sonst ist es Zufall.
            if (months == 1 && sorted.size < 3) continue
            val amounts = sorted.map { abs(it.amount) }
            val med = amounts.sorted()[amounts.size / 2]
            if (amounts.any { abs(it - med) * 100 > med * 35 }) continue

            val income = key.second.not()
            val name = Merchants.pretty(sorted.last().counterparty)
            if (existing.any { it.income == income && Merchants.similar(it.name, name) }) continue

            val steps = mutableListOf<PriceStep>()
            for (r in sorted) {
                val a = abs(r.amount)
                if (steps.isEmpty() || steps.last().amount != a) steps += PriceStep(r.date, a)
            }
            val text = sorted.last().counterparty + " " + sorted.last().purpose
            val category = Merchants.guess(text) ?: if (income) DefaultCategories.INCOME_OTHER else DefaultCategories.OTHER
            val isSalary = income && (category == DefaultCategories.SALARY || med >= 100_000 && months == 1)
            out += RecurringSuggestion(
                Recurring(
                    id = Store.newId(),
                    name = name,
                    categoryId = if (isSalary) DefaultCategories.SALARY else category,
                    income = income,
                    intervalMonths = months,
                    firstDue = sorted.first().date,
                    spread = !income && months > 1,
                    prices = compactSteps(steps),
                    salary = isSalary,
                ),
                sorted,
            )
        }
        return out.sortedWith(compareBy<RecurringSuggestion> { it.recurring.income.not() }.thenByDescending { it.recurring.prices.last().amount })
    }

    /** Kleine Schwankungen (Strom-Abschlag ±2 Cent) nicht als Preisänderung zählen. */
    private fun compactSteps(steps: List<PriceStep>): List<PriceStep> {
        val out = mutableListOf<PriceStep>()
        for (s in steps) {
            val last = out.lastOrNull()
            if (last == null || abs(last.amount - s.amount) > maxOf(5L, last.amount / 100)) out += s
        }
        return out
    }

    fun belongsTo(row: CsvRow, r: Recurring, priceAt: (Recurring, java.time.LocalDate) -> Cents): Boolean {
        if ((row.amount > 0) != r.income) return false
        if (!Merchants.similar(row.counterparty, r.name)) return false
        val expected = priceAt(r, row.date)
        return expected > 0 && abs(abs(row.amount) - expected) * 100 <= expected * 35
    }
}
