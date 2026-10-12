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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.data.FixedView
import de.kewl.cashdy.data.Recurring
import de.kewl.cashdy.data.category
import de.kewl.cashdy.ui.components.FoldChildren
import de.kewl.cashdy.ui.components.FoldRow
import de.kewl.cashdy.ui.components.ItemTitle
import de.kewl.cashdy.ui.components.ItemValue
import de.kewl.cashdy.ui.components.rowStart
import de.kewl.cashdy.ui.components.Pill
import de.kewl.cashdy.ui.components.SearchField
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.PotStatus
import de.kewl.cashdy.domain.Recurrence
import de.kewl.cashdy.domain.Reserve
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.Route
import de.kewl.cashdy.ui.components.RowTitle
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.Group
import de.kewl.cashdy.ui.components.GroupDivider
import de.kewl.cashdy.ui.components.shortDate
import de.kewl.cashdy.ui.theme.C
import java.time.LocalDate

@Composable
fun FixedScreen() {
    val ui = LocalUi.current
    val d by Store.data.collectAsState()
    val today = LocalDate.now()
    val c = C.colors
    val view = d.settings.fixedView
    var query by rememberSaveable { mutableStateOf("") }
    val open = remember { mutableStateMapOf<String, Boolean>() }
    var addMenu by remember { mutableStateOf(false) }

    val q = query.trim()
    fun matches(r: Recurring) = q.isEmpty() || r.name.contains(q, true) || (d.category(r.categoryId)?.name ?: NO_CATEGORY).contains(q, true)
    val active = d.recurring.filter { it.endDate == null || !it.endDate.isBefore(today) }
    val ended = (d.recurring - active.toSet()).filter(::matches)
    val incomes = active.filter { it.income && matches(it) }
    val costs = active.filter { !it.income && matches(it) }
    val pots = remember(d, today) { Reserve.all(d, today).associateBy { it.recurring.id } }
    fun perMonth(r: Recurring): Cents =
        if (r.spread && r.intervalMonths > 1) Recurrence.monthlyShare(r, today) else Recurrence.currentPrice(r, today) / r.intervalMonths
    val costsMonth = costs.sumOf(::perMonth)
    val potMonth = costs.filter { it.spread && it.intervalMonths > 1 }.sumOf(::perMonth)
    val row: @Composable (Recurring) -> Unit = { r ->
        val go = { ui.go(Route.RecurringEdit(r.id, r.income)) }
        val pot = pots[r.id]
        if (pot != null) PotRow(pot, go) else RecurringRow(r, today, go)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fest", style = MaterialTheme.typography.headlineMedium, color = c.fg, modifier = Modifier.weight(1f))
            Box {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(c.primary).clickable { addMenu = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, "Hinzufügen", tint = c.onPrimary) }
                DropdownMenu(addMenu, { addMenu = false }, containerColor = c.surface) {
                    DropdownMenuItem({ Text("Fixkosten", style = RowTitle, color = c.fg) }, { addMenu = false; ui.go(Route.RecurringEdit(null, income = false)) })
                    DropdownMenuItem({ Text("Einnahme", style = RowTitle, color = c.fg) }, { addMenu = false; ui.go(Route.RecurringEdit(null, income = true)) })
                    DropdownMenuItem({ Text("Aus Kontoauszug finden", style = RowTitle, color = c.fg) }, { addMenu = false; ui.go(Route.Import()) })
                }
            }
        }

        Group {
            Column(Modifier.padding(16.dp)) {
                val onlyIncome = q.isNotEmpty() && costs.isEmpty() && incomes.isNotEmpty()
                val count = if (onlyIncome) incomes.size else costs.size
                Text(
                    when {
                        q.isEmpty() -> "Pro Monat fest verplant"
                        count == 0 -> "„$q“: nichts gefunden"
                        else -> "„$q“: $count Posten, pro Monat"
                    },
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
                val total = if (onlyIncome) incomes.sumOf(::perMonth) else costsMonth
                Text(Money.format(total), style = MaterialTheme.typography.headlineMedium, color = if (onlyIncome) c.good else c.fg)
                Text(
                    "${Money.format(total * 12)} im Jahr" + if (potMonth > 0 && !onlyIncome) ", davon ${Money.format(potMonth)} pro Monat Rücklage" else "",
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
            }
        }

        if (d.recurring.isEmpty()) {
            Hint("Hier kommen Miete, Handy, Netflix, Versicherungen und dein Gehalt rein. Einmal eintragen, ich rechne jeden Monat automatisch mit. Tipp oben auf Plus.")
        } else {
            SearchField(query, { query = it }, "Name oder Kategorie suchen")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(FixedView.CATEGORY to "Kategorie", FixedView.NAME to "Name", FixedView.DUE to "Fällig").forEach { (v, label) ->
                    Pill(label, view == v, { Store.update { it.copy(settings = it.settings.copy(fixedView = v)) } })
                }
            }
        }

        // Beim Suchen gehen die Gruppen mit Treffern von selbst auf.
        fun isOpen(key: String) = open[key] ?: (q.isNotEmpty())
        fun toggle(key: String) { open[key] = !isOpen(key) }

        if (costs.isNotEmpty()) {
            when (view) {
                FixedView.CATEGORY -> {
                    val groups = costs.groupBy { it.categoryId?.takeIf { id -> d.category(id) != null } }
                        .map { (cat, rs) -> Triple(cat, rs, rs.sumOf(::perMonth)) }
                        .sortedWith(compareBy<Triple<String?, List<Recurring>, Cents>> { it.first == null }.thenByDescending { it.third })
                    Group {
                        groups.forEachIndexed { i, (cat, rs, sum) ->
                            if (i > 0) GroupDivider()
                            val key = "c:" + (cat ?: "")
                            FoldRow(
                                d.category(cat)?.name ?: NO_CATEGORY, Money.format(sum), isOpen(key),
                                note = "${rs.size} Posten, ${Money.format(sum * 12)} im Jahr",
                            ) { toggle(key) }
                            AnimatedVisibility(isOpen(key)) {
                                FoldChildren {
                                    rs.sortedByDescending(::perMonth).forEachIndexed { j, r -> if (j > 0) GroupDivider(); row(r) }
                                }
                            }
                        }
                    }
                }
                FixedView.NAME -> Group {
                    costs.sortedBy { it.name.lowercase() }.forEachIndexed { i, r -> if (i > 0) GroupDivider(); row(r) }
                }
                FixedView.DUE -> Group {
                    costs.sortedBy { Recurrence.nextDue(it, today) ?: LocalDate.MAX }.forEachIndexed { i, r -> if (i > 0) GroupDivider(); row(r) }
                }
            }
        }

        if (incomes.isNotEmpty() || ended.isNotEmpty()) {
            Group {
                if (incomes.isNotEmpty()) {
                    FoldRow("Einnahmen", Money.format(incomes.sumOf(::perMonth)), isOpen("in"), valueColor = c.good, note = "pro Monat") { toggle("in") }
                    AnimatedVisibility(isOpen("in")) {
                        FoldChildren { incomes.sortedByDescending(::perMonth).forEachIndexed { j, r -> if (j > 0) GroupDivider(); row(r) } }
                    }
                }
                if (ended.isNotEmpty()) {
                    if (incomes.isNotEmpty()) GroupDivider()
                    FoldRow("Beendet", "${ended.size}", isOpen("end"), valueColor = c.muted) { toggle("end") }
                    AnimatedVisibility(isOpen("end")) {
                        FoldChildren { ended.sortedByDescending { it.endDate }.forEachIndexed { j, r -> if (j > 0) GroupDivider(); RecurringRow(r, today) { ui.go(Route.RecurringEdit(r.id, r.income)) } } }
                    }
                }
            }
        }
        Spacer(Modifier.height(110.dp))
    }
}

private const val NO_CATEGORY = "Ohne Kategorie"

@Composable
private fun RecurringRow(r: Recurring, today: LocalDate, onClick: () -> Unit) {
    val c = C.colors
    val next = Recurrence.nextDue(r, today)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = rowStart, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.name, style = ItemTitle, color = c.fg)
            val parts = listOfNotNull(
                Recurrence.intervalLabel(r.intervalMonths),
                next?.let { "nächste ${shortDate(it)}" } ?: r.endDate?.let { "bis ${shortDate(it)}" },
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.muted)
            Recurrence.changeHint(r, today)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.warn) }
        }
        Text(Money.format(Recurrence.currentPrice(r, today)), style = ItemValue, color = if (r.income) c.good else c.fg)
    }
}

@Composable
private fun PotRow(p: PotStatus, onClick: () -> Unit) {
    val c = C.colors
    val r = p.recurring
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = rowStart, end = 16.dp, top = 11.dp, bottom = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, style = ItemTitle, color = c.fg)
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
