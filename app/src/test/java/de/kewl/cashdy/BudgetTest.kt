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

    @Test fun restartCountsRareBillsInFull() {
        // Rücklage lief seit Januar, Neustart am 12.10. mit 2.000 € auf dem Konto. Die
        // Quartalsrechnung am 20.10. geht im Startmonat voll ab, ohne Rücklage. Der Topf fängt im
        // November leer an.
        val quarter = Recurring("q", "Quartal", intervalMonths = 3, firstDue = d(2026, 1, 20), spread = true, prices = listOf(PriceStep(d(2026, 1, 20), 12_000)))
        val data = AppData(
            recurring = listOf(salary, car, quarter),
            entries = listOf(
                Entry("w", 10_000, d(2026, 9, 1), EntryKind.CASH_WITHDRAWAL),
                Entry("b", 1_500, d(2026, 9, 2), EntryKind.EXPENSE, "food", method = PayMethod.CASH),
            ),
            settings = Settings(potStart = d(2026, 11, 1), startDay = d(2026, 10, 12), startBalance = 200_000),
        )
        val oct = Budget.summary(data, Periods.calendar(d(2026, 10, 1)), d(2026, 10, 12))
        assertEquals(200_000L + 8_500, oct.startMoney) // Portemonnaie: 100 € abgehoben, 15 € bar bezahlt
        assertEquals(208_500L + 300_000, oct.income)
        assertEquals(12_000L, oct.fixed)
        assertEquals(0L, oct.reserve)
        // November: Kfz am 15.11. hat erst den November-Anteil im Topf, der Rest zählt im November.
        val nov = Budget.summary(data, Periods.calendar(d(2026, 11, 1)), d(2026, 11, 1))
        assertEquals(60_000L - 5_000, nov.fixed)
        assertEquals(5_000L + 4_000, nov.reserve)
    }

    @Test fun oldRestartGetsFreshPot() {
        // Neustart mit einer älteren Version: Topf ohne Starttag. Nach dem Update fängt er im
        // November an, im Oktober zählt die Quartalsrechnung voll, ohne „Davon Rücklage“.
        val quarter = Recurring("q", "Quartal", intervalMonths = 3, firstDue = d(2026, 1, 20), spread = true, prices = listOf(PriceStep(d(2026, 1, 20), 12_597)))
        val old = AppData(recurring = listOf(quarter), settings = Settings(startDay = d(2026, 10, 13), startBalance = 155_470))
        val data = de.kewl.cashdy.data.Store.decode(de.kewl.cashdy.data.Store.encode(old))
        assertEquals(d(2026, 11, 1), data.settings.potStart)
        val oct = Budget.summary(data, Periods.calendar(d(2026, 10, 1)), d(2026, 10, 13))
        assertEquals(155_470L, oct.income)
        assertEquals(12_597L, oct.fixed)
        assertEquals(0L, oct.reserve)
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

    @Test fun loanProgress() {
        // 24 Raten von Januar 2026 bis Dezember 2027, am 12.10.2026 sind 10 bezahlt.
        val loan = Recurring("l", "Autokredit", categoryId = "loans", firstDue = d(2026, 1, 1), endDate = d(2027, 12, 1),
            prices = listOf(PriceStep(d(2026, 1, 1), 15_000)))
        val lp = Recurrence.loanProgress(loan, d(2026, 10, 12))!!
        assertEquals(10, lp.paid)
        assertEquals(24, lp.total)
        assertEquals(14, lp.left)
        assertEquals(d(2027, 12, 1), lp.last)
        // Nach der letzten Rate ist sie weg und der Monat hat mehr frei.
        val data = AppData(recurring = listOf(loan))
        assertEquals(15_000L, Budget.summary(data, Periods.calendar(d(2027, 12, 1)), d(2027, 12, 1)).fixed)
        assertEquals(0L, Budget.summary(data, Periods.calendar(d(2028, 1, 1)), d(2028, 1, 1)).fixed)
        // Ohne letzte Rate gibt es keinen Fortschritt, aber die Rate zählt weiter.
        assertEquals(null, Recurrence.loanProgress(loan.copy(endDate = null), d(2026, 10, 12)))
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

    @Test fun savingLowersFreeButIsNotSpent() {
        val data = AppData(
            recurring = listOf(salary, rent),
            entries = listOf(
                Entry("e", 2_000, d(2026, 10, 3), EntryKind.EXPENSE, "food"),
                Entry("s", 5_000, d(2026, 10, 5), EntryKind.SAVING, merchant = "Tagesgeld"),
            ),
        )
        val oct = Budget.summary(data, Periods.calendar(d(2026, 10, 1)), d(2026, 10, 10))
        assertEquals(2_000L, oct.spent)
        assertEquals(5_000L, oct.saved)
        assertEquals(300_000L - 85_000 - 2_000 - 5_000, oct.free)
        assertEquals(5_000L, Budget.savedInYear(data, 2026))
    }

    @Test fun potStaysOnAccountAtMonthEnd() {
        // Kfz 600 € am 15.11., Topf seit Januar: Ende Oktober liegen 10 x 50 € darin.
        val data = AppData(recurring = listOf(salary, car), settings = Settings(potStart = d(2026, 1, 1)))
        assertEquals(50_000L, de.kewl.cashdy.domain.Reserve.heldAfter(data, Periods.calendar(d(2026, 10, 1))))
        // Ende November ist die Rechnung bezahlt, der Topf fängt für 2027 neu an.
        assertEquals(5_000L, de.kewl.cashdy.domain.Reserve.heldAfter(data, Periods.calendar(d(2026, 12, 1))))
    }

    @Test fun plannedEntriesCountOnlyInTheirOwnMonth() {
        val data = AppData(
            recurring = listOf(salary),
            entries = listOf(
                Entry("e1", 2_000, d(2026, 10, 10), EntryKind.EXPENSE, "food", "REWE"),
                Entry("e2", 4_000, d(2026, 10, 30), EntryKind.EXPENSE, "shop", "lidl.de"),
                Entry("e3", 4_000, d(2026, 12, 30), EntryKind.EXPENSE, "shop", "lidl.de"),
            ),
        )
        val today = d(2026, 10, 15)
        val oct = Budget.summary(data, Periods.calendar(today), today)
        // Der 30.10. geht schon jetzt von „Noch frei“ ab, der 30.12. noch nicht.
        assertEquals(6_000L, oct.spent)
        assertEquals(4_000L, oct.planned)
        assertEquals(300_000L - 6_000, oct.free)
        val dec = Budget.summary(data, Periods.calendar(d(2026, 12, 1)), today)
        assertEquals(4_000L, dec.spent)
        assertEquals(4_000L, dec.planned)
        // Am Tag selbst ist es ein ganz normaler Eintrag.
        assertEquals(0L, Budget.summary(data, Periods.calendar(today), d(2026, 10, 30)).planned)
    }
}
