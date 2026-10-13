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

    private val dkbNew = "com.dkbcodefactory.banking"

    @Test fun reservedThenBookedMergesOnce() {
        val (d1, r1) = Detection.handle(AppData(), dkbNew, "Umsatz vorgemerkt (*2872)", "-10,99 Euro an freenet DLS GmbH", t0)
        val (d2, _) = Detection.accept(d1, (r1 as DetectionResult.Pending).detected.id, de.kewl.cashdy.data.DefaultCategories.PHONE)
        val (d3, r2) = Detection.handle(d2, dkbNew, "Umsatz gebucht (*2872)", "-10,99 Euro an freenet DLS GmbH", t0 + 2 * 86_400_000L)
        assertTrue(r2 is DetectionResult.Merged)
        assertEquals(1, d3.entries.size)
        // Eine zweite Buchung ohne eigene Vormerkung ist eine neue Zahlung.
        val (_, r3) = Detection.handle(d3, dkbNew, "Umsatz gebucht (*2872)", "-10,99 Euro an freenet DLS GmbH", t0 + 3 * 86_400_000L)
        assertTrue(r3 is DetectionResult.Pending)
    }

    @Test fun ignoredReservationAlsoSwallowsBooking() {
        val (d1, r1) = Detection.handle(AppData(), dkbNew, "Umsatz vorgemerkt (*2872)", "-12,50 Euro an AMAZON PAYMENTS EUROPE S.C.A.", t0)
        val d2 = Detection.ignore(d1, (r1 as DetectionResult.Pending).detected.id)
        val (_, r2) = Detection.handle(d2, dkbNew, "Umsatz gebucht (*2872)", "-12,50 Euro an AMAZON PAYMENTS EUROPE S.C.A.", t0 + 86_400_000L)
        assertTrue(r2 is DetectionResult.Merged)
    }

    @Test fun sameAmountThisMonthIsFlagged() {
        val (d1, r1) = Detection.handle(AppData(), dkbNew, "Umsatz vorgemerkt (*2872)", "-10,99 Euro an freenet DLS GmbH", t0)
        r1 as DetectionResult.Pending
        assertEquals(null, Detection.maybeDuplicate(d1, r1.detected))
        val (d2, _) = Detection.accept(d1, r1.detected.id, de.kewl.cashdy.data.DefaultCategories.PHONE)
        // Gleicher Betrag, aber ein anderer Weg (Lastschrift statt Vormerkung): wartet, mit Hinweis.
        val (d3, r2) = Detection.handle(d2, dkbNew, "Lastschrift", "-10,99 Euro an freenet DLS GmbH", t0 + 2 * 86_400_000L)
        r2 as DetectionResult.Pending
        val warn = Detection.maybeDuplicate(d3, r2.detected)!!
        assertTrue(warn, warn.contains("10,99") && warn.contains("freenet", ignoreCase = true))
        // Der schon übernommene Eintrag warnt nicht vor sich selbst.
        val accepted = d3.detected.first { it.id == r1.detected.id }
        assertEquals(null, Detection.maybeDuplicate(d3.copy(detected = d3.detected - d3.detected.first { it.id == r2.detected.id }), accepted))
    }

    @Test fun sameAmountAsFixedCostIsFlagged() {
        val day = Detection.dayOf(de.kewl.cashdy.data.Detected("x", dkbNew, "DKB", "", "", 0, "", de.kewl.cashdy.data.DetectedKind.EXPENSE, de.kewl.cashdy.data.PayMethod.CARD, t0))
        val handy = Recurring("h", "Handy", firstDue = day.withDayOfMonth(1).minusMonths(2), prices = listOf(PriceStep(day.withDayOfMonth(1).minusMonths(2), 1099)))
        val (d1, r) = Detection.handle(AppData(recurring = listOf(handy)), dkbNew, "Umsatz vorgemerkt (*2872)", "-10,99 Euro an freenet DLS GmbH", t0)
        r as DetectionResult.Pending
        val warn = Detection.maybeDuplicate(d1, r.detected)!!
        assertTrue(warn, warn.contains("Handy"))
    }

    @Test fun suspectedDuplicateIsNotAutoAccepted() {
        val base = AppData(settings = Settings(autoAccept = true, watchedApps = Settings.DEFAULT_WATCHED), merchantRules = mapOf(de.kewl.cashdy.domain.Merchants.normalize("freenet DLS GmbH") to de.kewl.cashdy.data.DefaultCategories.PHONE))
        val (d1, r1) = Detection.handle(base, dkbNew, "Umsatz vorgemerkt (*2872)", "-10,99 Euro an freenet DLS GmbH", t0)
        assertTrue(r1 is DetectionResult.AutoAccepted)
        val (_, r2) = Detection.handle(d1, dkbNew, "Lastschrift", "-10,99 Euro an freenet DLS GmbH", t0 + 86_400_000L)
        assertTrue(r2 is DetectionResult.Pending)
    }
}
