package de.kewl.cashdy.detect

import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.DefaultCategories
import de.kewl.cashdy.data.Detected
import de.kewl.cashdy.data.DetectedKind
import de.kewl.cashdy.data.DetectedStatus
import de.kewl.cashdy.data.Entry
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.data.RawNotification
import de.kewl.cashdy.data.Source
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.domain.Merchants
import de.kewl.cashdy.domain.Recurrence
import de.kewl.cashdy.domain.Periods
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.data.category
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

sealed interface DetectionResult {
    /** Nichts zu tun (keine Zahlung, Duplikat, App nicht freigegeben). */
    data object Nothing : DetectionResult
    /** Neue Zahlung, wartet auf Bestätigung. */
    data class Pending(val detected: Detected, val suggestedCategory: String?) : DetectionResult
    /** Automatisch übernommen, weil der Händler bekannt ist. */
    data class AutoAccepted(val detected: Detected, val entry: Entry) : DetectionResult
    /** Still zusammengeführt (Doppelmeldung, PayPal-Abbuchung, Fixkosten). */
    data class Merged(val detected: Detected) : DetectionResult
}

object Detection {

    private const val DUP_WINDOW_MS = 10 * 60 * 1000L
    private const val PAYPAL_WINDOW_DAYS = 10L

    /**
     * Verarbeitet eine eingehende Benachrichtigung. Reine Funktion auf [AppData], damit
     * sie ohne Android testbar ist; der Service speichert nur das Ergebnis.
     */
    fun handle(d: AppData, pkg: String, title: String, text: String, now: Long): Pair<AppData, DetectionResult> {
        var data = d
        val s = d.settings
        val parsed = NotificationParser.parse(pkg, title, text)

        // Rekorder: alles mit Betrag mitschneiden, auch aus nicht freigegebenen Apps.
        if (s.recorder && NotificationParser.hasAmount("$title $text") && pkg != "de.kewl.cashdy") {
            data = data.copy(recorded = (listOf(RawNotification(pkg, title, text, now)) + data.recorded).take(200))
        }
        if (pkg !in s.watchedApps || parsed == null) return data to DetectionResult.Nothing

        // Dieselbe Meldung nochmal (Apps aktualisieren Benachrichtigungen gern).
        if (data.detected.any { it.pkg == pkg && it.amount == parsed.amount && it.text == text && now - it.at < DUP_WINDOW_MS }) {
            return data to DetectionResult.Nothing
        }

        val app = NotificationParser.app(pkg)
        var det = Detected(
            id = Store.newId(), pkg = pkg, sourceName = app?.name ?: pkg.substringAfterLast('.'),
            title = title, text = text, amount = parsed.amount, merchant = parsed.merchant,
            kind = parsed.kind, method = parsed.method, at = now,
        )
        val day = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()

        // 1) Gleicher Betrag aus einer anderen App innerhalb von 10 Minuten (z. B. Google Wallet + DKB).
        val twin = data.detected.firstOrNull {
            it.pkg != pkg && it.amount == parsed.amount && it.kind == parsed.kind && abs(now - it.at) < DUP_WINDOW_MS &&
                it.status != DetectedStatus.IGNORED
        }
        if (twin != null) {
            // Der bessere Händlername gewinnt (PayPal/Wallet kennen ihn, die Bank oft nicht).
            if (twin.merchant.isBlank() && det.merchant.isNotBlank()) {
                data = data.copy(detected = data.detected.map { if (it.id == twin.id) it.copy(merchant = det.merchant) else it })
            }
            det = det.copy(status = DetectedStatus.MERGED, mergeNote = "Doppelt gemeldet, gleich wie ${twin.sourceName}")
            return data.copy(detected = listOf(det) + data.detected) to DetectionResult.Merged(det)
        }

        // 2) Bank meldet eine PayPal-Abbuchung, die PayPal selbst schon gemeldet hat.
        if (parsed.paypalViaBank) {
            val pp = data.entries.firstOrNull {
                it.method == PayMethod.PAYPAL && it.amount == parsed.amount && it.kind == EntryKind.EXPENSE &&
                    !it.date.isAfter(day) && !it.date.isBefore(day.minusDays(PAYPAL_WINDOW_DAYS))
            }
            val ppDetected = data.detected.firstOrNull {
                it.pkg == "com.paypal.android.p2pmobile" && it.amount == parsed.amount &&
                    now - it.at in 0..PAYPAL_WINDOW_DAYS * 86_400_000L
            }
            if (pp != null || ppDetected != null) {
                val name = pp?.merchant?.ifBlank { null } ?: ppDetected?.merchant?.ifBlank { null } ?: "PayPal"
                det = det.copy(status = DetectedStatus.MERGED, mergeNote = "PayPal-Abbuchung, gehört zu $name")
                return data.copy(detected = listOf(det) + data.detected) to DetectionResult.Merged(det)
            }
        }

        // 3) Abbuchung einer bekannten Fixkosten-Position (Netflix-Lastschrift o. ä.): die zählt schon.
        if (parsed.kind == DetectedKind.EXPENSE || parsed.kind == DetectedKind.INCOME) {
            val wantIncome = parsed.kind == DetectedKind.INCOME
            val rec = data.recurring.firstOrNull { r ->
                r.income == wantIncome && det.merchant.isNotBlank() &&
                    (Merchants.similar(r.name, det.merchant)) &&
                    closeEnough(Recurrence.priceAt(r, day), parsed.amount)
            }
            // Im Modus "ab Geldeingang" muss das Gehalt sichtbar bleiben: es startet den neuen Monat.
            val keepForPeriod = rec != null && rec.salary && s.periodMode == de.kewl.cashdy.data.PeriodMode.INCOME
            if (rec != null && !keepForPeriod) {
                det = det.copy(status = DetectedStatus.MERGED, mergeNote = "Gehört zu „${rec.name}“ (regelmäßig)")
                return data.copy(detected = listOf(det) + data.detected) to DetectionResult.Merged(det)
            }
        }

        val suggested = when (parsed.kind) {
            DetectedKind.EXPENSE, DetectedKind.REFUND -> Merchants.suggestCategory(data, det.merchant)
            DetectedKind.INCOME -> Merchants.suggestCategory(data, det.merchant)?.takeIf { id ->
                data.categories.any { it.id == id && it.type == de.kewl.cashdy.data.CategoryType.INCOME }
            } ?: DefaultCategories.INCOME_OTHER
            DetectedKind.CASH -> null
        }

        // 4) Automatisch übernehmen, wenn eingeschaltet und Händler gelernt. Nicht, wenn es doppelt sein könnte.
        if (s.autoAccept && parsed.kind == DetectedKind.EXPENSE && det.merchant.isNotBlank() &&
            maybeDuplicate(data, det) == null &&
            data.merchantRules.containsKey(Merchants.normalize(det.merchant))
        ) {
            val (d2, entry) = accept(data.copy(detected = listOf(det) + data.detected), det.id, suggested, asCashWithdrawal = false)
            return d2 to DetectionResult.AutoAccepted(det.copy(status = DetectedStatus.ACCEPTED, entryId = entry?.id), entry!!)
        }

        return data.copy(detected = listOf(det) + data.detected) to DetectionResult.Pending(det, suggested)
    }

