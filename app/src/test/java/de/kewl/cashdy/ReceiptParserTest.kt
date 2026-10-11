package de.kewl.cashdy

import de.kewl.cashdy.data.DefaultCategories
import de.kewl.cashdy.ocr.OcrLine
import de.kewl.cashdy.ocr.ReceiptLayout
import de.kewl.cashdy.ocr.ReceiptParser
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {

    @Test fun reweReceipt() {
        val text = """
            REWE
            Musterstraße 12
            12345 Berlin
            BANANE CHIQUITA 1,49 B
            MILCH 1,5% 1,19 B
            BROT 2,99 B
            Zwischensumme 5,67
            SUMME EUR 5,67
            Geg. EC-Karte 5,67
            MwSt 7% 0,37
            Datum: 03.10.2026 14:12
        """.trimIndent()
        val r = ReceiptParser.parse(text)
        assertEquals(567L, r.total)
        assertEquals("REWE", r.merchant)
        assertEquals(java.time.LocalDate.of(2026, 10, 3), r.date)
        assertFalse(r.paidCash)
        assertEquals(DefaultCategories.FOOD, r.categoryGuess)
    }

    @Test fun cashBakeryTotalOnNextLine() {
        val text = """
            Bäckerei Schmidt
            Brötchen 0,45
            Croissant 1,30
            Gesamt
            4,50
            Bar 5,00
            Rückgeld 0,50
        """.trimIndent()
        val r = ReceiptParser.parse(text)
        assertEquals(450L, r.total)
        assertTrue(r.paidCash)
    }

    @Test fun fallsBackToLargestAmount() {
        val r = ReceiptParser.parse("Kiosk am Eck\nCola 2,50\nChips 1,99\n4,49")
        assertEquals(449L, r.total)
    }

    // Echte Ausgaben der Texterkennung (ML Kit) zu zwei Fotos, Personendaten ersetzt.
    // Die Spalten kommen getrennt: "Summe" und "EUR 10,00" stehen weit auseinander im Text.
    private fun scanned(name: String): String {
        val json = javaClass.getResource("/receipts/$name.json")!!.readText()
        val lines = Json.decodeFromString(ListSerializer(OcrLine.serializer()), json)
        return ReceiptLayout.rows(lines).joinToString("\n")
    }

    private val today = java.time.LocalDate.of(2026, 10, 11)

    @Test fun pharmacyFromPhoto() {
        val rows = scanned("apotheke")
        assertTrue(rows, rows.lines().any { it.startsWith("Summe") && it.contains("10,00") })
        val r = ReceiptParser.parse(rows, today)
        assertEquals(1000L, r.total)
        assertTrue(r.sure)
        assertEquals("Melchendorfer Apotheke", r.merchant)
        assertFalse(r.paidCash) // "gegeben Kreditkarte"
        assertEquals(DefaultCategories.HEALTH, r.categoryGuess)
        assertEquals(listOf(1000L, 500L), r.candidates.take(2))
    }

    @Test fun hornbachFromPhoto() {
        val r = ReceiptParser.parse(scanned("hornbach"), today)
        assertEquals(6915L, r.total)
        assertTrue(r.sure)
        assertEquals("Hornbach", r.merchant)
        assertEquals(java.time.LocalDate.of(2026, 7, 14), r.date)
        assertFalse(r.paidCash) // "GEGEBEN VISA"
        assertEquals(DefaultCategories.HOUSEHOLD, r.categoryGuess)
        assertFalse(1407L in r.candidates) // Datum ist kein Betrag
    }
}
