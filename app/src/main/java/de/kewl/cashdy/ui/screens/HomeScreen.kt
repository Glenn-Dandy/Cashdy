package de.kewl.cashdy.ui.screens

import android.content.Intent
import android.provider.Settings as AndroidSettings
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
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
import de.kewl.cashdy.ui.components.Card
import de.kewl.cashdy.ui.components.CoinBar
import de.kewl.cashdy.ui.components.EntryRow
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.Mascot
import de.kewl.cashdy.ui.components.Mood
import de.kewl.cashdy.ui.components.SectionTitle
import de.kewl.cashdy.ui.components.SpeechBubble
import de.kewl.cashdy.ui.components.Sticker
import de.kewl.cashdy.ui.components.TextButtonSoft
import de.kewl.cashdy.ui.theme.C
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class MascotLine(val mood: Mood, val text: String)

fun mascotSays(d: AppData, sum: Summary, today: LocalDate): MascotLine {
    val p = sum.period
    if (sum.income == 0L) return MascotLine(Mood.OK, "Trag unter „Fest“ dein Gehalt ein, dann rechne ich los. 🧮")
    if (sum.free < 0) return MascotLine(Mood.WORRIED, "Hui, ${Money.format(-sum.free)} drüber. Kein Drama, nächsten Monat holen wir das rein. 💪")
    if (sum.daysLeft > 3 && sum.perDay in 1..800) return MascotLine(Mood.WORRIED, "Wird etwas knapp: nur noch ${Money.format(sum.perDay)} pro Tag. Augen auf! 👀")
    if (sum.entries.isEmpty()) return MascotLine(Mood.SLEEPY, "Noch ruhig hier. Was hast du heute gekauft? 😴")
    val dayIndex = ChronoUnit.DAYS.between(p.start, today)
    val prev = Periods.previous(p, d.settings)
    val prevSpent = Budget.spentUntilDay(d, prev, dayIndex)
    val nowSpent = Budget.spentUntilDay(d, p, dayIndex)
    val diff = prevSpent - nowSpent
    return when {
        prevSpent > 0 && diff >= 1000 -> MascotLine(Mood.HAPPY, "Läuft super! Du bist ${Money.short(diff)} besser als letzten Monat. 💪")
        prevSpent > 0 && diff <= -3000 -> MascotLine(Mood.OK, "Diesen Monat geht's flotter raus als im letzten. Alles ok, nur zur Info. 🙂")
        sum.perDay >= 3000 -> MascotLine(Mood.HAPPY, "Alles entspannt. Heute sind ${Money.short(sum.perDay)} drin. ☀️")
        else -> MascotLine(Mood.OK, "Alles im grünen Bereich. Weiter so! 🌱")
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
            ui.say("🎉 ${prev.shortLabel}: ${Money.format(prevSum.free)} übrig!")
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
                if (d.settings.name.isNotBlank()) "Hey ${d.settings.name} 👋" else "Hey du 👋",
                style = MaterialTheme.typography.headlineSmall, color = c.fg, modifier = Modifier.weight(1f),
            )
            if (streak >= 3) Chip("🔥 $streak Tage")
            Spacer(Modifier.padding(3.dp))
            Chip(period.shortLabel)
        }

        // Die große Karte
        Box {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.hero)
                    .clickable { ui.go(Route.Month) }.padding(20.dp),
            ) {
                Text("Noch frei", style = MaterialTheme.typography.titleSmall, color = c.heroFg.copy(alpha = 0.9f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Money.euros(sum.free), style = MaterialTheme.typography.displayLarge, color = c.heroFg)
                    Text(Money.centsPart(sum.free), style = MaterialTheme.typography.headlineSmall, color = c.heroFg.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 6.dp))
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.clip(RoundedCornerShape(50)).background(c.heroChip).padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text(
                        if (sum.perDay > 0) "☀️ heute ${Money.short(sum.perDay)} drin" else "noch ${sum.daysLeft} Tage",
                        style = MaterialTheme.typography.labelLarge, color = c.heroFg,
                    )
                }
                Spacer(Modifier.height(14.dp))
                val frac = if (sum.income > 0) (sum.free.toFloat() / sum.income).coerceIn(0f, 1f) else 0f
                CoinBar(frac, c.heroChip, c.coin)
                Spacer(Modifier.height(4.dp))
                Text(
                    "noch ${sum.daysLeft} ${if (sum.daysLeft == 1) "Tag" else "Tage"} · ${period.rangeLabel}",
                    style = MaterialTheme.typography.bodySmall, color = c.heroFg.copy(alpha = 0.8f),
                )
            }
            Mascot(line.mood, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-6).dp), size = 92.dp)
        }

        SpeechBubble(line.text, Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (d.settings.cashMode == CashMode.WALLET) {
                Sticker("💶", Money.short(Budget.wallet(d)), "Bargeld", c.soft1, -2f, Modifier.weight(1f)) { ui.go(Route.Wallet) }
            } else {
                Sticker("🐷", Money.short(sum.reserve), "Rücklage", c.soft1, -2f, Modifier.weight(1f)) { ui.go(Route.Fixed) }
            }
            Sticker("🛒", Money.short(sum.spent), "ausgegeben", c.soft2, 0f, Modifier.weight(1f)) { ui.go(Route.Month) }
            Sticker("🔁", Money.short(sum.fixed + sum.reserve), "fest", c.soft3, 2f, Modifier.weight(1f)) { ui.go(Route.Fixed) }
        }

        if (pending.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).clickable { ui.go(Route.Inbox()) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.clip(CircleShape).background(c.primary).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("${pending.size}", color = c.onPrimary, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.padding(5.dp))
                Text(if (pending.size == 1) "neue Zahlung erkannt" else "neue Zahlungen erkannt", style = MaterialTheme.typography.titleSmall, color = c.fg, modifier = Modifier.weight(1f))
                Text("›", color = c.muted, fontSize = 22.sp)
            }
        }

        if (!listenerOn) {
            Card(color = c.soft2) {
                Text("✨ Zahlungen automatisch erkennen", style = MaterialTheme.typography.titleSmall, color = c.fg)
                Hint("Wenn PayPal oder deine Bank eine Zahlung meldet, schlage ich sie dir direkt vor. Dafür brauche ich Zugriff auf Benachrichtigungen.")
                TextButtonSoft("Einschalten", {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
            }
        }

        val todays = sum.entries.filter { it.date == today }
        if (todays.isNotEmpty()) {
            SectionTitle("Heute")
            todays.take(6).forEach { e -> EntryRow(e, d.category(e.categoryId)) { ui.openAdd(AddRequest(editId = e.id)) } }
        } else {
            val recent = d.entries.sortedByDescending { it.createdAt }.take(3)
            if (recent.isNotEmpty()) {
                SectionTitle("Zuletzt")
                recent.forEach { e -> EntryRow(e, d.category(e.categoryId)) { ui.openAdd(AddRequest(editId = e.id)) } }
            }
        }

        val upcoming = remember(d, today) {
            d.recurring.filter { !it.income }.mapNotNull { r -> Recurrence.nextDue(r, today)?.let { r to it } }
                .filter { ChronoUnit.DAYS.between(today, it.second) <= 14 }
                .sortedBy { it.second }.take(3)
        }
        upcoming.forEach { (r, due) ->
            val whenText = when (val days = ChronoUnit.DAYS.between(today, due)) {
                0L -> "Heute"
                1L -> "Morgen"
                else -> if (days < 7) "In $days Tagen" else "Am ${due.dayOfMonth}."
            }
            Text(
                "📅 $whenText kommt ${r.name}, ${Money.format(Recurrence.priceAt(r, due))}" + if (r.spread) " (zurückgelegt 🐷)" else "",
                style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(150.dp))
    }
}

@Composable
private fun Chip(text: String) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(C.colors.surface).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = C.colors.muted, fontWeight = FontWeight.Medium)
    }
}