    /**
     * Gibt es genau diesen Betrag im selben Monat schon? Eingetragen, als Fixkosten fällig oder
     * als andere erkannte Zahlung, die noch wartet. Dann ein Hinweis, dass sie doppelt sein
     * könnte, zum Beispiel weil die Bank erst „vorgemerkt“ und später „gebucht“ meldet.
     */
    fun maybeDuplicate(d: AppData, det: Detected): String? {
        if (det.kind != DetectedKind.EXPENSE && det.kind != DetectedKind.INCOME) return null
        val day = dayOf(det)
        val period = Periods.periodFor(day, d.settings)
        val income = det.kind == DetectedKind.INCOME
        val amount = Money.format(det.amount)
        val entry = d.entries.firstOrNull {
            it.id != det.entryId && it.amount == det.amount && it.date in period &&
                it.kind == (if (income) EntryKind.INCOME else EntryKind.EXPENSE)
        }
        if (entry != null) {
            val what = entry.merchant.ifBlank { d.category(entry.categoryId)?.name ?: "" }
            return "Vielleicht doppelt: $amount hast du am ${short(entry.date)} schon eingetragen" + (if (what.isNotBlank()) " ($what)." else ".")
        }
        val rec = d.recurring.firstOrNull { r ->
            r.income == income && Recurrence.dueDates(r, period.start, period.end).any { Recurrence.priceAt(r, it) == det.amount }
        }
        if (rec != null) return "Vielleicht doppelt: $amount steht schon bei „Fest“ als „${rec.name}“."
        val other = d.detected.firstOrNull {
            it.id != det.id && it.status == DetectedStatus.PENDING && it.amount == det.amount && it.kind == det.kind && dayOf(it) in period
        }
        if (other != null) return "Vielleicht doppelt: $amount" + (if (other.merchant.isNotBlank()) " von ${other.merchant}" else "") + " wartet hier schon."
        return null
    }

