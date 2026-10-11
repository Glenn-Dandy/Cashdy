package de.kewl.cashdy

import de.kewl.cashdy.data.DefaultCategories
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.importer.CsvImport
import de.kewl.cashdy.importer.RecurringDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ImportTest {

    private val dkbNew = """
        "Girokonto";"DE12 1203 0000 1234 5678 90"
        ""
        "Kontostand vom 10.10.2026:";"1.234,56 €"
        ""
        "Buchungsdatum";"Wertstellung";"Status";"Zahlungspflichtige*r";"Zahlungsempfänger*in";"Verwendungszweck";"Umsatztyp";"IBAN";"Betrag (€)";"Gläubiger-ID";"Mandatsreferenz";"Kundenreferenz"
        "01.10.26";"01.10.26";"Gebucht";"Max Muster";"Wohnbau GmbH";"Miete Oktober";"Ausgang";"DE001";"-850";"";"";""
        "15.10.26";"15.10.26";"Gebucht";"Max Muster";"Netflix International B.V.";"Netflix";"Ausgang";"";"-13,99";"";"";""
        "28.09.26";"28.09.26";"Gebucht";"Muster AG";"Max Muster";"Gehalt 09/2026";"Eingang";"DE002";"3.150,00";"";"";""
        "15.09.26";"15.09.26";"Gebucht";"Max Muster";"Netflix International B.V.";"Netflix";"Ausgang";"";"-13,99";"";"";""
        "03.09.26";"03.09.26";"Gebucht";"Max Muster";"REWE Markt GmbH";"REWE SAGT DANKE 4711";"Ausgang";"";"-42,17";"";"";""
        "01.09.26";"01.09.26";"Gebucht";"Max Muster";"Wohnbau GmbH";"Miete September";"Ausgang";"DE001";"-850";"";"";""
        "28.08.26";"28.08.26";"Gebucht";"Muster AG";"Max Muster";"Gehalt 08/2026";"Eingang";"DE002";"3.150,00";"";"";""
        "15.08.26";"15.08.26";"Gebucht";"Max Muster";"Netflix International B.V.";"Netflix";"Ausgang";"";"-12,99";"";"";""
        "05.08.26";"05.08.26";"Gebucht";"Max Muster";"Geldautomat";"Bargeldauszahlung";"Bargeld";"";"-200,00";"";"";""
        "01.08.26";"01.08.26";"Gebucht";"Max Muster";"Wohnbau GmbH";"Miete August";"Ausgang";"DE001";"-850";"";"";""
        "28.07.26";"28.07.26";"Gebucht";"Muster AG";"Max Muster";"Gehalt 07/2026";"Eingang";"DE002";"3.150,00";"";"";""
    """.trimIndent()

    private val dkbOld = """
        "Kontonummer:";"DE12345 / Girokonto";
        "Von:";"01.08.2023";
        "Buchungstag";"Wertstellung";"Buchungstext";"Auftraggeber / Begünstigter";"Verwendungszweck";"Kontonummer";"BLZ";"Betrag (EUR)";"Gläubiger-ID";"Mandatsreferenz";"Kundenreferenz";
        "02.08.2023";"02.08.2023";"Lastschrift";"Vodafone GmbH";"Rechnung 123";"DE9";"X";"-39,99";"";"";"";
        "03.08.2023";"03.08.2023";"Kartenzahlung";"EDEKA Center";"EDEKA SAGT DANKE";"";"";"-23,50";"";"";"";
    """.trimIndent()

    private val paypal = """
        "Datum","Uhrzeit","Zeitzone","Name","Typ","Status","Währung","Brutto","Gebühr","Netto","Absender E-Mail-Adresse","Empfänger E-Mail-Adresse","Transaktionscode"
        "05.10.2026","12:01:00","CEST","Zalando SE","PayPal Express-Zahlung","Abgeschlossen","EUR","-59,95","0,00","-59,95","","",""
        "05.10.2026","12:01:00","CEST","","Bankgutschrift auf PayPal-Konto","Abgeschlossen","EUR","59,95","0,00","59,95","","",""
        "07.10.2026","09:10:00","CEST","Steam","Zahlung im Einzugsverfahren mit Zahlungsrechnung","Abgeschlossen","USD","-9,99","0,00","-9,99","","",""
        "08.10.2026","18:00:00","CEST","Anna B.","Zahlung erhalten","Abgeschlossen","EUR","20,00","0,00","20,00","","",""
    """.trimIndent()

    @Test fun readsNewDkbFormat() {
        val r = CsvImport.parse(dkbNew)
        assertEquals("DKB", r.source)
        assertEquals(11, r.rows.size)
        assertEquals(PayMethod.TRANSFER, r.rows.first().method) // mit IBAN = Überweisung/Lastschrift
        val rent = r.rows.first()
        assertEquals(LocalDate.of(2026, 10, 1), rent.date)
        assertEquals(-85_000L, rent.amount)
        assertEquals("Wohnbau GmbH", rent.counterparty)
        val salary = r.rows.first { it.amount > 0 }
        assertEquals("Muster AG", salary.counterparty)
        assertEquals(PayMethod.CASH, r.rows.first { it.counterparty == "Geldautomat" }.method)
    }

    @Test fun readsOldDkbFormat() {
        val r = CsvImport.parse(dkbOld)
        assertEquals(2, r.rows.size)
        assertEquals("Vodafone GmbH", r.rows[0].counterparty)
        assertEquals(PayMethod.TRANSFER, r.rows[0].method)
        assertEquals(-2350L, r.rows[1].amount)
    }

    @Test fun readsPaypalAndSkipsFunding() {
        val r = CsvImport.parse(paypal)
        assertEquals("PayPal", r.source)
        assertEquals(2, r.rows.size) // Zalando + Anna; Aufladung und USD übersprungen
        assertEquals(-5995L, r.rows[0].amount)
        assertEquals(PayMethod.PAYPAL, r.rows[0].method)
        assertEquals(2, r.skipped)
    }

    @Test fun detectsRecurringWithPriceHistory() {
        val sugg = RecurringDetector.detect(CsvImport.parse(dkbNew).rows)
        val names = sugg.map { it.recurring.name }
        assertTrue(names.toString(), names.any { it.startsWith("Wohnbau") })
        assertTrue(names.toString(), names.any { it.startsWith("Netflix") })
        val nf = sugg.first { it.recurring.name.startsWith("Netflix") }.recurring
        assertEquals(listOf(1299L, 1399L), nf.prices.map { it.amount })
        assertEquals(LocalDate.of(2026, 9, 15), nf.prices[1].from)
        assertEquals(DefaultCategories.SUBS, nf.categoryId)
        val sal = sugg.first { it.recurring.income }.recurring
        assertTrue(sal.salary)
        assertEquals(1, sal.intervalMonths)
        // REWE (einmal) und Geldautomat sind nicht regelmäßig
        assertTrue(sugg.none { it.recurring.name.contains("Rewe", true) })
    }

    @Test fun windows1252IsDecoded() {
        val bytes = "\"Begünstigter\";\"Betrag\"".toByteArray(charset("windows-1252"))
        assertTrue(CsvImport.decode(bytes).contains("Begünstigter"))
    }
}
