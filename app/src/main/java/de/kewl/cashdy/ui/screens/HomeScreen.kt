package de.kewl.cashdy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.CashMode
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.data.category
import de.kewl.cashdy.detect.Detection
import de.kewl.cashdy.detect.PaymentListenerService
import de.kewl.cashdy.domain.Budget
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.Periods
import de.kewl.cashdy.domain.Recurrence
import de.kewl.cashdy.domain.Summary
import de.kewl.cashdy.ui.AddRequest
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.Route
import de.kewl.cashdy.ui.components.CoinBar
import de.kewl.cashdy.ui.components.EntryGroup
import de.kewl.cashdy.ui.components.Group
import de.kewl.cashdy.ui.components.GroupDivider
import de.kewl.cashdy.ui.components.MenuRow
import de.kewl.cashdy.ui.components.ValueRow
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.Mascot
import de.kewl.cashdy.ui.components.Mood
import de.kewl.cashdy.ui.components.SectionTitle
import de.kewl.cashdy.ui.theme.C
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class MascotLine(val mood: Mood, val text: String)

fun mascotSays(d: AppData, sum: Summary, today: LocalDate): MascotLine {
    val p = sum.period
    if (sum.income == 0L) return MascotLine(Mood.OK, "Trag unter „Fest“ dein Gehalt ein, dann rechne ich los.")
    if (sum.free < 0) return MascotLine(Mood.WORRIED, "Hui, ${Money.format(-sum.free)} drüber. Kein Drama, nächsten Monat holen wir das rein.")
    if (sum.daysLeft > 3 && sum.perDay in 1..800) return MascotLine(Mood.WORRIED, "Wird etwas knapp: nur noch ${Money.format(sum.perDay)} pro Tag. Augen auf!")
    if (sum.entries.isEmpty()) return MascotLine(Mood.SLEEPY, "Noch ruhig hier. Was hast du heute gekauft?")
    val dayIndex = ChronoUnit.DAYS.between(p.start, today)
    val prev = Periods.previous(p, d.settings)
    val prevSpent = Budget.spentUntilDay(d, prev, dayIndex)
    val nowSpent = Budget.spentUntilDay(d, p, dayIndex)
    val diff = prevSpent - nowSpent
    return when {
        prevSpent > 0 && diff >= 1000 -> MascotLine(Mood.HAPPY, "Läuft super! Du bist ${Money.short(diff)} besser als letzten Monat.")
        prevSpent > 0 && diff <= -3000 -> MascotLine(Mood.OK, "Diesen Monat geht's flotter raus als im letzten. Alles ok, nur zur Info.")
        sum.perDay >= 3000 -> MascotLine(Mood.HAPPY, "Alles entspannt. Heute sind ${Money.short(sum.perDay)} drin.")
        else -> MascotLine(Mood.OK, "Alles im grünen Bereich. Weiter so!")
    }
}

