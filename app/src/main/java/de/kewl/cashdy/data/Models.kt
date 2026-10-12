package de.kewl.cashdy.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

/** Geldbeträge sind überall Cent als Long, nie Float. */
typealias Cents = Long

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

typealias Day = @Serializable(with = LocalDateSerializer::class) LocalDate

@Serializable
enum class CategoryType { EXPENSE, INCOME }

@Serializable
data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val color: Long,
    val type: CategoryType = CategoryType.EXPENSE,
    /** Eher für regelmäßige Kosten gedacht (Miete, Abos ...). Nur für die Sortierung der Vorschläge. */
    val fixed: Boolean = false,
)

@Serializable
enum class EntryKind {
    /** Normale Ausgabe, egal ob Karte, PayPal oder bar. */
    EXPENSE,
    /** Einmalige Einnahme (Weihnachtsgeld, Flohmarkt ...). */
    INCOME,
    /** Geld vom Konto ins Portemonnaie. Keine Ausgabe (außer im Einfach-Modus). */
    CASH_WITHDRAWAL,
    /** Portemonnaie-Abgleich: Differenz zwischen App und echtem Bargeld. Positiv = verschwunden. */
    CASH_ADJUST,
    /** Anfangsbestand im Portemonnaie. Zählt nie in der Monatsbilanz. */
    CASH_START,
    /** Rückerstattung, mindert die Ausgaben. */
    REFUND,
}

@Serializable
enum class PayMethod { CARD, PAYPAL, CASH, TRANSFER }

@Serializable
enum class Source { MANUAL, NOTIFICATION, OCR, CSV }

@Serializable
data class Entry(
    val id: String,
    /** Immer positiv, außer bei CASH_ADJUST (dort mit Vorzeichen). */
    val amount: Cents,
    val date: Day,
    val kind: EntryKind,
    val categoryId: String? = null,
    val merchant: String = "",
    val note: String = "",
    val method: PayMethod = PayMethod.CARD,
    val source: Source = Source.MANUAL,
    val createdAt: Long = System.currentTimeMillis(),
    /** Pfad zum gespeicherten Kassenbon-Foto (relativ zu filesDir). */
    val receipt: String? = null,
)

@Serializable
data class PriceStep(val from: Day, val amount: Cents)

@Serializable
data class Recurring(
    val id: String,
    val name: String,
    val categoryId: String? = null,
    val income: Boolean = false,
    /** 1 = monatlich, 3 = vierteljährlich, 6 = halbjährlich, 12 = jährlich ... */
    val intervalMonths: Int = 1,
    val firstDue: Day,
    val endDate: Day? = null,
    /** Auf die Monate verteilen (Rücklage), statt im Fälligkeitsmonat voll abzuziehen. */
    val spread: Boolean = false,
    /** Preis-Historie, aufsteigend nach Datum. Mindestens ein Eintrag. */
    val prices: List<PriceStep>,
    /** Gehalt: bestimmt im Modus "ab Geldeingang" den Monatsanfang. */
    val salary: Boolean = false,
    /** Abweichender Betrag für einen einzelnen Zeitraum, Schlüssel = Startdatum des Zeitraums. */
    val overrides: Map<String, Cents> = emptyMap(),
)

@Serializable
enum class DetectedKind { EXPENSE, CASH, INCOME, REFUND }

@Serializable
enum class DetectedStatus { PENDING, ACCEPTED, IGNORED, MERGED }

@Serializable
data class Detected(
    val id: String,
    val pkg: String,
    val sourceName: String,
    val title: String,
    val text: String,
    val amount: Cents,
    val merchant: String,
    val kind: DetectedKind,
    val method: PayMethod,
    val at: Long,
    val status: DetectedStatus = DetectedStatus.PENDING,
    val entryId: String? = null,
    /** Warum zusammengeführt (z. B. "PayPal-Abbuchung zu REWE"). */
    val mergeNote: String = "",
)

@Serializable
data class RawNotification(val pkg: String, val title: String, val text: String, val at: Long)

@Serializable
enum class PeriodMode { CALENDAR, FIXED_DAY, INCOME }

@Serializable
enum class CashMode { WALLET, SIMPLE }

/** Sortierung auf "Fest": nach Kategorie zusammengefasst, nach Name oder nach nächster Abbuchung. */
@Serializable
enum class FixedView { CATEGORY, NAME, DUE }

