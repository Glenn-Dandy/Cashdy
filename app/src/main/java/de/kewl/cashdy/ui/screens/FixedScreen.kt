package de.kewl.cashdy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.kewl.cashdy.data.Recurring
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.PotStatus
import de.kewl.cashdy.domain.Recurrence
import de.kewl.cashdy.domain.Reserve
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.Route
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.Group
import de.kewl.cashdy.ui.components.GroupDivider
import de.kewl.cashdy.ui.components.MenuRow
import de.kewl.cashdy.ui.components.SectionTitle
import de.kewl.cashdy.ui.components.shortDate
import de.kewl.cashdy.ui.theme.C
import java.time.LocalDate

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
    val pots = remember(d, today) { Reserve.all(d, today) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Fest", style = MaterialTheme.typography.headlineMedium, color = c.fg)
        Group {
            Column(Modifier.padding(16.dp)) {
                Text("Pro Monat fest verplant", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                Text(Money.format(perMonth), style = MaterialTheme.typography.headlineMedium, color = c.fg)
                Text("${Money.format(perMonth * 12)} im Jahr", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }

        if (d.recurring.isEmpty()) {
            Hint("Hier kommen Miete, Handy, Netflix, Versicherungen und dein Gehalt rein. Einmal eintragen, ich rechne jeden Monat automatisch mit.")
        }

        if (incomes.isNotEmpty()) {
            SectionTitle("Einnahmen")
            RecurringGroup(incomes, today) { ui.go(Route.RecurringEdit(it.id, true)) }
        }
        if (monthly.isNotEmpty()) {
            SectionTitle("Fixkosten")
            RecurringGroup(monthly.sortedByDescending { Recurrence.currentPrice(it, today) }, today) { ui.go(Route.RecurringEdit(it.id)) }
        }
        if (pots.isNotEmpty()) {
            SectionTitle("Rücklage, ${Money.format(pots.sumOf { it.savedNow })} angespart")
            Group {
                pots.forEachIndexed { i, p ->
                    if (i > 0) GroupDivider()
                    PotRow(p) { ui.go(Route.RecurringEdit(p.recurring.id)) }
                }
            }
            Hint("Seltene Kosten werden auf die Monate verteilt. Kommt die Rechnung, bezahlt sie die Rücklage.")
        }
        if (ended.isNotEmpty()) {
            SectionTitle("Beendet")
            RecurringGroup(ended, today) { ui.go(Route.RecurringEdit(it.id, it.income)) }
        }

        Group {
            MenuRow("Fixkosten hinzufügen") { ui.go(Route.RecurringEdit(null, income = false)) }
            GroupDivider()
            MenuRow("Einnahme hinzufügen", "Gehalt, Kindergeld, Miete von Untermietern") { ui.go(Route.RecurringEdit(null, income = true)) }
            GroupDivider()
            MenuRow("Aus Kontoauszug finden", "Ich schlage Regelmäßiges vor") { ui.go(Route.Import()) }
        }
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun RecurringGroup(items: List<Recurring>, today: LocalDate, onClick: (Recurring) -> Unit) {
    Group {
        items.forEachIndexed { i, r ->
            if (i > 0) GroupDivider()
            RecurringRow(r, today) { onClick(r) }
        }
    }
}

@Composable
private fun RecurringRow(r: Recurring, today: LocalDate, onClick: () -> Unit) {
    val c = C.colors
    val next = Recurrence.nextDue(r, today)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.name, style = MaterialTheme.typography.titleSmall, color = c.fg)
            val parts = listOfNotNull(
                Recurrence.intervalLabel(r.intervalMonths),
                next?.let { "nächste ${shortDate(it)}" } ?: r.endDate?.let { "bis ${shortDate(it)}" },
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.muted)
            Recurrence.changeHint(r, today)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.warn) }
        }
        Text(Money.format(Recurrence.currentPrice(r, today)), style = MaterialTheme.typography.titleSmall, color = if (r.income) c.good else c.fg)
    }
}

@Composable
private fun PotRow(p: PotStatus, onClick: () -> Unit) {
    val c = C.colors
    val r = p.recurring
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleSmall, color = c.fg)
                Text(
                    "${Recurrence.intervalLabel(r.intervalMonths)} ${Money.format(p.bill)} · fällig ${shortDate(p.nextDue)}",
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
            }
            Text("${Money.format(Recurrence.monthlyShare(r, LocalDate.now()))}/Monat", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(c.line)) {
            val f = if (p.bill > 0) (p.savedNow.toFloat() / p.bill).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth(f).height(6.dp).clip(RoundedCornerShape(50)).background(c.coin))
        }
        Text(
            "${Money.format(p.savedNow)} von ${Money.format(p.bill)} angespart" +
                if (p.missing > 0) ", bis zur Fälligkeit fehlen ${Money.format(p.missing)}. Die zählen im ${p.nextDue.format(monthFmt)} extra." else "",
            style = MaterialTheme.typography.bodySmall, color = if (p.missing > 0) c.warn else c.muted, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private val monthFmt = java.time.format.DateTimeFormatter.ofPattern("LLLL", java.util.Locale.GERMAN)
