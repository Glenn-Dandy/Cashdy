package de.kewl.cashdy.ocr

import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.domain.Merchants
import de.kewl.cashdy.domain.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Ergebnis eines Bons. [sure] heißt: Summe stand neben "Summe" o. ä. und, falls der Bon eine
 * Zahlungszeile hat, steht dort derselbe Betrag. [candidates] sind die Beträge zum Antippen,
 * falls die Summe doch nicht stimmt, wahrscheinlichste zuerst.
 */
data class ReceiptInfo(
    val total: Cents?,
    val merchant: String,
    val date: LocalDate?,
    val paidCash: Boolean,
    val categoryGuess: String?,
    val sure: Boolean = false,
    val candidates: List<Cents> = emptyList(),
)

/**
 * Wertet den Text eines Kassenbons aus, Zeile für Zeile so, wie sie auf dem Bon stehen
 * (siehe [ReceiptLayout]).
 *
 * Summe: der Betrag in der Zeile mit "Summe", "Gesamt", "zu zahlen" o. ä. (bei mehreren die
 * letzte, Zwischensummen stehen weiter oben). Gegenprobe ist die Zahlungszeile ("gegeben
 * Visa", "EC-Karte", "Bar"). Fehlt beides, gilt der größte Betrag, der mehrfach vorkommt,
 * sonst der größte überhaupt. Händler: die erste Zeile mit Buchstaben, die nicht nach
 * Adresse oder Begrüßung aussieht.
 */
object ReceiptParser {

    private val totalWords = Regex("""(?i)\b(summe|gesamt|gesamtbetrag|endbetrag|zahlbetrag|zu\s?zahlen|total|betrag)\b""")
    private val notTotalWords = Regex("""(?i)(zwischensumme|zw\.?-?summe|mwst|ust|netto|steuer|rückgeld|rueckgeld|wechselgeld|pfand|rabatt|punkte|payback|gegeben|geg\.)""")
    private val payWords = Regex("""(?i)(gegeben|geg\.|bezahlt|kartenzahlung|ec-karte|girocard|visa|mastercard|maestro|kreditkarte|debit|kontaktlos|\bbar\b)""")
    private val cardWords = Regex("""(?i)(ec-karte|ec karte|girocard|visa|mastercard|maestro|kreditkarte|karte|debit|kontaktlos|apple pay|google pay|v pay)""")
    private val cashWords = Regex("""(?i)(\bbar\b|bargeld|rückgeld|rueckgeld|wechselgeld)""")
    private val price = Regex("""(?<![\d.,])(-?\d{1,4}(?:\.\d{3})*,\d{2}|\d{1,4}\.\d{2})(?![\d.,]?\d)""")
    private val dateRx = Regex("""(\d{1,2})[./](\d{1,2})[./](\d{2,4})""")
    private val timeRx = Regex("""\d{1,2}:\d{2}(:\d{2})?""")
    private val skipMerchant = Regex("""(?i)(str\.|straße|strasse|platz|weg\b|\d{5}|tel|fax|www\.|http|willkommen|herzlich|kassenbon|beleg|bon-nr|filiale|uid|st-nr|steuer|es gibt|danke)""")

    private fun prices(row: String): List<Cents> =
        price.findAll(row.replace(dateRx, " ").replace(timeRx, " ")).mapNotNull { Money.parse(it.value) }.filter { it in 1..1_000_000 }.toList()

    fun parse(text: String, today: LocalDate = LocalDate.now()): ReceiptInfo {
        val rows = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        var total: Cents? = null
        for (i in rows.indices) {
            val r = rows[i]
            if (!totalWords.containsMatchIn(r) || notTotalWords.containsMatchIn(r)) continue
            // Betrag in derselben Zeile, sonst in der nächsten, wenn dort nur ein Betrag steht.
            val v = prices(r).lastOrNull()
                ?: rows.getOrNull(i + 1)?.takeIf { n -> n.count { it.isLetter() } <= 3 }?.let { prices(it).lastOrNull() }
            if (v != null) total = v
        }
        val paid = rows.filter { payWords.containsMatchIn(it) && !Regex("(?i)rückgeld|rueckgeld|wechselgeld").containsMatchIn(it) }
            .flatMap { prices(it) }.maxOrNull()

        val all = rows.filter { !notTotalWords.containsMatchIn(it) || payWords.containsMatchIn(it) }.flatMap { prices(it) }
        val counts = all.groupingBy { it }.eachCount()
        val sure = total != null && (paid == null || paid >= total)
        if (total == null) total = paid ?: counts.filter { it.value >= 2 }.keys.maxOrNull() ?: all.maxOrNull()
        val candidates = (listOfNotNull(total) + all.distinct().sortedWith(compareByDescending<Cents> { counts[it] ?: 0 }.thenByDescending { it }))
            .distinct().take(6)

        val merchantRow = rows.take(10).firstOrNull { l ->
            l.count { it.isLetter() } >= 3 && !skipMerchant.containsMatchIn(l) && prices(l).isEmpty()
        }.orEmpty()
        val merchant = Merchants.pretty(merchantRow)

        val date = rows.firstNotNullOfOrNull { row ->
            dateRx.findAll(row).firstNotNullOfOrNull { m ->
                val (d, mo, y) = m.destructured
                val year = if (y.length == 2) "20$y" else y
                runCatching {
                    LocalDate.parse("${d.padStart(2, '0')}.${mo.padStart(2, '0')}.$year", DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                }.getOrNull()?.takeIf { !it.isAfter(today.plusDays(1)) && it.isAfter(today.minusYears(2)) }
            }
        }

        // Bar oder Karte: zuerst die Zahlungszeile, dann der ganze Bon.
        val payRows = rows.filter { payWords.containsMatchIn(it) }.joinToString(" ")
        val joined = rows.joinToString(" ")
        val cash = when {
            cardWords.containsMatchIn(payRows) -> false
            cashWords.containsMatchIn(payRows) -> true
            else -> cashWords.containsMatchIn(joined) && !cardWords.containsMatchIn(joined)
        }

        return ReceiptInfo(total, merchant, date, cash, Merchants.guess(merchantRow + " " + joined.take(300)), sure, candidates)
    }
}
