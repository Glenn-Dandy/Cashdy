package de.kewl.cashdy.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.kewl.cashdy.data.CashMode
import de.kewl.cashdy.data.DefaultCategories
import de.kewl.cashdy.data.Entry
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.data.PeriodMode
import de.kewl.cashdy.data.PriceStep
import de.kewl.cashdy.data.Recurring
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.Periods
import de.kewl.cashdy.notify.Notify
import de.kewl.cashdy.ui.components.BigButton
import de.kewl.cashdy.ui.components.Card
import de.kewl.cashdy.ui.components.DateChip
import de.kewl.cashdy.ui.components.DatePick
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.Mascot
import de.kewl.cashdy.ui.components.MoneyField
import de.kewl.cashdy.ui.components.Mood
import de.kewl.cashdy.ui.components.Pill
import de.kewl.cashdy.ui.components.SoftField
import de.kewl.cashdy.ui.components.SpeechBubble
import de.kewl.cashdy.ui.components.TextButtonSoft
import de.kewl.cashdy.ui.theme.C
import de.kewl.cashdy.widget.CashdyWidget
import java.time.LocalDate

private data class QuickFixed(val name: String, val cat: String, val months: Int, val amount: Long, val due: LocalDate)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val c = C.colors
    var step by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var salary by remember { mutableStateOf("") }
    var salaryDay by remember { mutableStateOf(27) }
    var mode by remember { mutableStateOf(PeriodMode.CALENDAR) }
    val fixed = remember { mutableStateListOf<QuickFixed>() }
    var cashMode by remember { mutableStateOf(CashMode.WALLET) }
    var wallet by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var minus by remember { mutableStateOf(false) }
    var askFixed by remember { mutableStateOf<Triple<String, String, Int>?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val listenerOn = rememberListenerEnabled()
    val steps = 6

    fun finish() {
        val today = LocalDate.now()
        Store.update { d ->
            val rec = mutableListOf<Recurring>()
            val sal = Money.parse(salary)?.takeIf { it > 0 }
            if (sal != null) {
                val thisMonth = Periods.startInMonth(today, salaryDay, false)
                rec += Recurring(
                    id = Store.newId(), name = "Gehalt", categoryId = DefaultCategories.SALARY, income = true,
                    intervalMonths = 1, firstDue = thisMonth.minusMonths(1), prices = listOf(PriceStep(thisMonth.minusMonths(1), sal)), salary = true,
                )
            }
            fixed.forEach { f ->
                rec += Recurring(
                    id = Store.newId(), name = f.name, categoryId = f.cat, intervalMonths = f.months,
                    firstDue = f.due, spread = f.months > 1, prices = listOf(PriceStep(f.due, f.amount)),
                )
            }
            val lastSalaryDay = Periods.startInMonth(today, salaryDay, false).let { if (it.isAfter(today)) Periods.startInMonth(today.minusMonths(1), salaryDay, false) else it }
            val entries = Money.parse(wallet)?.takeIf { it > 0 && cashMode == CashMode.WALLET }?.let {
                listOf(Entry(Store.newId(), it, today, EntryKind.CASH_START, method = PayMethod.CASH, note = "Startbestand"))
            } ?: emptyList()
            val start = Money.parse(balance)?.let { if (minus) -it else it }
            val settings = d.settings.copy(
                onboarded = true, name = name.trim(), periodMode = mode, periodDay = if (mode == PeriodMode.CALENDAR) 1 else salaryDay,
                periodStarts = if (mode == PeriodMode.INCOME) listOf(lastSalaryDay) else emptyList(), cashMode = cashMode,
                startDay = start?.let { today }, startBalance = start,
            )
            // Mit Kontostand fängt die Rücklage erst im ersten vollen Monat an.
            val potStart = if (start == null) today else Periods.next(Periods.periodFor(today, settings), settings).start
            d.copy(
                recurring = d.recurring + rec,
                entries = d.entries + entries,
                settings = settings.copy(potStart = potStart),
            )
        }
        CashdyWidget.updateAll(ctx)
        onDone()
    }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(steps) { i ->
                Box(Modifier.padding(horizontal = 4.dp).size(if (i == step) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (i <= step) c.primary else c.line))
            }
        }
        AnimatedContent(step, Modifier.weight(1f), label = "step") { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (s) {
                    0 -> {
                        Spacer(Modifier.height(20.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Mascot(Mood.PARTY, size = 150.dp) }
                        Text("Hallo! Ich bin Cashdy 👋", style = MaterialTheme.typography.headlineLarge, color = c.fg, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text(
                            "Ich zeig dir jeden Tag, wie viel Geld du noch frei hast. Ohne Bank-Login, ohne Schnickschnack. In einer Minute sind wir startklar.",
                            style = MaterialTheme.typography.bodyLarge, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                        SoftField(name, { name = it }, "Wie heißt du? (optional)")
                    }
                    1 -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Mascot(Mood.HAPPY, size = 70.dp)
                            Spacer(Modifier.width(8.dp))
                            SpeechBubble("Was kommt jeden Monat rein?", Modifier.weight(1f))
                        }
                        MoneyField(salary, { salary = it }, "Gehalt (netto) in €")
                        Card {
                            Text("Kommt ungefähr am $salaryDay.", style = MaterialTheme.typography.titleSmall, color = c.fg)
                            Slider(salaryDay.toFloat(), { salaryDay = it.toInt() }, valueRange = 1f..31f,
                                colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary))
                        }
                        Text("Wann beginnt für dich ein neuer Monat?", style = MaterialTheme.typography.titleMedium, color = c.fg)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Am 1.", mode == PeriodMode.CALENDAR, { mode = PeriodMode.CALENDAR })
                            Pill("Immer am $salaryDay.", mode == PeriodMode.FIXED_DAY, { mode = PeriodMode.FIXED_DAY })
                            Pill("Wenn Gehalt kommt", mode == PeriodMode.INCOME, { mode = PeriodMode.INCOME })
                        }
                        Hint(
                            when (mode) {
                                PeriodMode.CALENDAR -> "Ganz klassisch: 1. bis Monatsende."
                                PeriodMode.FIXED_DAY -> "Dein Monat läuft vom $salaryDay. bis zum Tag davor. Fällt der Tag aufs Wochenende, nehme ich den Freitag davor."
                                PeriodMode.INCOME -> "Dein neuer Monat startet, sobald das Gehalt da ist. Ich frag kurz nach oder erkenne es an der Bank-Meldung."
                            }
                        )
                        Hint("Kein festes Gehalt? Einfach leer lassen, du kannst Einnahmen auch einzeln eintragen.")
                    }
                    2 -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Mascot(Mood.HAPPY, size = 70.dp)
                            Spacer(Modifier.width(8.dp))
                            SpeechBubble("Wie viel ist gerade auf deinem Konto?", Modifier.weight(1f))
                        }
                        MoneyField(balance, { balance = it }, "Kontostand heute in €")
                        Pill(if (minus) "✓ Konto ist im Minus" else "Konto ist im Minus", minus, { minus = !minus })
                        Hint(
                            "Damit dein erster Monat gleich stimmt, rechne ich bis " +
                                when (mode) { PeriodMode.CALENDAR -> "zum Monatsende"; PeriodMode.FIXED_DAY -> "zum nächsten $salaryDay."; PeriodMode.INCOME -> "zum nächsten Gehalt" } +
                                " mit diesem Betrag. " +
                                "Was bis heute abgebucht wurde oder reinkam, steckt schon darin. Was danach noch kommt, Gehalt oder Fixkosten, " +
                                "rechne ich dazu oder ab. Darum ist bei den Fixkosten der Abbuchungstag wichtig."
                        )
                        Hint("Ab dem nächsten Monat rechne ich ganz normal mit deinem Gehalt. Leer lassen geht auch, dann zählt schon jetzt das ganze Gehalt.")
                    }
                    3 -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Mascot(Mood.OK, size = 70.dp)
                            Spacer(Modifier.width(8.dp))
                            SpeechBubble("Was geht jeden Monat fest weg? Tipp an, was du hast.", Modifier.weight(1f))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            quick.forEach { (n, cat, m) ->
                                val has = fixed.any { it.name == n }
                                Pill((if (has) "✓ " else "") + n, has, {
                                    if (has) fixed.removeAll { it.name == n } else askFixed = Triple(n, cat, m)
                                })
                            }
                            Pill("＋ Eigene", false, { askFixed = Triple("", DefaultCategories.OTHER, 1) })
                        }
                        fixed.forEach { f ->
                            Row(Modifier.padding(horizontal = 4.dp)) {
                                Text(f.name, style = MaterialTheme.typography.bodyLarge, color = c.fg, modifier = Modifier.weight(1f))
                                Text(Money.format(f.amount) + if (f.months > 1) " / ${f.months} Mon." else "", style = MaterialTheme.typography.bodyLarge, color = c.muted)
                            }
                        }
                        Hint("Später kannst du unter „Fest“ einen Kontoauszug (CSV) einlesen, dann finde ich alles Regelmäßige von selbst.")
                    }
                    4 -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Mascot(Mood.HAPPY, size = 70.dp)
                            Spacer(Modifier.width(8.dp))
                            SpeechBubble("Und Bargeld? 💶", Modifier.weight(1f))
                        }
                        Card(color = if (cashMode == CashMode.WALLET) c.soft2 else c.surface, onClick = { cashMode = CashMode.WALLET }) {
                            Text("👛 Portemonnaie führen", style = MaterialTheme.typography.titleMedium, color = c.fg)
                            Hint("Abheben füllt dein Portemonnaie, Barzahlungen leeren es. Ab und zu fragst du mich nach einem Abgleich. Genau, aber trotzdem locker.")
                        }
                        Card(color = if (cashMode == CashMode.SIMPLE) c.soft2 else c.surface, onClick = { cashMode = CashMode.SIMPLE }) {
                            Text("🤷 Einfach-Modus", style = MaterialTheme.typography.titleMedium, color = c.fg)
                            Hint("Jede Abhebung zählt sofort als ausgegeben. Um Barzahlungen musst du dich nicht kümmern.")
                        }
                        if (cashMode == CashMode.WALLET) MoneyField(wallet, { wallet = it }, "Wie viel Bargeld hast du gerade? (€)")
                    }
                    5 -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Mascot(Mood.PARTY, size = 110.dp) }
                        Text("Fast fertig! ✨", style = MaterialTheme.typography.headlineMedium, color = c.fg)
                        Card {
                            Text("🔔 Erinnerungen", style = MaterialTheme.typography.titleMedium, color = c.fg)
                            Hint("Ich sag Bescheid, wenn etwas fällig wird oder du lange nichts eingetragen hast. Versprochen: nicht nervig.")
                            TextButtonSoft(if (Notify.canPost(ctx)) "✓ Erlaubt" else "Erlauben", {
                                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            })
                        }
                        Card {
                            Text("✨ Zahlungen automatisch erkennen", style = MaterialTheme.typography.titleMedium, color = c.fg)
                            Hint("Wenn PayPal, DKB oder Google Wallet eine Zahlung melden, schlage ich sie dir vor. Ein Tipp, fertig. Ich lese nur diese Apps, nichts verlässt dein Handy.")
                            TextButtonSoft(if (listenerOn) "✓ Aktiv" else "Zugriff erlauben", { openListenerSettings(ctx) })
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButtonSoft("Zurück", { step-- })
            Spacer(Modifier.weight(1f))
            BigButton(
                when (step) { 0 -> "Los geht's"; steps - 1 -> "Fertig 🎉"; else -> "Weiter" },
                { if (step < steps - 1) step++ else finish() },
            )
        }
    }

    askFixed?.let { (n, cat, m) ->
        var amount by remember(n) { mutableStateOf(if (n == "Rundfunkbeitrag") "55,08" else "") }
        var title by remember(n) { mutableStateOf(n) }
        var due by remember(n) { mutableStateOf(LocalDate.now().let { if (m == 1) it.withDayOfMonth(1) else it.plusMonths(1).withDayOfMonth(1) }) }
        var pickDue by remember(n) { mutableStateOf(false) }
        if (pickDue) DatePick(due, { pickDue = false }) { due = it }
        AlertDialog(
            onDismissRequest = { askFixed = null },
            title = { Text(if (n.isBlank()) "Eigene Fixkosten" else n) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (n.isBlank()) SoftField(title, { title = it }, "Name")
                    MoneyField(amount, { amount = it }, if (m == 1) "Betrag pro Monat (€)" else if (m == 12) "Betrag pro Jahr (€)" else "Betrag alle $m Monate (€)")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (m == 1) "Abbuchung am" else "Nächste Abbuchung", style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
                        DateChip(due, { pickDue = true })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val a = Money.parse(amount)
                    if (a != null && a > 0 && title.isNotBlank()) { fixed += QuickFixed(title.trim(), cat, m, a, due); askFixed = null }
                }) { Text("Hinzufügen") }
            },
            dismissButton = { TextButton(onClick = { askFixed = null }) { Text("Abbrechen") } },
        )
    }
}

private val quick = listOf(
    Triple("Miete", DefaultCategories.HOME, 1),
    Triple("Strom", DefaultCategories.ENERGY, 1),
    Triple("Handy", DefaultCategories.PHONE, 1),
    Triple("Internet", DefaultCategories.PHONE, 1),
    Triple("Netflix", DefaultCategories.SUBS, 1),
    Triple("Spotify", DefaultCategories.SUBS, 1),
    Triple("Rundfunkbeitrag", DefaultCategories.SUBS, 3),
    Triple("Kfz-Versicherung", DefaultCategories.INSURANCE, 12),
    Triple("Fitnessstudio", DefaultCategories.FUN, 1),
    Triple("Kredit", DefaultCategories.LOANS, 1),
)
