package de.kewl.cashdy.detect

import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.data.DetectedKind
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.domain.Merchants
import de.kewl.cashdy.domain.Money

data class ParsedPayment(
    val kind: DetectedKind,
    val amount: Cents,
    val merchant: String,
    val method: PayMethod,
    /** Bank-Meldung über eine PayPal-Abbuchung: gehört zu einer PayPal-Zahlung, die es schon gibt. */
    val paypalViaBank: Boolean = false,
)

data class SourceApp(val pkg: String, val name: String, val method: PayMethod, val bank: Boolean)

/**
 * Liest Beträge, Händler und Art der Zahlung aus Benachrichtigungstexten.
 *
 * Die genauen Formulierungen der Banken ändern sich gelegentlich und sind nirgends
 * dokumentiert. Deshalb gibt es keine starren Vorlagen je App, sondern Muster, die auf
 * typische Satzteile reagieren ("bei X", "an X", "Bargeldauszahlung", "Gutschrift").
 * Mit dem Rekorder-Modus lassen sich echte Texte sammeln und hier als Testfall ergänzen.
 */
object NotificationParser {

    val knownApps = listOf(
        SourceApp("com.dkbcodefactory.banking", "DKB", PayMethod.CARD, bank = true),
        SourceApp("de.dkb.portalapp", "DKB (alte App)", PayMethod.CARD, bank = true),
        SourceApp("com.paypal.android.p2pmobile", "PayPal", PayMethod.PAYPAL, bank = false),
        SourceApp("com.google.android.apps.walletnfcrel", "Google Wallet", PayMethod.CARD, bank = false),
        SourceApp("com.starfinanz.smob.android.sfinanzstatus", "Sparkasse", PayMethod.CARD, bank = true),
        SourceApp("de.ingdiba.bankingapp", "ING", PayMethod.CARD, bank = true),
        SourceApp("de.number26.android", "N26", PayMethod.CARD, bank = true),
        SourceApp("com.revolut.revolut", "Revolut", PayMethod.CARD, bank = true),
        SourceApp("de.comdirect.android", "comdirect", PayMethod.CARD, bank = true),
        SourceApp("de.fiducia.smartphone.android.banking.vr", "VR Banking", PayMethod.CARD, bank = true),
        SourceApp("de.c24.banking", "C24", PayMethod.CARD, bank = true),
        SourceApp("de.traderepublic.app", "Trade Republic", PayMethod.CARD, bank = true),
    )

    fun app(pkg: String): SourceApp? = knownApps.firstOrNull { it.pkg == pkg }

    // Betrag mit Währung davor oder dahinter: "12,49 €", "€12.49", "1.234,56 EUR", "-12,49 €"
    private const val NUM = """[-−]?\d{1,3}(?:[.  ]\d{3})*(?:[,.]\d{2})|[-−]?\d+(?:[,.]\d{2})"""
    private val amountAfter = Regex("""($NUM)\s?(?:€|EUR|Euro)""", RegexOption.IGNORE_CASE)
    private val amountBefore = Regex("""(?:€|EUR)\s?($NUM)""", RegexOption.IGNORE_CASE)

    fun findAmount(text: String): Cents? {
        val m = amountAfter.find(text) ?: amountBefore.find(text) ?: return null
        return Money.parse(m.groupValues[1])?.let { kotlin.math.abs(it) }
    }

    fun hasAmount(text: String): Boolean = findAmount(text) != null

    private val skipWords = listOf(
        "tan", "freigabe", "freigeben", "bestätig", "bestaetig", "anmeldung", "login", "einloggen",
        "sicherheitscode", "verifizier", "angebot", "gutschein", "rabatt", "cashback-aktion", "kontostand",
        "dein limit", "abgelehnt", "nicht durchgeführt", "fehlgeschlagen", "declined",
    )
    private val cashWords = listOf("bargeld", "geldautomat", "auszahlung am", "abhebung", "atm", "bargeldauszahlung", "cash withdrawal")
    private val incomeWords = listOf("gutschrift", "zahlungseingang", "geldeingang", "eingegangen", "hast du erhalten", "haben sie erhalten", "erhalten:", "hat dir", "hat ihnen", "received", "sent you", "überweisung erhalten")
    private val refundWords = listOf("rückerstattung", "rueckerstattung", "erstattet", "erstattung", "refund", "zurückgezahlt", "gutgeschrieben (rück")

