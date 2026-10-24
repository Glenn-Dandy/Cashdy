package de.kewl.cashdy

import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.BankCheck
import de.kewl.cashdy.data.Detected
import de.kewl.cashdy.data.DetectedKind
import de.kewl.cashdy.data.Entry
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.data.PeriodMode
import de.kewl.cashdy.data.PriceStep
import de.kewl.cashdy.data.Recurring
import de.kewl.cashdy.data.Settings
import de.kewl.cashdy.domain.AccountCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AccountCheckTest {
    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)
    private val settings = Settings(periodMode = PeriodMode.FIXED_DAY, periodDay = 27, startDay = d(10, 13), startBalance = 155_470)
    private val handy = Recurring("h", "Handy", firstDue = d(10, 15), prices = listOf(PriceStep(d(10, 15), 1_099)))
    private val early = Recurring("m", "Miete", firstDue = d(10, 1), prices = listOf(PriceStep(d(10, 1), 36_000)))
    private val salary = Recurring("s", "Gehalt", income = true, firstDue = d(9, 30), prices = listOf(PriceStep(d(9, 1), 120_000)), salary = true)

    private val data = AppData(
        settings = settings,
        recurring = listOf(handy, early, salary),
        entries = listOf(
            Entry("e1", 1_344, d(10, 13), EntryKind.EXPENSE, "food", "Lidl"),
            Entry("e2", 3_800, d(10, 21), EntryKind.EXPENSE, "other", "Amtsgericht", method = PayMethod.CASH),
            Entry("e3", 30_212, d(10, 21), EntryKind.INCOME, "income_other", "IKK"),
            Entry("e4", 740, d(10, 16), EntryKind.EXPENSE, "food", "Späti", method = PayMethod.CASH),
            Entry("e5", 2_000, d(10, 18), EntryKind.CASH_WITHDRAWAL),
            Entry("e6", 4_000, d(10, 30), EntryKind.EXPENSE, "other", "lidl.de"),
        ),
    )

    @Test fun expectedFromStartBalance() {
        // Start 1.554,70, Lidl am Starttag zählt, Handy am 15.10., IKK rein, 20 € abgehoben.
        // Bar-Einträge, Miete vor dem Start, Gehalt im September und Vorgemerktes zählen nicht.
        assertEquals(155_470L - 1_344 - 1_099 + 30_212 - 2_000, AccountCheck.expected(data, d(10, 24)))
    }

    @Test fun cashEntryExplainsGap() {
        val expected = AccountCheck.expected(data, d(10, 24))!!
        val m = AccountCheck.matches(data, expected - (expected - 3_800), d(10, 24))
        assertEquals("e2", (m.single() as AccountCheck.Match.WrongMethod).entry.id)
        // Konto hat mehr: eine Kartenzahlung war vielleicht bar.
        val other = AccountCheck.matches(data, -1_344, d(10, 24))
        assertEquals("e1", (other.single() as AccountCheck.Match.WrongMethod).entry.id)
    }

    @Test fun pendingDetectionExplainsGap() {
        val withPending = data.copy(detected = listOf(Detected("x", "pkg", "DKB", "", "", 4_500, "Shell", DetectedKind.EXPENSE, PayMethod.CARD, 0)))
        assertTrue(AccountCheck.matches(withPending, 4_500, d(10, 24)).single() is AccountCheck.Match.Pending)
    }

    @Test fun laterCheckIsNewAnchor() {
        // Geprüft am 21.10. um 12 Uhr: Einträge danach zählen, die vorher am selben Tag nicht.
        val at = 1_000L
        val checked = data.copy(
            settings = settings.copy(bankCheck = BankCheck(d(10, 21), 100_000, at)),
            entries = data.entries.map { if (it.id == "e3") it.copy(createdAt = at - 1) else it.copy(createdAt = at + 1) } +
                Entry("e7", 500, d(10, 21), EntryKind.EXPENSE, "food", "Bäcker", createdAt = at + 5),
        )
        assertEquals(100_000L - 500, AccountCheck.expected(checked, d(10, 24)))
    }

    @Test fun unknownWithoutBalance() {
        assertNull(AccountCheck.expected(AppData(), d(10, 24)))
    }

    @Test fun notCashOnWalletCheck() {
        assertEquals("e2", AccountCheck.notCash(data, 3_800, d(10, 24))?.id)
        assertNull(AccountCheck.notCash(data, 3_799, d(10, 24)))
    }
}