    private fun short(d: LocalDate) = "${d.dayOfMonth}.${d.monthValue}."

    private fun closeEnough(expected: Long, actual: Long): Boolean =
        expected > 0 && abs(expected - actual) * 100 <= expected * 20

    /**
     * Übernimmt eine erkannte Zahlung als Eintrag.
     * [asCashWithdrawal]: bei Bargeld true = ins Portemonnaie, false = als Ausgabe.
     */
    fun accept(d: AppData, detectedId: String, categoryId: String?, asCashWithdrawal: Boolean = true): Pair<AppData, Entry?> {
        val det = d.detected.firstOrNull { it.id == detectedId } ?: return d to null
        if (det.status == DetectedStatus.ACCEPTED) return d to d.entries.firstOrNull { it.id == det.entryId }
        val day = Instant.ofEpochMilli(det.at).atZone(ZoneId.systemDefault()).toLocalDate()
        val kind = when (det.kind) {
            DetectedKind.EXPENSE -> EntryKind.EXPENSE
            DetectedKind.INCOME -> EntryKind.INCOME
            DetectedKind.REFUND -> EntryKind.REFUND
            DetectedKind.CASH -> if (asCashWithdrawal) EntryKind.CASH_WITHDRAWAL else EntryKind.EXPENSE
        }
        val entry = Entry(
            id = Store.newId(), amount = det.amount, date = day, kind = kind,
            categoryId = when (kind) {
                EntryKind.CASH_WITHDRAWAL -> null
                EntryKind.EXPENSE -> categoryId ?: if (det.kind == DetectedKind.CASH) DefaultCategories.CASH_UNKNOWN else DefaultCategories.OTHER
                else -> categoryId
            },
            merchant = det.merchant,
            method = if (det.kind == DetectedKind.CASH) PayMethod.CARD else det.method,
            source = Source.NOTIFICATION,
        )
        var out = d.copy(
            entries = d.entries + entry,
            detected = d.detected.map { if (it.id == detectedId) it.copy(status = DetectedStatus.ACCEPTED, entryId = entry.id) else it },
        )
        if (kind == EntryKind.EXPENSE || kind == EntryKind.REFUND) out = Merchants.learn(out, det.merchant, entry.categoryId)
        return out to entry
    }

    fun ignore(d: AppData, detectedId: String): AppData =
        d.copy(detected = d.detected.map { if (it.id == detectedId) it.copy(status = DetectedStatus.IGNORED) else it })

    fun pending(d: AppData): List<Detected> = d.detected.filter { it.status == DetectedStatus.PENDING }.sortedByDescending { it.at }

    /** Alte erledigte Meldungen aufräumen, damit die Datei nicht endlos wächst. */
    fun prune(d: AppData, now: Long): AppData {
        val cutoff = now - 60L * 86_400_000L
        val keep = d.detected.filter { it.status == DetectedStatus.PENDING || it.at >= cutoff }
        return if (keep.size == d.detected.size) d else d.copy(detected = keep)
    }

    fun dayOf(det: Detected): LocalDate = Instant.ofEpochMilli(det.at).atZone(ZoneId.systemDefault()).toLocalDate()
}
