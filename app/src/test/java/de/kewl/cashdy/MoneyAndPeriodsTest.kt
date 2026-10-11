package de.kewl.cashdy

import de.kewl.cashdy.data.PeriodMode
import de.kewl.cashdy.data.Settings
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.Periods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MoneyAndPeriodsTest {

    @Test fun parsesGermanAndEnglishAmounts() {
        assertEquals(1249L, Money.parse("12,49"))
        assertEquals(1249L, Money.parse("12,49 €"))
        assertEquals(123456L, Money.parse("1.234,56"))
        assertEquals(123456L, Money.parse("1,234.56"))
        assertEquals(1249L, Money.parse("12.49"))
        assertEquals(1200L, Money.parse("12"))
        assertEquals(-1249L, Money.parse("-12,49"))
        assertEquals(-1249L, Money.parse("12,49-"))
        assertEquals(-1249L, Money.parse("−12,49 €"))
        assertEquals(1250L, Money.parse("12,5"))
        assertEquals(100000L, Money.parse("1.000"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
    }

    @Test fun formatsGerman() {
        assertEquals("1.284,49 €", Money.format(128449))
        assertEquals("0,05 €", Money.format(5))
        assertEquals("−12,00 €", Money.format(-1200))
        assertEquals("+3,00 €", Money.format(300, withSign = true))
        assertEquals("1.284", Money.euros(128449))
    }

    @Test fun calendarPeriod() {
        val p = Periods.periodFor(LocalDate.of(2026, 2, 14), Settings())
        assertEquals(LocalDate.of(2026, 2, 1), p.start)
        assertEquals(LocalDate.of(2026, 2, 28), p.end)
        assertEquals(15, p.daysLeft(LocalDate.of(2026, 2, 14)))
    }

    @Test fun fixedDayPeriod() {
        val s = Settings(periodMode = PeriodMode.FIXED_DAY, periodDay = 27, shiftWeekend = false)
        val p = Periods.periodFor(LocalDate.of(2026, 10, 11), s)
        assertEquals(LocalDate.of(2026, 9, 27), p.start)
        assertEquals(LocalDate.of(2026, 10, 26), p.end)
        val q = Periods.periodFor(LocalDate.of(2026, 10, 27), s)
        assertEquals(LocalDate.of(2026, 10, 27), q.start)
    }

    @Test fun fixedDayShiftsWeekendBackwards() {
        // 1. November 2026 ist ein Sonntag -> Monatsstart Freitag, 30. Oktober.
        val s = Settings(periodMode = PeriodMode.FIXED_DAY, periodDay = 1, shiftWeekend = true)
        val p = Periods.periodFor(LocalDate.of(2026, 10, 31), s)
        assertEquals(LocalDate.of(2026, 10, 30), p.start)
        assertEquals(LocalDate.of(2026, 11, 30), p.end) // 1.12.2026 ist Dienstag
        val before = Periods.periodFor(LocalDate.of(2026, 10, 29), s)
        assertEquals(LocalDate.of(2026, 10, 1), before.start)
        assertEquals(LocalDate.of(2026, 10, 29), before.end)
    }

    @Test fun fixedDayClampsShortMonths() {
        val s = Settings(periodMode = PeriodMode.FIXED_DAY, periodDay = 31, shiftWeekend = false)
        val p = Periods.periodFor(LocalDate.of(2026, 3, 5), s)
        assertEquals(LocalDate.of(2026, 2, 28), p.start)
        assertEquals(LocalDate.of(2026, 3, 30), p.end)
    }

    @Test fun incomePeriodFollowsConfirmedSalary() {
        val s = Settings(
            periodMode = PeriodMode.INCOME, periodDay = 27,
            periodStarts = listOf(LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 29)),
        )
        val p = Periods.periodFor(LocalDate.of(2026, 9, 10), s)
        assertEquals(LocalDate.of(2026, 8, 28), p.start)
        assertEquals(LocalDate.of(2026, 9, 28), p.end)
        val open = Periods.periodFor(LocalDate.of(2026, 10, 11), s)
        assertEquals(LocalDate.of(2026, 9, 29), open.start)
        assertEquals(true, open.estimatedEnd)
        // Vor dem ersten bekannten Eingang: fester Tag, aber nicht über den ersten Start hinaus.
        val early = Periods.periodFor(LocalDate.of(2026, 8, 10), s)
        assertEquals(LocalDate.of(2026, 7, 27), early.start)
        assertEquals(LocalDate.of(2026, 8, 26), early.end)
    }
}
