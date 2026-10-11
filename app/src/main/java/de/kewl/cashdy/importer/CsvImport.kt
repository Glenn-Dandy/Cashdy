package de.kewl.cashdy.importer

import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.domain.Merchants
import de.kewl.cashdy.domain.Money
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class CsvRow(
    val date: LocalDate,
    /** Mit Vorzeichen: negativ = Ausgabe, positiv = Eingang. */
    val amount: Cents,
    val counterparty: String,
    val purpose: String,
    val method: PayMethod,
)

data class CsvResult(val source: String, val rows: List<CsvRow>, val skipped: Int)

/**
 * Liest Kontoauszüge als CSV. Erkannt wird über die Spaltenköpfe, nicht über ein festes
 * Format je Bank: DKB (alt und neu, Giro und Kreditkarte), PayPal, Sparkasse/ING-ähnliche
 * Exporte funktionieren so ohne eigene Vorlage.
 */
object CsvImport {

    fun decode(bytes: ByteArray): String {
        // BOM entfernen; ungültiges UTF-8 deutet auf die alten Windows-1252-Exporte hin.
        val utf = String(bytes, Charsets.UTF_8)
        val text = if (utf.contains('�')) String(bytes, Charset.forName("windows-1252")) else utf
        return text.removePrefix("﻿")
    }

    fun parse(text: String): CsvResult {
        val lines = text.lines().filter { it.isNotBlank() }
        val headerIdx = lines.indexOfFirst { isHeader(it) }
        require(headerIdx >= 0) { "Keine Kopfzeile mit Datum und Betrag gefunden" }
        val headerLine = lines[headerIdx]
        val sep = listOf(';', ',', '\t').maxBy { c -> headerLine.count { it == c } }
        val header = split(headerLine, sep).map { it.lowercase().trim() }

        fun col(vararg names: String): Int = names.firstNotNullOfOrNull { n ->
            header.indexOfFirst { it == n }.takeIf { it >= 0 }
        } ?: names.firstNotNullOfOrNull { n -> header.indexOfFirst { it.contains(n) }.takeIf { it >= 0 } } ?: -1

        val isPayPal = header.contains("brutto") && header.any { it.contains("typ") }
        val cDate = col("buchungsdatum", "buchungstag", "belegdatum", "datum", "date", "valuta")
        val cAmount = if (isPayPal) col("brutto") else col("betrag (€)", "betrag (eur)", "betrag", "umsatz", "amount", "brutto")
        val cPayee = col("zahlungsempfänger*in", "zahlungsempfänger", "empfänger", "begünstigter", "auftraggeber / begünstigter", "beguenstigter/zahlungspflichtiger", "name", "beschreibung", "payee")
        val cPayer = col("zahlungspflichtige*r", "zahlungspflichtiger", "auftraggeber", "name", "beschreibung")
        val cPurpose = col("verwendungszweck", "buchungstext", "beschreibung", "betreff", "description")
        val cType = col("typ", "umsatztyp", "buchungstext")
        val cStatus = col("status")
        val cCurrency = col("währung", "waehrung", "currency")
        val cIban = col("iban", "kontonummer")
        require(cDate >= 0 && cAmount >= 0) { "Spalten für Datum oder Betrag fehlen" }

        val rows = mutableListOf<CsvRow>()
        var skipped = 0
        for (line in lines.drop(headerIdx + 1)) {
            val f = split(line, sep)
            fun at(i: Int) = if (i in f.indices) f[i].trim() else ""
            val date = parseDate(at(cDate))
            val amount = Money.parse(at(cAmount))
            if (date == null || amount == null || amount == 0L) { skipped++; continue }
            if (cCurrency >= 0 && at(cCurrency).isNotBlank() && !at(cCurrency).equals("EUR", true)) { skipped++; continue }
            val status = at(cStatus).lowercase()
            if (status.contains("storniert") || status.contains("abgelehnt") || status.contains("ausstehend") && isPayPal) { skipped++; continue }
            val type = at(cType).lowercase()
            if (isPayPal && paypalFunding(type)) { skipped++; continue }

            val who = if (amount < 0) at(cPayee) else at(cPayer).ifBlank { at(cPayee) }
            val purpose = at(cPurpose)
            val counterparty = who.ifBlank { purpose.take(40) }
            val method = when {
                isPayPal -> PayMethod.PAYPAL
                type.contains("lastschrift") || type.contains("überweisung") || type.contains("dauerauftrag") || type.contains("gutschrift") -> PayMethod.TRANSFER
                type.contains("bargeld") || purpose.lowercase().contains("geldautomat") || who.lowercase().contains("geldautomat") -> PayMethod.CASH
                // Mit Gegenkonto (IBAN) ist es eine Überweisung oder Lastschrift, ohne meist die Karte.
                at(cIban).length >= 3 -> PayMethod.TRANSFER
                else -> PayMethod.CARD
            }
            rows += CsvRow(date, amount, counterparty, purpose, method)
        }
        return CsvResult(if (isPayPal) "PayPal" else guessBank(lines.take(headerIdx + 1).joinToString(" ")), rows, skipped)
    }

    /** PayPal listet Aufladungen vom Bankkonto und Umbuchungen extra, die sind keine Ausgaben. */
    private fun paypalFunding(type: String): Boolean = listOf(
        "bankgutschrift", "allgemeine abbuchung", "abbuchung", "überweisung auf", "auf bankkonto", "einbehalt", "freigabe",
        "währungsumrechnung", "konto aufladen", "general credit card deposit", "bank deposit", "withdrawal",
    ).any { type.contains(it) }

    private fun guessBank(head: String): String {
        val h = head.lowercase()
        return when {
            h.contains("dkb") || h.contains("zahlungsempfänger*in") || h.contains("belegdatum") -> "DKB"
            h.contains("sparkasse") || h.contains("auftragskonto") -> "Sparkasse"
            h.contains("ing") -> "ING"
            else -> "Bank"
        }
    }

    private fun isHeader(line: String): Boolean {
        val l = line.lowercase()
        val hasDate = listOf("buchungsdatum", "buchungstag", "belegdatum", "\"datum\"", "datum;", "datum,", "date").any { l.contains(it) }
        val hasAmount = listOf("betrag", "brutto", "amount", "umsatz").any { l.contains(it) }
        return hasDate && hasAmount
    }

    fun split(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQ = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQ && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQ = !inQ
                c == sep && !inQ -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    private val dateFormats = listOf("dd.MM.yyyy", "dd.MM.yy", "yyyy-MM-dd", "d.M.yyyy", "d.M.yy", "dd/MM/yyyy", "MM/dd/yyyy")
        .map { DateTimeFormatter.ofPattern(it) }

    fun parseDate(s: String): LocalDate? {
        val t = s.trim().take(10).trim()
        for (f in dateFormats) runCatching { return LocalDate.parse(t, f) }
        return null
    }

    /** Ist dieser Umsatz schon da (per Push erfasst oder früher importiert)? */
    fun isDuplicate(row: CsvRow, existing: List<de.kewl.cashdy.data.Entry>): Boolean =
        existing.any { e ->
            e.amount == kotlin.math.abs(row.amount) &&
                kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(e.date, row.date)) <= 3 &&
                (e.merchant.isBlank() || row.counterparty.isBlank() || Merchants.similar(e.merchant, row.counterparty) ||
                    row.counterparty.contains("paypal", true) && e.method == PayMethod.PAYPAL)
        }
}
