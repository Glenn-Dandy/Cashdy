package de.kewl.cashdy.ocr

import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.domain.Merchants
import de.kewl.cashdy.domain.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ReceiptInfo(val total: Cents?, val merchant: String, val date: LocalDate?, val paidCash: Boolean, val categoryGuess: String?)

/**
 * Wertet den erkannten Text eines Kassenbons aus.
 *
 * Summe: die Zahl in der Zeile mit "Summe", "Gesamt", "zu zahlen" o. ä. (bei mehreren die
 * letzte, denn Zwischensummen stehen weiter oben). Gibt es keine solche Zeile, gilt der
 * größte Betrag auf dem Bon. Händler: die erste Zeile mit Buchstaben, die nicht nach
 * Adresse oder Begrüßung aussieht.
 */
object ReceiptParser {

    private val totalWords = listOf("summe", "gesamt", "zu zahlen", "zuzahlen", "total", "betrag", "endbetrag", "gesamtbetrag", "zahlbetrag", "eur summe")
    private val notTotalWords = listOf("zwischensumme", "mwst", "ust", "netto", "steuer", "rückgeld", "rueckgeld", "gegeben", "pfand", "rabatt", "punkte", "payback")
    private val price = Regex("""(-?\d{1,4}(?:[.]\d{3})*[,.]\d{2})(?!\d)""")
    private val dateRx = Regex("""(\d{1,2})[./](\d{1,2})[./](\d{2,4})""")
    private val skipMerchant = Regex("""(?i)(str\.|straße|strasse|platz|weg\b|\d{5}|tel|fax|www\.|http|willkommen|herzlich|kassenbon|beleg|bon-nr|filiale|uid|st-nr|steuer)""")

    fun parse(text: String): ReceiptInfo {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val lower = lines.map { it.lowercase() }

        var total: Cents? = null
        for (i in lines.indices) {
            val l = lower[i]
            if (totalWords.any { l.contains(it) } && notTotalWords.none { l.contains(it) }) {
                // Betrag in derselben Zeile, sonst in der nächsten (OCR trennt Spalten oft).
                val cand = price.findAll(lines[i]).lastOrNull()?.value ?: lines.getOrNull(i + 1)?.let { price.find(it)?.value }
                cand?.let { Money.parse(it) }?.takeIf { it > 0 }?.let { total = it }
            }
        }
        if (total == null) {
            total = lines.filter { l -> notTotalWords.none { l.lowercase().contains(it) } }
                .flatMap { l -> price.findAll(l).mapNotNull { Money.parse(it.value) }.toList() }
                .filter { it in 1..1_000_000 }
                .maxOrNull()
        }

        val merchantLine = lines.take(8).firstOrNull { l ->
            l.count { it.isLetter() } >= 3 && !skipMerchant.containsMatchIn(l) && price.find(l) == null
        }.orEmpty()
        val merchant = Merchants.pretty(merchantLine)

        val date = dateRx.findAll(text).firstNotNullOfOrNull { m ->
            val (d, mo, y) = m.destructured
            val year = if (y.length == 2) "20$y" else y
            runCatching {
                LocalDate.parse("${d.padStart(2, '0')}.${mo.padStart(2, '0')}.$year", DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            }.getOrNull()?.takeIf { !it.isAfter(LocalDate.now().plusDays(1)) && it.isAfter(LocalDate.now().minusYears(2)) }
        }

        val all = lower.joinToString(" ")
        val cash = Regex("""\bbar\b|bargeld|gegeben|rückgeld|rueckgeld""").containsMatchIn(all) &&
            !Regex("""ec-karte|girocard|kartenzahlung|visa|mastercard|kontaktlos""").containsMatchIn(all)

        return ReceiptInfo(total, merchant, date, cash, Merchants.guess(merchantLine + " " + all.take(200)))
    }
}