    // "bei REWE", "an Netflix", "Einkauf bei X", "Zahlung an X", "to X", "at X"
    private val merchantPatterns = listOf(
        Regex("""(?i)(?:einkauf|zahlung|kartenzahlung|bezahlt|gezahlt)\s+(?:bei|an)\s+(.+?)(?:\s+(?:autorisiert|gesendet|getätigt|bezahlt|mit|über|am|für)\b|[.,!:]\s|[.,!]$|$)"""),
        Regex("""(?i)\b(?:bei|an)\s+(.+?)(?:\s+(?:autorisiert|gesendet|getätigt|bezahlt|gezahlt|mit|über|am|für)\b|[.,!:]\s|[.,!]$|$)"""),
        Regex("""(?i)\b(?:to|at)\s+(.+?)(?:\s+(?:with|using|on|for)\b|[.,!]\s|[.,!]$|$)"""),
        Regex("""(?i)\bvon\s+(.+?)(?:\s+(?:erhalten|bekommen|gutgeschrieben)\b|[.,!:]\s|[.,!]$|$)"""),
    )

    fun parse(pkg: String, title: String, text: String): ParsedPayment? {
        val full = "$title\n$text".trim()
        val lower = full.lowercase()
        val amount = findAmount(full) ?: return null
        if (amount == 0L) return null
        if (skipWords.any { w -> Regex("""(?<![\p{L}])${Regex.escape(w)}""").containsMatchIn(lower) } &&
            !lower.contains("zahlung") && !lower.contains("einkauf")) return null

        val app = app(pkg)
        val isBank = app?.bank ?: true
        val kind = when {
            refundWords.any { lower.contains(it) } -> DetectedKind.REFUND
            cashWords.any { lower.contains(it) } -> DetectedKind.CASH
            incomeWords.any { lower.contains(it) } -> DetectedKind.INCOME
            else -> DetectedKind.EXPENSE
        }
        var merchant = extractMerchant(title, text, amount)
        val paypalViaBank = isBank && pkg != "com.paypal.android.p2pmobile" &&
            (lower.contains("paypal") && kind == DetectedKind.EXPENSE)
        if (paypalViaBank && merchant.isBlank()) merchant = "PayPal"

        val method = when {
            kind == DetectedKind.CASH -> PayMethod.CASH
            pkg == "com.paypal.android.p2pmobile" -> PayMethod.PAYPAL
            lower.contains("lastschrift") || lower.contains("überweisung") || lower.contains("dauerauftrag") -> PayMethod.TRANSFER
            else -> app?.method ?: PayMethod.CARD
        }
        return ParsedPayment(kind, amount, merchant, method, paypalViaBank)
    }

    fun extractMerchant(title: String, text: String, amount: Cents): String {
        // Erst der Text, dann der Titel ("Einkauf bei Zalando" / "Du hast 59,95 € bezahlt").
        for (source in listOf(text, title)) {
            val body = source.replace('\n', ' ').trim()
            for (p in merchantPatterns) {
                val m = p.find(body) ?: continue
                val cand = clean(m.groupValues[1])
                if (isPlausibleMerchant(cand)) return Merchants.pretty(cand)
            }
        }
        // Manche Apps schreiben den Händler in den Titel ("REWE" / "12,49 € bezahlt").
        val t = clean(title)
        if (isPlausibleMerchant(t) && !t.contains(Regex("""\d+[,.]\d{2}"""))) return Merchants.pretty(t)
        return ""
    }

    private fun clean(s: String): String = s
        .replace(amountAfter, "").replace(amountBefore, "")
        .replace(Regex("""["„“”'»«]"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim().trimEnd('.', ',', '!', ':', ';', '-')
        .trim()

    private val notMerchant = setOf(
        "dkb", "paypal", "google wallet", "google pay", "sparkasse", "ing", "n26", "revolut",
        "neue zahlung", "zahlung", "kartenzahlung", "umsatz", "neuer umsatz", "deine karte", "ihre karte",
        "visa", "mastercard", "debitkarte", "kreditkarte", "dein konto", "ihr konto", "du", "sie", "dich",
    )

    private fun isPlausibleMerchant(s: String): Boolean {
        if (s.length < 2 || s.length > 50) return false
        val l = s.lowercase()
        if (l in notMerchant) return false
        if (!s.any { it.isLetter() }) return false
        if (l.startsWith("deine ") || l.startsWith("ihre ") || l.startsWith("dein ") || l.startsWith("ihr ")) return false
        return true
    }
}
