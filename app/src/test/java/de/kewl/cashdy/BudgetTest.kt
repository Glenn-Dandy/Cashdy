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
import de.kewl.cashdy.domain.Reserve
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

    @Test fun potPaysBillWhenFullySaved() {
        // Topf läuft seit einem Jahr: Kfz im November kommt komplett aus der Rücklage.
        val data = AppData(recurring = listOf(car), settings = Settings(potStart = d(2025, 11, 1)))
        val nov = Budget.summary(data, Periods.calendar(d(2026, 11, 1)), d(2026, 11, 1))
        assertEquals(0L, nov.fixed)
        assertEquals(5_000L, nov.reserve)
        assertEquals(60_000L, nov.potBills.single().amount)
        assertEquals(60_000L, nov.potBills.single().fromPot)
    }

    @Test fun potShortInFirstRound() {
        // Erst im August angefangen: August bis November = 4 x 50 € angespart, 400 € fehlen.
        val data = AppData(recurring = listOf(car), settings = Settings(potStart = d(2026, 8, 10)))
        val nov = Budget.summary(data, Periods.calendar(d(2026, 11, 1)), d(2026, 11, 1))
        assertEquals(40_000L, nov.fixed)
        assertEquals(20_000L, nov.fixedItems.single().fromPot)
        val jul = Budget.summary(data, Periods.calendar(d(2026, 7, 1)), d(2026, 7, 1))
        assertEquals(0L, jul.reserve)

        val st = Reserve.status(data, car, d(2026, 10, 11))!!
        assertEquals(d(2026, 11, 15), st.nextDue)
        assertEquals(15_000L, st.savedNow)
        assertEquals(20_000L, st.savedAtDue)
        assertEquals(40_000L, st.missing)
        // Ab dem zweiten Durchlauf reicht der Topf.
        val next = Reserve.status(data, car, d(2026, 12, 1))!!
        assertEquals(0L, next.missing)
    }

    @Test fun startMonthUsesBalance() {
        // Start am 12.10. mit 1.240 € auf dem Konto und 80 € bar. Miete (1.) ist schon abgebucht,
        // Netflix (15.) und Gehalt (28.) kommen noch. Die Kfz-Rücklage fängt erst im November an.
        val strom = Recurring("st", "Strom", firstDue = d(2026, 1, 12), prices = listOf(PriceStep(d(2026, 1, 12), 9_500)))
        val data = AppData(
            recurring = listOf(salary, rent, netflix, car, strom),
            entries = listOf(
                Entry("c", 8_000, d(2026, 10, 12), EntryKind.CASH_START, method = PayMethod.CASH),
                Entry("old", 5_000, d(2026, 10, 5), EntryKind.EXPENSE, "food"), // nachgetragen, steckt im Kontostand
                Entry("e", 2_000, d(2026, 10, 13), EntryKind.EXPENSE, "food"),
            ),
            settings = Settings(startDay = d(2026, 10, 12), startBalance = 124_000, potStart = d(2026, 11, 1)),
        )
        val oct = Budget.summary(data, Periods.calendar(d(2026, 10, 1)), d(2026, 10, 13))
        assertEquals(132_000L, oct.startMoney)
        assertEquals(132_000L + 300_000, oct.income)
        assertEquals(1_399L, oct.fixed) // Strom am Starttag gilt als abgebucht
        assertEquals(0L, oct.reserve)
        assertEquals(2_000L, oct.spent)
        assertEquals(432_000L - 1_399 - 2_000, oct.free)

        // Ab November ganz normal.
        val nov = Budget.summary(data, Periods.calendar(d(2026, 11, 1)), d(2026, 11, 1))
        assertEquals(null, nov.startMoney)
        assertEquals(300_000L, nov.income)
        // Kfz am 15.11.: im Topf ist erst der November-Anteil, der Rest zählt im November.
        assertEquals(85_000L + 1_399 + 9_500 + 55_000, nov.fixed)
        assertEquals(5_000L, nov.reserve)

        // Im Einfach-Modus zählt das Bargeld nicht dazu, im Minus geht es auch.
        val simple = data.copy(settings = data.settings.copy(cashMode = CashMode.SIMPLE, startBalance = -20_000))
        assertEquals(-20_000L, Budget.summary(simple, Periods.calendar(d(2026, 10, 1)), d(2026, 10, 13)).startMoney)
    }

    @Test fun restartKeepsPotMoney() {
        // Rücklage läuft seit Januar, Neustart am 12.10. mit 2.000 € auf dem Konto. Darin stecken
        // Januar bis September Kfz-Rücklage (9 x 50 €), die bleiben liegen. Der Oktober legt wie
        // gewohnt seinen Anteil zurück.
        val data = AppData(
            recurring = listOf(salary, car),
            entries = listOf(
                Entry("w", 10_000, d(2026, 9, 1), EntryKind.CASH_WITHDRAWAL),
                Entry("b", 1_500, d(2026, 9, 2), EntryKind.EXPENSE, "food", method = PayMethod.CASH),
            ),
            settings = Settings(potStart = d(2026, 1, 1), startDay = d(2026, 10, 12), startBalance = 200_000),
        )
        val oct = Budget.summary(data, Periods.calendar(d(2026, 10, 1)), d(2026, 10, 12))
        assertEquals(200_000L + 8_500, oct.startMoney) // Portemonnaie: 100 € abgehoben, 15 € bar bezahlt
        assertEquals(45_000L, oct.startPot)
        assertEquals(208_500L - 45_000 + 300_000, oct.income)
        assertEquals(5_000L, oct.reserve)
    }

    @Test fun startMonthUntilNextSalary() {
        // Monat ab Gehaltseingang: Start am 12.10., letztes Gehalt kam am 28.9. Der Startmonat
        // läuft bis zum nächsten Gehalt, das ist dann schon der nächste Zeitraum.
        val settings = Settings(periodMode = PeriodMode.INCOME, periodDay = 28, periodStarts = listOf(d(2026, 9, 28)),
            startDay = d(2026, 10, 12), startBalance = 50_000)
        val data = AppData(recurring = listOf(salary, rent), settings = settings)
        val p = Periods.periodFor(d(2026, 10, 12), settings)
        val s = Budget.summary(data, p, d(2026, 10, 12))
        assertEquals(50_000L, s.income)
        assertEquals(0L, s.fixed)
        assertEquals(50_000L, s.free)
    }

    @Test fun roundingRestStaysInPot() {
        val gez = Recurring("g", "Rundfunk", intervalMonths = 3, firstDue = d(2026, 1, 15), spread = true, prices = listOf(PriceStep(d(2026, 1, 15), 5509)))
        val data = AppData(recurring = listOf(gez), settings = Settings(potStart = d(2025, 1, 1)))
        val apr = Budget.summary(data, Periods.calendar(d(2026, 4, 1)), d(2026, 4, 1))
        assertEquals(0L, apr.fixed) // 3 x 18,36 = 55,08, der eine Cent fällt nicht ins Gewicht
    }

    @Test fun yearPlan() {
        val data = AppData(recurring = listOf(salary, rent, car), settings = Settings(potStart = d(2025, 1, 1)))
        val y = Budget.year(data, 2026, d(2026, 10, 11))
        assertEquals(12, y.months.size)
        assertEquals("Januar", y.months.first().period.shortLabel)
        assertEquals(12 * 85_000L + 60_000L, y.fixedTotal)
        assertEquals(300_000L - 85_000L - 5_000L, y.months[11].free)
    }

    @Test fun incomeModeFuturePeriodsAndPot() {
        val s = Settings(periodMode = PeriodMode.INCOME, periodDay = 27, periodStarts = listOf(d(2026, 9, 27)), potStart = d(2026, 10, 11))
        val p = Periods.periodFor(d(2027, 3, 5), s)
        assertEquals(d(2027, 2, 27), p.start)
        assertEquals(d(2027, 3, 26), p.end)
        assertEquals(d(2026, 10, 27), Periods.next(Periods.periodFor(d(2026, 10, 11), s), s).start)

        // Kfz am 1.10.: diesmal fehlen 550 €, beim nächsten Mal ist der Topf voll.
        val kfz = car.copy(firstDue = d(2026, 10, 1), prices = listOf(PriceStep(d(2026, 10, 1), 60_000)))
        val data = AppData(recurring = listOf(kfz), settings = s)
        assertEquals(55_000L, Budget.summary(data, Periods.periodFor(d(2026, 10, 11), s), d(2026, 10, 11)).fixed)
        val st = Reserve.status(data, kfz, d(2026, 10, 11))!!
        assertEquals(d(2027, 10, 1), st.nextDue)
        assertEquals(0L, st.savedNow)
        assertEquals(0L, st.missing)
        assertEquals(12, Budget.year(data, 2027, d(2026, 10, 11)).months.map { it.period.start }.distinct().size)
    }
}