/** Einträge auf "Monat": nach Kategorie zusammengefasst oder nach Tag. */
@Serializable
enum class MonthView { CATEGORY, DAY }

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    val name: String = "",
    val periodMode: PeriodMode = PeriodMode.CALENDAR,
    val periodDay: Int = 1,
    val shiftWeekend: Boolean = true,
    /** Bestätigte Gehaltseingänge = Monatsanfänge im Modus INCOME. */
    val periodStarts: List<Day> = emptyList(),
    val cashMode: CashMode = CashMode.WALLET,
    val remindInactiveDays: Int = 3,
    val remindDue: Boolean = true,
    val remindLow: Boolean = true,
    val lowThreshold: Cents = 10_000,
    val remindPeriodEnd: Boolean = true,
    val remindWallet: Boolean = true,
    val autoAccept: Boolean = false,
    val watchedApps: Set<String> = DEFAULT_WATCHED,
    val recorder: Boolean = false,
    val webdavUrl: String = "",
    val webdavUser: String = "",
    val webdavPass: String = "",
    val autoBackup: Boolean = false,
    val lastBackupAt: Long = 0,
    /** Merker, wann welche Erinnerung zuletzt kam (Schlüssel -> ISO-Datum oder Kennung). */
    val reminderLog: Map<String, String> = emptyMap(),
    /** Entwicklermodus (siebenmal auf die Version tippen). */
    val devMode: Boolean = false,
    /** Bei der Update-Prüfung auch den rollenden Dev-Build anbieten. */
    val devUpdates: Boolean = false,
    /** Zeiträume, deren Plus schon mit Konfetti gefeiert wurde. */
    val celebrated: Set<String> = emptySet(),
    /**
     * Ab hier wird für verteilte Kosten zurückgelegt. Davor gab es den Topf noch nicht, eine
     * Rechnung im ersten Durchlauf ist also nur teilweise angespart. Leer = schon immer.
     */
    val potStart: Day? = null,
    /** Zuletzt gewählte Ansichten, damit sie beim nächsten Öffnen wieder so sind. */
    val fixedView: FixedView = FixedView.CATEGORY,
    val monthView: MonthView = MonthView.CATEGORY,
) {
    companion object {
        val DEFAULT_WATCHED = setOf(
            "de.dkb.portalapp",
            "com.paypal.android.p2pmobile",
            "com.google.android.apps.walletnfcrel",
        )
    }
}

@Serializable
data class AppData(
    val version: Int = 1,
    val categories: List<Category> = DefaultCategories.all,
    val entries: List<Entry> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val detected: List<Detected> = emptyList(),
    val recorded: List<RawNotification> = emptyList(),
    /** Gelernte Zuordnung: normalisierter Händler -> Kategorie-ID. */
    val merchantRules: Map<String, String> = emptyMap(),
    val settings: Settings = Settings(),
)

object DefaultCategories {
    const val FOOD = "food"
    const val EATING = "eating"
    const val FUEL = "fuel"
    const val CLOTHES = "clothes"
    const val FUN = "fun"
    const val HEALTH = "health"
    const val HOUSEHOLD = "household"
    const val OTHER = "other"
    const val CASH_UNKNOWN = "cash_unknown"
    const val HOME = "home"
    const val ENERGY = "energy"
    const val SUBS = "subs"
    const val PHONE = "phone"
    const val INSURANCE = "insurance"
    const val CAR = "car"
    const val LOANS = "loans"
    const val SAVING = "saving"
    const val SALARY = "salary"
    const val INCOME_OTHER = "income_other"

    val all = listOf(
        Category(FOOD, "Lebensmittel", "🛒", 0xFFFFB37A),
        Category(EATING, "Essen gehen", "🍔", 0xFFFF8F70),
        Category(FUEL, "Tanken & Fahren", "⛽", 0xFF7DA7FF),
        Category(CLOTHES, "Kleidung", "👕", 0xFFB59CFF),
        Category(FUN, "Freizeit & Geschenke", "🎁", 0xFFFF7EB6),
        Category(HEALTH, "Gesundheit", "💊", 0xFF7ED9B5),
        Category(HOUSEHOLD, "Haushalt & Drogerie", "🧴", 0xFFFFD36B),
        Category(OTHER, "Sonstiges", "✨", 0xFFC3BED6),
        Category(CASH_UNKNOWN, "Bargeld, nicht zugeordnet", "💶", 0xFFE0C9A6),
        Category(HOME, "Wohnen", "🏠", 0xFF8E76FF, fixed = true),
        Category(ENERGY, "Strom & Heizung", "⚡", 0xFFFFC83D, fixed = true),
        Category(SUBS, "Abos & Streaming", "📺", 0xFFFF6F91, fixed = true),
        Category(PHONE, "Handy & Internet", "📱", 0xFF5FC7E8, fixed = true),
        Category(INSURANCE, "Versicherungen", "🛡️", 0xFF6C9BD2, fixed = true),
        Category(CAR, "Auto", "🚗", 0xFF9AA5B8, fixed = true),
        Category(LOANS, "Kredite & Raten", "💳", 0xFFD98BA0, fixed = true),
        Category(SAVING, "Sparen", "🐷", 0xFFFFA3C4, fixed = true),
        Category(SALARY, "Gehalt", "💰", 0xFF6C4DF6, CategoryType.INCOME),
        Category(INCOME_OTHER, "Sonstige Einnahme", "🎉", 0xFFFFB37A, CategoryType.INCOME),
    )
}
