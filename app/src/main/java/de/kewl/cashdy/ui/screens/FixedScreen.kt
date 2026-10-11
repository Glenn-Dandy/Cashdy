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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.kewl.cashdy.data.Recurring
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.data.category
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.Recurrence
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.Route
import de.kewl.cashdy.ui.components.Card
import de.kewl.cashdy.ui.components.EmojiBubble
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.Pill
import de.kewl.cashdy.ui.components.SectionTitle
import de.kewl.cashdy.ui.components.categoryTint
import de.kewl.cashdy.ui.components.shortDate
import de.kewl.cashdy.ui.theme.C
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FixedScreen() {
    val ui = LocalUi.current
    val d by Store.data.collectAsState()
    val today = LocalDate.now()
    val c = C.colors

    val active = d.recurring.filter { it.endDate == null || !it.endDate.isBefore(today) }
    val ended = d.recurring - active.toSet()
    val incomes = active.filter { it.income }
    val monthly = active.filter { !it.income && !(it.spread && it.intervalMonths > 1) }
    val spread = active.filter { !it.income && it.spread && it.intervalMonths > 1 }
    val perMonth = monthly.sumOf { Recurrence.currentPrice(it, today) / it.intervalMonths } + spread.sumOf { Recurrence.monthlyShare(it, today) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Fest & regelmäßig", style = MaterialTheme.typography.headlineMedium, color = c.fg)
        Card(color = c.soft3) {
            Text("Pro Monat fest verplant", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            Text(Money.format(perMonth), style = MaterialTheme.typography.headlineMedium, color = c.fg)
            if (spread.isNotEmpty()) Hint("davon ${Money.format(spread.sumOf { Recurrence.monthlyShare(it, today) })} Rücklage für Jahres- und Quartalskosten 🐷")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("＋ Fixkosten", true, { ui.go(Route.RecurringEdit(null, income = false)) })
            Pill("＋ Einnahme", false, { ui.go(Route.RecurringEdit(null, income = true)) })
            Pill("📄 Aus Kontoauszug", false, { ui.go(Route.Import()) })
        }

        if (d.recurring.isEmpty()) {
            Hint("Hier kommen Miete, Handy, Netflix, Versicherungen und dein Gehalt rein. Einmal eintragen, ich rechne jeden Monat automatisch mit.")
        }

        if (incomes.isNotEmpty()) {
            SectionTitle("💰 Einnahmen")
            incomes.forEach { RecurringRow(it, today) { ui.go(Route.RecurringEdit(it.id, true)) } }
        }
        if (monthly.isNotEmpty()) {
            SectionTitle("🔁 Fixkosten")
            monthly.sortedByDescending { Recurrence.currentPrice(it, today) }.forEach { RecurringRow(it, today) { ui.go(Route.RecurringEdit(it.id)) } }
        }
        if (spread.isNotEmpty()) {
            SectionTitle("🐷 Wird zurückgelegt")
            spread.forEach { RecurringRow(it, today) { ui.go(Route.RecurringEdit(it.id)) } }
        }
        if (ended.isNotEmpty()) {
            SectionTitle("Beendet")
            ended.forEach { RecurringRow(it, today) { ui.go(Route.RecurringEdit(it.id, it.income)) } }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun RecurringRow(r: Recurring, today: LocalDate, onClick: () -> Unit) {
    val d = Store.current
    val c = C.colors
    val cat = d.category(r.categoryId)
    val next = Recurrence.nextDue(r, today)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EmojiBubble(cat?.emoji ?: if (r.income) "💰" else "🔁", categoryTint(cat, c.dark), 40.dp)
        Column(Modifier.weight(1f)) {
            Text(r.name, style = MaterialTheme.typography.titleSmall, color = c.fg)
            val parts = listOfNotNull(
                Recurrence.intervalLabel(r.intervalMonths),
                next?.let { "nächste ${shortDate(it)}" },
                if (r.spread && r.intervalMonths > 1) "${Money.format(Recurrence.monthlyShare(r, today))}/Monat" else null,
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.muted)
            Recurrence.changeHint(r, today)?.let { Text("📈 $it", style = MaterialTheme.typography.bodySmall, color = c.warn) }
        }
        Text(Money.format(Recurrence.currentPrice(r, today)), style = MaterialTheme.typography.titleSmall, color = if (r.income) c.good else c.fg)
    }
}
