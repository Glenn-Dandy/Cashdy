package de.kewl.cashdy

import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.CashMode
import de.kewl.cashdy.data.Entry
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.data.PeriodMode
import de.kewl.cashdy.data.PriceStep
import de.kewl.cashdy.data.Recurring
import de.kewl.cashdy.data.Settings
import de.kewl.cashdy.domain.Budget
import de.kewl.cashdy.domain.Periods
import de.kewl.cashdy.domain.Recurrence
import de.kewl.cashdy.domain.Salary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BudgetTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private val salary = Recurring("sal", "Gehalt", income = true, firstDue = d(2026, 1, 28), prices = listOf(PriceStep(d(2026, 1, 28), 300_000)), salary = true)
    private val rent = Recurring("rent", "Miete", firstDue = d(2026, 1, 1), prices = listOf(PriceStep(d(2026, 1, 1), 85_000)))
    private val netflix = Recurring(
        "nf", "Netflix", firstDue = d(2025, 1, 15),
        prices = listOf(PriceStep(d(2025, 1, 15), 1299), PriceStep(d(2026, 3, 1), 1399)),
    )
    private val car = Recurring("car", "Kfz", intervalMonths = 12, firstDue = d(2026, 11, 15), spread = true, prices = listOf(PriceStep(d(2026, 11, 15), 60_000)))

    @Test fun priceHistory() {
        assertEquals(1299L, Recurrence.priceAt(netflix, d(2026, 2, 15)))
        assertEquals(1399L, Recurrence.priceAt(netflix, d(2026, 3, 15)))
        assertEquals("seit 2025 um 7,6 % teurer", Recurrence.changeHint(netflix, d(2026, 4, 1)))
    }

    @Test fun dueDatesKeepThe31st() {
        val r = Recurring("x", "x", firstDue = d(2026, 1, 31), prices = listOf(PriceStep(d(2026, 1, 31), 100)))
        val dates = Recurrence.dueDates(r, d(2026, 1, 1), d(2026, 5, 31))
        assertEquals(listOf(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31), d(2026, 4, 30), d(2026, 5, 31)), dates)
    }

    @Test fun quarterlyDueDates() {
        val r = Recurring("q", "GEZ", intervalMonths = 3, firstDue = d(2026, 1, 15), prices = listOf(PriceStep(d(2026, 1, 15), 5508)))
        assertEquals(listOf(d(2026, 4, 15), d(2026, 7, 15), d(2026, 10, 15)), Recurrence.dueDates(r, d(2026, 2, 1), d(2026, 12, 31)))
    }

    @Test fun summaryWithSpreadAndCash() {
        val data = AppData(
            recurring = listOf(salary, rent, netflix, car),
            entries = listOf(
                Entry("e1", 4_250, d(2026, 10, 3), EntryKind.EXPENSE, "food", "REWE"),
                Entry("e2", 20_000, d(2026, 10, 4), EntryKind.CASH_WITHDRAWAL, method = PayMethod.CARD),
                Entry("e3", 450, d(2026, 10, 5), EntryKind.EXPENSE, "food", "Bäcker", method = PayMethod.CASH),
                Entry("e4", 1_000, d(2026, 10, 6), EntryKind.REFUND),
                Entry("e5", 2_000, d(2026, 10, 7), EntryKind.CASH_ADJUST, method = PayMethod.CASH),
            ),
        )
        val p = Periods.periodFor(d(2026, 10, 11), data.settings)
        val s = Budget.summary(data, p, d(2026, 10, 11))
        assertEquals(300_000L, s.income)
        assertEquals(85_000L + 1399L, s.fixed)
        assertEquals(5_000L, s.reserve) // 600 € / 12
        // Abhebung zählt nicht, Barzahlung und Abgleich schon, Erstattung mindert
        assertEquals(4_250L + 450 - 1_000 + 2_000, s.spent)
        assertEquals(300_000L - 86_399 - 5_000 - 5_700, s.free)
        // Portemonnaie: 200 - 4,50 - 20 = 175,50
        assertEquals(17_550L, Budget.wallet(data))

        val simple = data.copy(settings = Settings(cashMode = CashMode.SIMPLE))
        val ss = Budget.summary(simple, p, d(2026, 10, 11))
        // Einfach-Modus: Abhebung zählt, Barzahlung und Abgleich nicht
        assertEquals(4_250L + 20_000 - 1_000, ss.spent)
    }

    @Test fun spreadNotDueAgainInDueMonth() {
        val data = AppData(recurring = listOf(car))
        val nov = Budget.summary(data, Periods.calendar(d(2026, 11, 1)), d(2026, 11, 1))
        assertEquals(0L, nov.fixed)
        assertEquals(5_000L, nov.reserve)
    }

    @Test fun salaryCountsOncePerIncomePeriodEvenWhenLate() {
        val s = Settings(periodMode = PeriodMode.INCOME, periodDay = 28, periodStarts = listOf(d(2026, 8, 28), d(2026, 9, 30)))
        val data = AppData(recurring = listOf(salary), settings = s)
        val aug = Budget.summary(data, Periods.periodFor(d(2026, 9, 10), s), d(2026, 9, 10))
        val sep = Budget.summary(data, Periods.periodFor(d(2026, 10, 10), s), d(2026, 10, 10))
        assertEquals(300_000L, aug.income)
        assertEquals(300_000L, sep.income)
    }

    @Test fun salaryConfirmationStoresDifferentAmount() {
        val s = Settings(periodMode = PeriodMode.INCOME, periodStarts = listOf(d(2026, 9, 28)))
        val data = AppData(recurring = listOf(salary), settings = s)
        val next = Salary.startNewPeriod(data, d(2026, 10, 28), 315_000)
        assertEquals(listOf(d(2026, 9, 28), d(2026, 10, 28)), next.settings.periodStarts)
        val p = Periods.periodFor(d(2026, 11, 2), next.settings)
        assertEquals(315_000L, Budget.summary(next, p, d(2026, 11, 2)).income)
        // Doppelt bestätigt (zwei Tage später) ändert nichts
        assertEquals(next, Salary.startNewPeriod(next, d(2026, 10, 30), null))
    }
}
