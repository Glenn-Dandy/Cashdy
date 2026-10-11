package de.kewl.cashdy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.kewl.cashdy.data.CategoryType
import de.kewl.cashdy.data.Detected
import de.kewl.cashdy.data.DetectedKind
import de.kewl.cashdy.data.DetectedStatus
import de.kewl.cashdy.data.PeriodMode
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.detect.Detection
import de.kewl.cashdy.domain.Merchants
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.Salary
import de.kewl.cashdy.notify.Notify
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.components.Card
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.Mascot
import de.kewl.cashdy.ui.components.Mood
import de.kewl.cashdy.ui.components.Pill
import de.kewl.cashdy.ui.components.ScreenHeader
import de.kewl.cashdy.ui.components.SectionTitle
import de.kewl.cashdy.ui.components.categoryTint
import de.kewl.cashdy.ui.components.dayLabel
import de.kewl.cashdy.ui.theme.C
import de.kewl.cashdy.widget.CashdyWidget

@Composable
fun InboxScreen(focusId: String?) {
    val ui = LocalUi.current
    val d by Store.data.collectAsState()
    val c = C.colors
    val pending = Detection.pending(d).sortedByDescending { it.id == focusId }
    val done = d.detected.filter { it.status != DetectedStatus.PENDING }.sortedByDescending { it.at }.take(30)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Erkannte Zahlungen", onBack = { ui.back() })
        if (pending.isEmpty()) {
            Column(Modifier.padding(vertical = 30.dp).align(Alignment.CenterHorizontally), horizontalAlignment = Alignment.CenterHorizontally) {
                Mascot(Mood.HAPPY, size = 110.dp)
                Spacer(Modifier.height(10.dp))
                Text("Alles erledigt!", style = MaterialTheme.typography.headlineSmall, color = c.fg)
                Hint("Sobald PayPal oder deine Bank eine Zahlung meldet, taucht sie hier auf.")
            }
        }
        pending.forEach { det -> DetectedCard(det, expanded = det.id == focusId || pending.size <= 3) }

        if (done.isNotEmpty()) {
            SectionTitle("Erledigt")
            done.forEach { det ->
                Row(Modifier.padding(horizontal = 6.dp)) {
                    Text(
                        "${statusEmoji(det.status)} ${det.merchant.ifBlank { det.sourceName }} · ${Money.format(det.amount)}",
                        style = MaterialTheme.typography.bodyMedium, color = c.fg, modifier = Modifier.weight(1f),
                    )
                    Text(dayLabel(Detection.dayOf(det)), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                if (det.mergeNote.isNotBlank()) Hint("   ${det.mergeNote}")
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

private fun statusEmoji(s: DetectedStatus) = when (s) {
    DetectedStatus.ACCEPTED -> "✅"
    DetectedStatus.IGNORED -> "🙈"
    DetectedStatus.MERGED -> "🔗"
    DetectedStatus.PENDING -> "⏳"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetectedCard(det: Detected, expanded: Boolean) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val d = Store.current
    val c = C.colors
    var open by remember { mutableStateOf(expanded) }
    val suggested = Merchants.suggestCategory(d, det.merchant)

    fun done(msg: String) {
        Notify.cancel(ctx, det.id)
        CashdyWidget.updateAll(ctx)
        ui.say(msg)
    }

    Card {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { open = !open }) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (det.kind) {
                        DetectedKind.EXPENSE -> "💳 ${det.merchant.ifBlank { "Zahlung" }}"
                        DetectedKind.CASH -> "🏧 Geld abgehoben"
                        DetectedKind.INCOME -> "💰 Eingang" + if (det.merchant.isNotBlank()) " von ${det.merchant}" else ""
                        DetectedKind.REFUND -> "↩️ Erstattung ${det.merchant}"
                    },
                    style = MaterialTheme.typography.titleMedium, color = c.fg,
                )
                Text("${det.sourceName} · ${dayLabel(Detection.dayOf(det))}", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Text(Money.format(det.amount), style = MaterialTheme.typography.titleLarge, color = if (det.kind == DetectedKind.INCOME || det.kind == DetectedKind.REFUND) c.good else c.fg)
        }
        if (open) Hint("„${det.title} ${det.text}“".take(200), Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp))

        when (det.kind) {
            DetectedKind.EXPENSE, DetectedKind.REFUND -> {
                val cats = d.categories.filter { it.type == CategoryType.EXPENSE }
                val ordered = (listOfNotNull(cats.firstOrNull { it.id == suggested }) + cats.filter { it.id != suggested })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (if (open) ordered else ordered.take(4)).forEach { cat ->
                        val sel = cat.id == suggested
                        Text(
                            "${cat.emoji} ${cat.name}" + if (sel) " ✓" else "",
                            Modifier.clip(RoundedCornerShape(50)).background(if (sel) c.primary else categoryTint(cat, c.dark))
                                .clickable { Store.update { Detection.accept(it, det.id, cat.id).first }; done("${cat.emoji} Eingetragen") }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge, color = if (sel) c.onPrimary else c.fg,
                        )
                    }
                    if (!open) Pill("mehr …", false, { open = true })
                }
            }
            DetectedKind.CASH -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("💶 Ins Portemonnaie", true, { Store.update { Detection.accept(it, det.id, null, true).first }; done("Ins Portemonnaie gesteckt") })
                Pill("War eine Ausgabe", false, { Store.update { Detection.accept(it, det.id, null, false).first }; done("Als Ausgabe eingetragen") })
            }
            DetectedKind.INCOME -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d.settings.periodMode == PeriodMode.INCOME) Pill("💰 Gehalt, neuer Monat", true, {
                    Store.update { data -> Salary.startNewPeriod(Detection.ignore(data, det.id).let { x -> x.copy(detected = x.detected.map { if (it.id == det.id) it.copy(status = DetectedStatus.ACCEPTED) else it }) }, Detection.dayOf(det), det.amount) }
                    done("Neuer Monat gestartet 🎉")
                })
                Pill("Einnahme", d.settings.periodMode != PeriodMode.INCOME, { Store.update { Detection.accept(it, det.id, null).first }; done("Als Einnahme eingetragen") })
            }
        }
        Row(Modifier.padding(top = 6.dp)) {
            Spacer(Modifier.weight(1f))
            Text("Nö, ignorieren", Modifier.clip(RoundedCornerShape(50)).clickable {
                Store.update { Detection.ignore(it, det.id) }; done("Ignoriert")
            }.padding(8.dp), style = MaterialTheme.typography.labelLarge, color = c.muted)
        }
    }
}
