package de.kewl.cashdy

import de.kewl.cashdy.data.DetectedKind
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.detect.NotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Formulierungen nach dem Muster der echten Meldungen. Sobald der Rekorder echte Texte von
 * DKB und PayPal liefert, kommen sie hier als eigene Fälle dazu.
 */
class NotificationParserTest {
    private val paypal = "com.paypal.android.p2pmobile"
    private val dkb = "de.dkb.portalapp"
    private val wallet = "com.google.android.apps.walletnfcrel"

    @Test fun paypalAuthorized() {
        val p = NotificationParser.parse(paypal, "Zahlung autorisiert", "Du hast eine Zahlung in Höhe von 12,49 € an REWE autorisiert.")!!
        assertEquals(1249L, p.amount)
        assertEquals("REWE", p.merchant)
        assertEquals(DetectedKind.EXPENSE, p.kind)
        assertEquals(PayMethod.PAYPAL, p.method)
    }

    @Test fun paypalPurchaseAt() {
        val p = NotificationParser.parse(paypal, "Einkauf bei Zalando", "Du hast 59,95 € bezahlt.")!!
        assertEquals(5995L, p.amount)
        assertEquals("Zalando", p.merchant)
    }

    @Test fun paypalSentTo() {
        val p = NotificationParser.parse(paypal, "", "Du hast 25,00 € an Max Mustermann gesendet")!!
        assertEquals(2500L, p.amount)
        assertEquals("Max Mustermann", p.merchant)
    }

    @Test fun paypalReceived() {
        val p = NotificationParser.parse(paypal, "Geld erhalten", "Anna Beispiel hat dir 30,00 € gesendet.")!!
        assertEquals(DetectedKind.INCOME, p.kind)
        assertEquals(3000L, p.amount)
    }

    @Test fun paypalRefund() {
        val p = NotificationParser.parse(paypal, "Rückerstattung", "Du hast eine Rückerstattung über 19,99 € von MediaMarkt erhalten.")!!
        assertEquals(DetectedKind.REFUND, p.kind)
        assertEquals(1999L, p.amount)
    }

    @Test fun englishPaypal() {
        val p = NotificationParser.parse(paypal, "", "You sent €12.49 to Spotify")!!
        assertEquals(1249L, p.amount)
        assertEquals("Spotify", p.merchant)
    }

    @Test fun dkbCardPayment() {
        val p = NotificationParser.parse(dkb, "Kartenzahlung", "Zahlung über 23,80 € bei ARAL TANKSTELLE")!!
        assertEquals(2380L, p.amount)
        assertEquals(DetectedKind.EXPENSE, p.kind)
        assertEquals(PayMethod.CARD, p.method)
        assertTrue(p.merchant.contains("Aral", ignoreCase = true))
    }

    @Test fun dkbCashWithdrawal() {
        val p = NotificationParser.parse(dkb, "Bargeldauszahlung", "Es wurden 200,00 € am Geldautomaten ausgezahlt.")!!
        assertEquals(DetectedKind.CASH, p.kind)
        assertEquals(20000L, p.amount)
        assertEquals(PayMethod.CASH, p.method)
    }

    @Test fun dkbIncoming() {
        val p = NotificationParser.parse(dkb, "Zahlungseingang", "Gutschrift über 3.150,00 € von Muster GmbH")!!
        assertEquals(DetectedKind.INCOME, p.kind)
        assertEquals(315000L, p.amount)
    }

    @Test fun dkbPaypalDebitIsMarked() {
        val p = NotificationParser.parse(dkb, "Lastschrift", "Lastschrift über 12,49 € an PayPal Europe S.a.r.l. et Cie S.C.A")!!
        assertTrue(p.paypalViaBank)
        assertEquals(PayMethod.TRANSFER, p.method)
    }

    @Test fun googleWallet() {
        val p = NotificationParser.parse(wallet, "EDEKA", "12,30 € mit Visa ••1234")!!
        assertEquals(1230L, p.amount)
        assertEquals("EDEKA", p.merchant)
    }

    @Test fun tanAndLoginAreIgnored() {
        assertNull(NotificationParser.parse(dkb, "Bitte bestätigen", "Bitte gib die Anmeldung frei."))
        assertNull(NotificationParser.parse(dkb, "Freigabe erforderlich", "Bitte bestätige den Auftrag über 50,00 €"))
        assertNull(NotificationParser.parse(dkb, "Neue Nachricht", "Du hast eine neue Nachricht im Postfach"))
    }

    @Test fun amountsInDifferentFormats() {
        assertEquals(123456L, NotificationParser.findAmount("Betrag 1.234,56 EUR"))
        assertEquals(999L, NotificationParser.findAmount("€9,99 bezahlt"))
        assertNotNull(NotificationParser.findAmount("-12,49 €"))
        assertNull(NotificationParser.findAmount("Bestellung 12345 versandt"))
    }

    // Echte Meldungen der aktuellen DKB-App (Rekorder, 13.10.2026).
    private val dkbNew = "com.dkbcodefactory.banking"

    @Test fun dkbNewReserved() {
        val p = NotificationParser.parse(dkbNew, "Umsatz vorgemerkt (*2872)", "-12,50 Euro an AMAZON PAYMENTS EUROPE S.C.A.")!!
        assertEquals(DetectedKind.EXPENSE, p.kind)
        assertEquals(1250L, p.amount)
        assertTrue(p.merchant.lowercase().startsWith("amazon"))
        assertEquals("DKB", NotificationParser.app(dkbNew)!!.name)
    }

    @Test fun dkbNewReservedFreenet() {
        val p = NotificationParser.parse(dkbNew, "Umsatz vorgemerkt (*2872)", "-10,99 Euro an freenet DLS GmbH")!!
        assertEquals(DetectedKind.EXPENSE, p.kind)
        assertEquals(1099L, p.amount)
        assertTrue(p.merchant.lowercase().startsWith("freenet"))
    }

    @Test fun dkbNewBookedPaypal() {
        val p = NotificationParser.parse(dkbNew, "Umsatz gebucht (*2872)", "-7,22 Euro an PayPal Europe S.a.r.l. et Cie S.C.A")!!
        assertEquals(DetectedKind.EXPENSE, p.kind)
        assertEquals(722L, p.amount)
        assertTrue(p.paypalViaBank)
    }

    // Echte Meldungen vom 13.10.2026: dieselbe Zahlung über DKB und Google Wallet.
    @Test fun dkbNewPaymentSuccessful() {
        val p = NotificationParser.parse(dkbNew, "Zahlung erfolgreich", "10,33 EUR (10,33 EUR), Sonderpreis Baumarkt, Visa Debitkarte ****2555")!!
        assertEquals(DetectedKind.EXPENSE, p.kind)
        assertEquals(1033L, p.amount)
        assertEquals("Sonderpreis Baumarkt", p.merchant)
    }

    @Test fun walletSameBaumarkt() {
        val p = NotificationParser.parse("com.google.android.apps.walletnfcrel", "Sonderpreis Baumarkt", "10,33 € mit Visa Debitkarte ••2555")!!
        assertEquals(1033L, p.amount)
        assertEquals("Sonderpreis Baumarkt", p.merchant)
    }
}
