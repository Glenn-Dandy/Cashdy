package de.kewl.cashdy.domain

import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.DefaultCategories as C

object Merchants {

    private val legal = Regex("""\b(gmbh|co|kg|ag|se|ug|ohg|e\.?k|mbh|s\.?a\.?r\.?l|sarl|ltd|inc|bv|b\.v|europe|deutschland|germany|filiale|markt)\b\.?""")

    /** "REWE Markt GmbH Fil. 4711 Berlin" -> "rewe" (für Regeln und Gruppierung). */
    fun normalize(raw: String): String {
        var s = raw.lowercase()
            .replace(Regex("""\b(sagt danke|vielen dank|danke)\b"""), " ")
            .replace(Regex("""[*/\\_|]"""), " ")
            .replace(Regex("""\d+"""), " ")
            .replace(legal, " ")
            .replace(Regex("""[^\p{L}& ]"""), " ")
            .replace(Regex("""\s+"""), " ").trim()
        // Höchstens zwei Wörter: "rewe berlin mitte" und "rewe hamburg" landen beide bei "rewe ...".
        val words = s.split(' ').filter { it.length > 1 || it == "&" }
        s = words.take(2).joinToString(" ")
        return s
    }

    /** Hübscher Anzeigename: "REWE MARKT GMBH" -> "Rewe Markt". */
    fun pretty(raw: String): String {
        val cleaned = raw.trim().replace(Regex("""\s+"""), " ")
            .replace(Regex("""(?i)\b(gmbh|ag|se|kg|mbh|s\.?a\.?r\.?l\.?|ltd|inc)\b\.?"""), "")
            .trim().trimEnd(',', '.', '-')
        if (cleaned.isEmpty()) return raw.trim()
        val allCaps = cleaned.count { it.isUpperCase() } > cleaned.count { it.isLowerCase() }
        if (!allCaps) return cleaned.take(40)
        // Kurze Markennamen bleiben groß (REWE, EDEKA, ALDI, ARAL), lange Wörter werden normal
        // geschrieben ("NETFLIX INTERNATIONAL" -> "Netflix International").
        return cleaned.split(' ').joinToString(" ") { w ->
            if (w.count { it.isLetter() } <= 5) w else w.lowercase().replaceFirstChar { it.uppercase() }
        }.take(40)
    }

    private val keywords: List<Pair<List<String>, String>> = listOf(
        // Fast Food, Snacks, Coffee to go und Lieferdienste. Steht vor den Lebensmitteln, damit
        // „Kamps Bäckerei“ oder „Backwerk“ hier landen und nicht beim Wocheneinkauf.
        listOf(
            "mcdonald", "mc donald", "burger king", "burgerking", "kfc", "subway", "five guys", "domino", "pizza hut", "nordsee",
            "starbucks", "coffee fellows", "dunkin", "tim hortons", "backwerk", "back-werk", "back factory", "backfactory", "kamps",
            "ditsch", "le crobag", "yormas", "dean & david", "dean&david", "lieferando", "wolt", "uber eats", "ubereats", "just eat",
            "foodora", "döner", "doener", "kebab", "imbiss", "pommes", "snack", "burger",
        ) to C.TAKEAWAY,
        listOf("rewe", "edeka", "aldi", "lidl", "netto", "penny", "kaufland", "norma", "globus", "tegut", "real", "hit ", "nahkauf", "bäckerei", "baeckerei", "backhaus", "metzgerei", "flink", "picnic", "getir") to C.FOOD,
        listOf("pizza", "pizzeria", "restaurant", "ristorante", "trattoria", "gasthaus", "gaststätte", "wirtshaus", "brauhaus", "cafe", "café", "bistro", "sushi", "bar ") to C.EATING,
        listOf("aral", "shell", "esso", "jet", "total", "avia", "agip", "tankstelle", "db vertrieb", "deutsche bahn", "bvg", "mvg", "hvv", "flixbus", "uber", "bolt", "tier", "parken", "parkhaus", "easypark") to C.FUEL,
        listOf("h&m", "zara", "zalando", "primark", "c&a", "about you", "deichmann", "snipes", "tk maxx", "only", "jack & jones") to C.CLOTHES,
        listOf("kino", "cinema", "steam", "playstation", "nintendo", "xbox", "thalia", "hugendubel", "eventim", "ticket", "spielwaren", "lego", "museum", "zoo", "fitness", "mcfit", "urban sports") to C.FUN,
        listOf("apotheke", "arzt", "zahnarzt", "praxis", "optik", "fielmann", "physio", "shop apotheke", "docmorris") to C.HEALTH,
        listOf("dm ", "dm-", "dm drogerie", "rossmann", "müller", "mueller", "ikea", "obi", "hornbach", "bauhaus", "toom", "tedi", "action", "kik", "woolworth", "depot") to C.HOUSEHOLD,
        listOf("miete", "wohnungs", "hausverwaltung", "vonovia", "nebenkosten", "wohnbau") to C.HOME,
        listOf("stadtwerke", "strom", "e.on", "eon", "vattenfall", "enbw", "rwe", "gas", "tibber", "octopus", "ostrom", "fernwärme") to C.ENERGY,
        listOf("netflix", "spotify", "disney", "amazon prime", "prime video", "youtube", "apple.com", "apple music", "dazn", "sky ", "wow ", "audible", "rundfunk", "ard zdf", "beitragsservice", "chatgpt", "openai", "anthropic", "claude", "icloud", "google one") to C.SUBS,
        listOf("telekom", "vodafone", "o2", "telefonica", "1&1", "congstar", "aldi talk", "fraenk", "freenet", "mobilcom", "kabel", "glasfaser") to C.PHONE,
        listOf("versicherung", "huk", "allianz", "axa", "ergo", "debeka", "devk", "signal iduna", "generali", "zurich", "haftpflicht", "check24 versicherung", "barmenia") to C.INSURANCE,
        listOf("kfz", "autohaus", "werkstatt", "atu", "tüv", "tuev", "dekra", "adac") to C.CAR,
        listOf("kredit", "darlehen", "rate ", "finanzierung", "klarna", "ratepay", "santander", "targobank") to C.LOANS,
        listOf("sparplan", "tagesgeld", "trade republic", "scalable", "depot", "sparkonto") to C.SAVING,
        listOf("gehalt", "lohn", "bezüge", "bezuege", "entgelt", "salary") to C.SALARY,
    )

    /** Kategorie-Vorschlag: zuerst gelernte Regel, dann Stichwortliste. */
    fun suggestCategory(d: AppData?, merchant: String): String? {
        if (merchant.isBlank()) return null
        val key = normalize(merchant)
        d?.merchantRules?.get(key)?.let { return it }
        return guess(merchant)
    }

    fun guess(text: String): String? {
        val t = " " + text.lowercase() + " "
        for ((words, cat) in keywords) if (words.any { t.contains(it) }) return cat
        return null
    }

    fun learn(d: AppData, merchant: String, categoryId: String?): AppData {
        if (merchant.isBlank() || categoryId == null) return d
        val key = normalize(merchant)
        if (key.isBlank() || d.merchantRules[key] == categoryId) return d
        return d.copy(merchantRules = d.merchantRules + (key to categoryId))
    }

    /** Ähnlich genug für "dieselbe Firma"? */
    fun similar(a: String, b: String): Boolean {
        val na = normalize(a); val nb = normalize(b)
        if (na.isBlank() || nb.isBlank()) return false
        if (na == nb) return true
        val fa = na.substringBefore(' '); val fb = nb.substringBefore(' ')
        return fa.length >= 3 && fa == fb || na.contains(nb) || nb.contains(na)
    }
}
