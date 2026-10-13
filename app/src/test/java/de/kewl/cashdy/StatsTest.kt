package de.kewl.cashdy

import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.Entry
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.PriceStep
import de.kewl.cashdy.data.Recurring
import de.kewl.cashdy.domain.Stats
import de.kewl.cashdy.domain.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private val today = d(2026, 10, 13)
    private val salary = Recurring("sal", "Gehalt", income = true, firstDue = d(2026, 1, 28), prices = listOf(PriceStep(d(2026, 1, 28), 250_000)))

    /** Lebensmittel jeden Monat 300 €, im September nur 200 €. Essen gehen 60 €. */
    private fun data(): AppData {
        val entries = (5..10).flatMap { m ->
            listOf(
                Entry("f$m", if (m == 9) 20_000 else 30_000, d(2026, m, 5), EntryKind.EXPENSE, "food", merchant = if (m % 2 == 0) "REWE" else "Lidl"),
                Entry("e$m", 6_000, d(2026, m, 6), EntryKind.EXPENSE, "eating", merchant = "Pizzeria"),
            )
        }
        return AppData(recurring = listOf(salary), entries = entries)
    }

    @Test fun lastSixMonthsAndAverages() {
        val months = Stats.lastMonths(data(), today)
        assertEquals(6, months.size)
        assertEquals(5, months.count { it.done })
        val cats = Stats.categories(data(), months)
        assertEquals("food", cats.first().categoryId)
        // Mai bis September: 4 x 300 + 200 = 1.400 / 5
        assertEquals(28_000L, cats.first().avg)
        assertEquals(6, cats.first().values.size)
    }

    @Test fun monthsBeforeFirstUseAreLeftOut() {
        val only = data().copy(entries = data().entries.filter { it.date.monthValue >= 8 })
        assertEquals(3, Stats.lastMonths(only, today).size)
    }

    @Test fun trendAndInsights() {
        assertEquals(Trend.LESS, Stats.trend(listOf(30_000, 30_000, 20_000, 18_000)))
        assertEquals(Trend.SAME, Stats.trend(listOf(30_000, 29_000, 31_000, 30_000)))
        val months = Stats.lastMonths(data(), today)
        val tips = Stats.insights(data(), months, Stats.categories(data(), months), today)
        assertTrue(tips.toString(), tips.first().text.contains("Lebensmittel") && tips.first().text.contains("100,00 €"))
    }

    @Test fun fixedPerMonthAndYear() {
        val car = Recurring("car", "Kfz", categoryId = "insurance", intervalMonths = 12, firstDue = d(2026, 3, 1), prices = listOf(PriceStep(d(2026, 3, 1), 60_000)))
        val nf = Recurring("nf", "Netflix", categoryId = "subs", firstDue = d(2026, 1, 15), prices = listOf(PriceStep(d(2026, 1, 15), 1_399)))
        val f = Stats.fixed(AppData(recurring = listOf(salary, car, nf)), today)
        assertEquals(listOf("insurance", "subs"), f.map { it.categoryId })
        assertEquals(5_000L, f[0].perMonth)
        assertEquals(16_788L, f[1].perYear)
    }

    @Test fun topMerchants() {
        val months = Stats.lastMonths(data(), today)
        val top = Stats.topMerchants(data(), "food", months)
        assertEquals("REWE", top.first().first)
        assertEquals(3, top.first().third)
    }

    @Test fun takeawayHintLooksSkeptical() {
        val fast = (1..5).map { Entry("t$it", 1_290, d(2026, 9, it * 3), EntryKind.EXPENSE, "takeaway", merchant = "McDonald's") }
        val data = data().copy(entries = data().entries + fast)
        val months = Stats.lastMonths(data, today)
        val tip = Stats.insights(data, months, Stats.categories(data, months), today).first { it.tone == de.kewl.cashdy.domain.Insight.Tone.DOUBT }
        assertTrue(tip.text, tip.text.contains("5 Mal") && tip.text.contains("64,50 €") && tip.text.contains("774 €"))
    }

    @Test fun fastFoodChainsLandInTakeaway() {
        val m = de.kewl.cashdy.domain.Merchants
        listOf("McDonald's", "BURGER KING 1234", "Subway Hbf", "Lieferando.de", "Uber Eats", "Backwerk", "Kamps Bäckerei", "Döner Express").forEach {
            assertEquals(it, "takeaway", m.guess(it))
        }
        assertEquals("eating", m.guess("Pizzeria Roma"))
        assertEquals("food", m.guess("Bäckerei Schmidt"))
        assertEquals("fuel", m.guess("Uber BV"))
    }
}
