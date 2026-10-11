package de.kewl.cashdy

import de.kewl.cashdy.data.DefaultCategories
import de.kewl.cashdy.ocr.ReceiptParser
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
}
