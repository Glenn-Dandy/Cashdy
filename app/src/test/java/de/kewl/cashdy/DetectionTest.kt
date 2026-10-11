package de.kewl.cashdy

import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.DetectedStatus
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.PriceStep
import de.kewl.cashdy.data.Recurring
import de.kewl.cashdy.data.Settings
import de.kewl.cashdy.detect.Detection
import de.kewl.cashdy.detect.DetectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DetectionTest {
    private val paypal = "com.paypal.android.p2pmobile"
    private val dkb = "de.dkb.portalapp"
    private val wallet = "com.google.android.apps.walletnfcrel"
    private val t0 = 1_790_000_000_000L

    @Test fun pendingThenAcceptLearnsMerchant() {
        val (d1, r) = Detection.handle(AppData(), paypal, "", "Du hast eine Zahlung in Höhe von 12,49 € an REWE autorisiert.", t0)
        r as DetectionResult.Pending
        assertEquals("food", r.suggestedCategory) // aus der Stichwortliste
        val (d2, e) = Detection.accept(d1, r.detected.id, "food")
        assertEquals(1, d2.entries.size)
        assertEquals(EntryKind.EXPENSE, e!!.kind)
        assertEquals("food", d2.merchantRules["rewe"])
        assertTrue(Detection.pending(d2).isEmpty())
    }

    @Test fun sameNotificationTwiceIsIgnored() {
        val text = "Du hast 5,00 € an Bäcker gesendet"
        val (d1, _) = Detection.handle(AppData(), paypal, "", text, t0)
        val (d2, r) = Detection.handle(d1, paypal, "", text, t0 + 1000)
        assertEquals(DetectionResult.Nothing, r)
        assertEquals(1, d2.detected.size)
    }

    @Test fun walletAndBankForSamePaymentMerge() {
        val (d1, _) = Detection.handle(AppData(), wallet, "EDEKA", "12,30 € mit Visa", t0)
        val (d2, r) = Detection.handle(d1, dkb, "Kartenzahlung", "Zahlung über 12,30 €", t0 + 60_000)
        assertTrue(r is DetectionResult.Merged)
        assertEquals(1, Detection.pending(d2).size)
    }

    @Test fun bankPaypalDebitMergesWithPaypalPayment() {
        val (d1, r1) = Detection.handle(AppData(), paypal, "", "Du hast eine Zahlung in Höhe von 12,49 € an REWE autorisiert.", t0)
        val (d2, _) = Detection.accept(d1, (r1 as DetectionResult.Pending).detected.id, "food")
        val twoDaysLater = t0 + 2 * 86_400_000L
        val (d3, r) = Detection.handle(d2, dkb, "Lastschrift", "Lastschrift über 12,49 € an PayPal Europe S.a.r.l.", twoDaysLater)
        r as DetectionResult.Merged
        assertTrue(r.detected.mergeNote.contains("REWE"))
        assertEquals(1, d3.entries.size)
    }

    @Test fun debitOfKnownFixedCostIsNotCountedTwice() {
        val nf = Recurring("nf", "Netflix", firstDue = LocalDate.of(2026, 1, 15), prices = listOf(PriceStep(LocalDate.of(2026, 1, 15), 1399)))
        val (_, r) = Detection.handle(AppData(recurring = listOf(nf)), dkb, "Lastschrift", "Lastschrift über 13,99 € an Netflix International B.V.", t0)
        r as DetectionResult.Merged
        assertEquals(DetectedStatus.MERGED, r.detected.status)
    }

    @Test fun autoAcceptKnownMerchant() {
        val d0 = AppData(settings = Settings(autoAccept = true), merchantRules = mapOf("rewe" to "food"))
        val (d1, r) = Detection.handle(d0, paypal, "", "Du hast eine Zahlung in Höhe von 7,10 € an REWE autorisiert.", t0)
        assertTrue(r is DetectionResult.AutoAccepted)
        assertEquals(710L, d1.entries.single().amount)
    }

    @Test fun cashGoesToWallet() {
        val (d1, r) = Detection.handle(AppData(), dkb, "Bargeldauszahlung", "200,00 € am Geldautomaten", t0)
        val (d2, e) = Detection.accept(d1, (r as DetectionResult.Pending).detected.id, null, asCashWithdrawal = true)
        assertEquals(EntryKind.CASH_WITHDRAWAL, e!!.kind)
        assertEquals(20_000L, de.kewl.cashdy.domain.Budget.wallet(d2))
    }

    @Test fun unwatchedAppIsOnlyRecorded() {
        val d0 = AppData(settings = Settings(recorder = true))
        val (d1, r) = Detection.handle(d0, "de.some.bank", "Umsatz", "Kartenzahlung 9,99 € bei Kiosk", t0)
        assertEquals(DetectionResult.Nothing, r)
        assertEquals(1, d1.recorded.size)
        assertTrue(d1.detected.isEmpty())
    }
}