@Composable
fun HomeScreen() {
    val ui = LocalUi.current
    val d by Store.data.collectAsState()
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val period = Periods.periodFor(today, d.settings)
    val sum = remember(d, today) { Budget.summary(d, period, today) }
    val line = remember(d, today) { mascotSays(d, sum, today) }
    val pending = remember(d) { Detection.pending(d) }
    val streak = remember(d) { Budget.streak(d, today) }

    // Benachrichtigungszugriff nach Rückkehr aus den Einstellungen neu prüfen.
    var listenerOn by remember { mutableStateOf(PaymentListenerService.isEnabled(ctx)) }
    val owner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) listenerOn = PaymentListenerService.isEnabled(ctx) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // Konfetti, wenn der letzte Monat im Plus geendet hat (einmal).
    LaunchedEffect(period.key) {
        val prev = Periods.previous(period, d.settings)
        val prevSum = Budget.summary(d, prev, today)
        if (prevSum.income > 0 && prevSum.free > 0 && prev.key !in d.settings.celebrated && (prevSum.entries.isNotEmpty())) {
            ui.confettiKey++
            ui.say("${prev.shortLabel}: ${Money.format(prevSum.free)} übrig!")
            Store.update { it.copy(settings = it.settings.copy(celebrated = it.settings.celebrated + prev.key)) }
        }
    }

    val c = C.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (d.settings.name.isNotBlank()) "Hey ${d.settings.name}" else "Hey du",
                style = MaterialTheme.typography.headlineSmall, color = c.fg, modifier = Modifier.weight(1f),
            )
            if (streak >= 3) Chip("$streak Tage am Stück")
            Spacer(Modifier.padding(3.dp))
            Chip(period.shortLabel)
        }

        // Die eine Zahl, auf die es ankommt.
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.hero)
                .clickable { ui.go(Route.Month) }.padding(18.dp),
        ) {
            Column {
                Text(if (sum.free >= 0) "Noch frei" else "Drüber", style = MaterialTheme.typography.titleSmall, color = c.heroFg.copy(alpha = 0.85f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Money.euros(kotlin.math.abs(sum.free)), style = MaterialTheme.typography.displayLarge, color = c.heroFg)
                    Text(Money.centsPart(kotlin.math.abs(sum.free)), style = MaterialTheme.typography.headlineSmall, color = c.heroFg.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 6.dp))
                }
                Spacer(Modifier.height(10.dp))
                val frac = if (sum.income > 0) (sum.free.toFloat() / sum.income).coerceIn(0f, 1f) else 0f
                CoinBar(frac, c.heroChip, c.coin)
                Spacer(Modifier.height(8.dp))
                Text(
                    listOfNotNull(
                        if (sum.perDay > 0) "heute ${Money.short(sum.perDay)} drin" else null,
                        "noch ${sum.daysLeft} ${if (sum.daysLeft == 1) "Tag" else "Tage"}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = c.heroFg.copy(alpha = 0.85f),
                )
            }
            Mascot(line.mood, Modifier.align(Alignment.TopEnd), size = 64.dp)
        }
        // Das Maskottchen meldet sich nur, wenn es etwas zu sagen gibt.
        if (line.mood == Mood.WORRIED || sum.income == 0L) Hint(line.text, Modifier.padding(horizontal = 4.dp))

        // Die Rechnung dahinter, in vier Zeilen.
        Group {
            ValueRow("Einnahmen", Money.format(sum.income), onClick = { ui.go(Route.Fixed) })
            GroupDivider()
            ValueRow("Fest", "− " + Money.format(sum.fixed), onClick = { ui.go(Route.Fixed) })
            if (sum.reserve > 0) {
                GroupDivider()
                ValueRow("Rücklage", "− " + Money.format(sum.reserve), note = "für Jahres- und Quartalskosten", onClick = { ui.go(Route.Fixed) })
            }
            GroupDivider()
            ValueRow("Ausgegeben", "− " + Money.format(sum.spent), onClick = { ui.go(Route.Month) })
            GroupDivider()
            ValueRow(if (sum.free >= 0) "Noch frei" else "Drüber", Money.format(sum.free), strong = true, valueColor = if (sum.free >= 0) c.fg else c.warn)
        }

        if (pending.isNotEmpty() || !listenerOn || d.settings.cashMode == CashMode.WALLET) {
            Group {
                var first = true
                if (pending.isNotEmpty()) {
                    MenuRow(if (pending.size == 1) "1 Zahlung erkannt" else "${pending.size} Zahlungen erkannt", "Kurz prüfen und eintragen", badge = "${pending.size}") { ui.go(Route.Inbox()) }
                    first = false
                }
                if (!listenerOn) {
                    if (!first) GroupDivider()
                    MenuRow("Zahlungen automatisch erkennen", "Einrichten, dauert eine Minute") { ui.go(Route.Detection) }
                    first = false
                }
                if (d.settings.cashMode == CashMode.WALLET) {
                    if (!first) GroupDivider()
                    MenuRow("Bargeld", "Im Portemonnaie: ${Money.format(Budget.wallet(d))}") { ui.go(Route.Wallet) }
                }
            }
        }

        val todays = sum.entries.filter { it.date == today }
        val recent = if (todays.isNotEmpty()) todays.take(6) else d.entries.sortedByDescending { it.createdAt }.take(3)
        if (recent.isNotEmpty()) {
            SectionTitle(if (todays.isNotEmpty()) "Heute" else "Zuletzt")
            EntryGroup(recent, { d.category(it) }, showDay = todays.isEmpty()) { e -> ui.openAdd(AddRequest(editId = e.id)) }
        }

        val upcoming = remember(d, today) {
            d.recurring.filter { !it.income }.mapNotNull { r -> Recurrence.nextDue(r, today)?.let { r to it } }
                .filter { ChronoUnit.DAYS.between(today, it.second) <= 14 }
                .sortedBy { it.second }.take(3)
        }
        if (upcoming.isNotEmpty()) {
            SectionTitle("Demnächst")
            Group {
                upcoming.forEachIndexed { i, (r, due) ->
                    if (i > 0) GroupDivider()
                    val whenText = when (val days = ChronoUnit.DAYS.between(today, due)) {
                        0L -> "heute"
                        1L -> "morgen"
                        else -> if (days < 7) "in $days Tagen" else "am ${due.dayOfMonth}.${due.monthValue}."
                    }
                    ValueRow(
                        r.name, Money.format(Recurrence.priceAt(r, due)),
                        note = whenText + if (r.spread && r.intervalMonths > 1) ", aus der Rücklage" else "",
                        onClick = { ui.go(Route.RecurringEdit(r.id)) },
                    )
                }
            }
        }
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun Chip(text: String) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(C.colors.surface).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = C.colors.muted, fontWeight = FontWeight.Medium)
    }
}
