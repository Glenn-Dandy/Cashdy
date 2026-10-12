package de.kewl.cashdy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.mutableStateMapOf
import de.kewl.cashdy.data.CashMode
import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.data.Entry
import de.kewl.cashdy.data.MonthView
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.ui.components.EntryRow
import de.kewl.cashdy.ui.components.FoldRow
import de.kewl.cashdy.data.category
import de.kewl.cashdy.domain.Budget
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.Periods
import de.kewl.cashdy.ui.AddRequest
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.components.RowTitle
import de.kewl.cashdy.ui.components.EntryGroup
import de.kewl.cashdy.ui.components.Group
import de.kewl.cashdy.ui.components.GroupDivider
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.IncomeRows
import de.kewl.cashdy.ui.components.ValueRow
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.domain.Period
import de.kewl.cashdy.domain.Recurrence
import de.kewl.cashdy.ui.components.Pill
import de.kewl.cashdy.ui.components.SectionTitle
import de.kewl.cashdy.ui.components.dayLabel
import de.kewl.cashdy.ui.theme.C
import java.time.LocalDate

@Composable
fun MonthScreen() {
    val ui = LocalUi.current
    val d by Store.data.collectAsState()
    val today = LocalDate.now()
    var anchor by remember { mutableStateOf(today) }
    var yearView by remember { mutableStateOf(false) }
    val open = remember { mutableStateMapOf<String, Boolean>() }
    val c = C.colors

    val period = Periods.periodFor(anchor, d.settings)
    val sum = remember(d, period) { Budget.summary(d, period, today) }
    val prevSum = remember(d, period) { Budget.summary(d, Periods.previous(period, d.settings), today) }
    // Auch in die Zukunft blättern, um zu sehen, was dort fest ansteht.
    val canGoNext = period.start.isBefore(today.plusMonths(12))
    val future = period.start.isAfter(today)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            .pointerInput(period, yearView) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (!yearView) {
                            if (total > 120) anchor = Periods.previous(period, d.settings).start
                            if (total < -120 && canGoNext) anchor = Periods.next(period, d.settings).start
                        }
                        total = 0f
                    },
                ) { _, dx -> total += dx }
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (yearView) "Jahr" else "Monat", style = MaterialTheme.typography.headlineMedium, color = c.fg, modifier = Modifier.weight(1f))
            Pill("Monat", !yearView, { yearView = false })
            Spacer(Modifier.padding(3.dp))
            Pill("Jahr", yearView, { yearView = true })
        }

        if (yearView) {
            YearView { p -> anchor = p.start; yearView = false }
            Spacer(Modifier.height(110.dp))
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Arrow("‹") { anchor = Periods.previous(period, d.settings).start }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(period.label, style = MaterialTheme.typography.titleMedium, color = c.fg)
                Text(period.rangeLabel, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Arrow("›", enabled = canGoNext) { anchor = Periods.next(period, d.settings).start }
        }

        Group {
            IncomeRows(sum, d.settings.startDay, d.settings.cashMode == CashMode.WALLET)
            GroupDivider()
            ValueRow("Fest", "− " + Money.format(sum.fixed))
            if (sum.reserve > 0) {
                GroupDivider()
                ValueRow("Rücklage", "− " + Money.format(sum.reserve))
            }
            GroupDivider()
            ValueRow("Ausgegeben", "− " + Money.format(sum.spent))
            GroupDivider()
            ValueRow(
                when { future -> "Zum Ausgeben"; sum.free >= 0 -> if (period.end.isBefore(today)) "Übrig geblieben" else "Noch frei"; else -> "Drüber" },
                Money.format(sum.free), strong = true, valueColor = if (sum.free >= 0) c.fg else c.warn,
            )
        }

        if (sum.entries.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Einträge", Modifier.weight(1f).padding(bottom = 8.dp))
                Pill("Kategorie", d.settings.monthView == MonthView.CATEGORY, { Store.update { it.copy(settings = it.settings.copy(monthView = MonthView.CATEGORY)) } })
                Spacer(Modifier.padding(3.dp))
                Pill("Tag", d.settings.monthView == MonthView.DAY, { Store.update { it.copy(settings = it.settings.copy(monthView = MonthView.DAY)) } })
            }
            if (d.settings.monthView == MonthView.CATEGORY) {
                CategoryList(sum.entries, prevSum.entries, open, d.settings.cashMode) { e -> ui.openAdd(AddRequest(editId = e.id)) }
            } else {
                sum.entries.groupBy { it.date }.toSortedMap(compareByDescending { it }).forEach { (day, es) ->
                    Text(dayLabel(day, today), style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
                    EntryGroup(es, { d.category(it) }) { e -> ui.openAdd(AddRequest(editId = e.id)) }
                }
            }
        } else if (!future) Hint("Noch nichts eingetragen in diesem Monat.")

        if (sum.fixedItems.isNotEmpty() || sum.reserveItems.isNotEmpty() || sum.potBills.isNotEmpty()) {
            SectionTitle("Fest in diesem Monat")
            Group {
                var first = true
                fun sep() = !first.also { first = false }
                sum.fixedItems.sortedBy { it.date }.forEach { o ->
                    if (sep()) GroupDivider()
                    val partly = o.recurring.spread && o.recurring.intervalMonths > 1
                    ValueRow(
                        o.recurring.name, Money.format(o.amount),
                        note = if (!partly) "am ${o.date.dayOfMonth}."
                        else if (o.fromPot > 0) "am ${o.date.dayOfMonth}., Rest, ${Money.format(o.fromPot)} kamen aus der Rücklage"
                        else "am ${o.date.dayOfMonth}., die Rücklage war noch leer",
                    )
                }
                sum.potBills.filter { it.amount - it.fromPot <= it.recurring.intervalMonths }.forEach { b ->
                    if (sep()) GroupDivider()
                    ValueRow(b.recurring.name, Money.format(b.amount), valueColor = c.muted, note = "am ${b.date.dayOfMonth}., bezahlt aus der Rücklage")
                }
                sum.reserveItems.forEach { o ->
                    if (sep()) GroupDivider()
                    val r = o.recurring
                    ValueRow("Rücklage ${r.name}", Money.format(o.amount), note = "${Recurrence.intervalLabel(r.intervalMonths)} ${Money.format(Recurrence.priceAt(r, o.date))}")
                }
            }
        }
        Spacer(Modifier.height(110.dp))
    }
}

/**
 * Einträge nach Kategorie, zugeklappt, mit Summe und Balken. Ohne Kategorie landet in einer
 * eigenen Gruppe, Bargeld-Bewegungen ebenso. Einnahmen stehen hinter den Ausgaben.
 */
@Composable
private fun CategoryList(entries: List<Entry>, prev: List<Entry>, open: MutableMap<String, Boolean>, cashMode: CashMode, onClick: (Entry) -> Unit) {
    val d by Store.data.collectAsState()
    val c = C.colors
    fun key(e: Entry): String = when (e.kind) {
        EntryKind.EXPENSE, EntryKind.REFUND, EntryKind.INCOME -> e.categoryId?.takeIf { d.category(it) != null } ?: NONE
        else -> CASH
    }
    // Ausgaben positiv, Einnahmen und Erstattungen negativ.
    fun value(e: Entry): Cents = if (e.kind == EntryKind.INCOME) -e.amount else Budget.spendingValue(e, cashMode)
    val prevBy = prev.groupBy(::key).mapValues { (_, es) -> es.sumOf(::value) }
    val groups = entries.groupBy(::key).map { (k, es) -> Triple(k, es, es.sumOf(::value)) }
        .sortedWith(compareBy<Triple<String, List<Entry>, Cents>> { if (it.first == NONE) 3 else if (it.first == CASH) 2 else if (it.third < 0) 1 else 0 }.thenByDescending { kotlin.math.abs(it.third) })
    val max = groups.maxOf { it.third }.coerceAtLeast(1)
    Group {
        groups.forEachIndexed { i, (k, es, v) ->
            if (i > 0) GroupDivider()
            val name = when (k) { NONE -> "Ohne Kategorie"; CASH -> "Bargeld"; else -> d.category(k)?.name ?: "Ohne Kategorie" }
            val p = prevBy[k] ?: 0
            val note = listOfNotNull(
                "${es.size} ${if (es.size == 1) "Eintrag" else "Einträge"}",
                // Gemischte Gruppe: nicht nur den Saldo zeigen.
                es.map(::value).let { vs ->
                    val out = vs.filter { it > 0 }.sum()
                    val inn = -vs.filter { it < 0 }.sum()
                    if (out > 0 && inn > 0) "${Money.format(out)} raus, ${Money.format(inn)} rein" else null
                },
                if (v > 0 && p > 0) when {
                    v > p -> "${Money.short(v - p)} mehr als im Vormonat"
                    v < p -> "${Money.short(p - v)} weniger als im Vormonat"
                    else -> "wie im Vormonat"
                } else null,
            ).joinToString(", ")
            val isOpen = open[k] ?: false
            FoldRow(
                name, if (v < 0) "+" + Money.format(-v) else Money.format(v), isOpen, note = note,
                valueColor = if (v < 0) c.good else c.fg, bar = if (v > 0) v.toFloat() / max else null,
            ) { open[k] = !isOpen }
            AnimatedVisibility(isOpen) {
                Column {
                    es.forEach { e -> GroupDivider(); EntryRow(e, d.category(e.categoryId), showDay = true) { onClick(e) } }
                }
            }
        }
    }
}

private const val NONE = "_none"
private const val CASH = "_cash"

@Composable
private fun YearView(onOpen: (Period) -> Unit) {
    val d by Store.data.collectAsState()
    val today = LocalDate.now()
    val c = C.colors
    var year by remember { mutableStateOf(Periods.periodFor(today, d.settings).let { it.start.plusDays(14).year }) }
    val plan = remember(d, year) { Budget.year(d, year, today) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Arrow("‹") { year-- }
        Text("$year", style = MaterialTheme.typography.titleLarge, color = c.fg, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Arrow("›", enabled = year <= today.year) { year++ }
    }
    Group {
        Column(Modifier.padding(16.dp)) {
            Text("Fixkosten im ganzen Jahr", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            Text(Money.format(plan.fixedTotal), style = MaterialTheme.typography.headlineMedium, color = c.fg)
            Text("im Schnitt ${Money.format(plan.fixedPerMonth)} pro Monat", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
    Group {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Monat", style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.weight(1.1f))
            Text("rein", style = MaterialTheme.typography.labelMedium, color = c.muted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text("fest", style = MaterialTheme.typography.labelMedium, color = c.muted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text("frei", style = MaterialTheme.typography.labelMedium, color = c.muted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        // Monate vor dem ersten Eintrag sind leer und bleiben weg.
        val shown = plan.months.dropWhile { it.income == 0L && it.fixed == 0L && it.reserve == 0L && it.spent == 0L && it.period.end.isBefore(today) }
        if (shown.isEmpty()) Hint("Für dieses Jahr gibt es noch nichts.", Modifier.padding(16.dp))
        shown.forEach { s ->
            GroupDivider()
            val past = s.period.end.isBefore(today)
            val now = today in s.period
            val tone = when { now -> c.primary; past -> c.muted; else -> c.fg }
            Column(Modifier.fillMaxWidth().clickable { onOpen(s.period) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row {
                    Text(monthAbbr[s.period.start.plusDays(14).monthValue - 1], style = RowTitle, color = tone, modifier = Modifier.weight(1.1f))
                    Text(Money.short(s.income), style = MaterialTheme.typography.bodyMedium, color = tone, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text(Money.short(s.fixed + s.reserve), style = MaterialTheme.typography.bodyMedium, color = tone, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text(
                        (if (past && s.free > 0) "+" else "") + Money.short(s.free),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (s.free < 0) c.warn else if (now) c.primary else c.fg,
                        textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                    )
                }
                // Große Rechnungen in diesem Monat: bezahlt der Topf oder belastet der Rest den Monat?
                s.potBills.forEach { b ->
                    val rest = b.amount - b.fromPot
                    Text(
                        "${b.recurring.name} ${Money.short(b.amount)}" + if (rest > b.recurring.intervalMonths) ", ${Money.short(rest)} fehlen in der Rücklage" else ", aus der Rücklage",
                        style = MaterialTheme.typography.bodySmall, color = if (rest > b.recurring.intervalMonths) c.warn else c.muted,
                    )
                }
                s.fixedItems.filter { it.recurring.intervalMonths > 1 && !it.recurring.spread }.forEach { o ->
                    Text("${o.recurring.name} ${Money.short(o.amount)}", style = MaterialTheme.typography.bodySmall, color = c.warn)
                }
            }
        }
    }
    Hint("Vorbei: was übrig blieb. Jetzt: was noch frei ist. Danach: was zum Ausgeben bereitsteht, wenn nichts dazukommt. Tippen öffnet den Monat.")
}

@Composable
private fun Arrow(t: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (enabled) C.colors.surface else C.colors.bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, fontSize = 24.sp, color = if (enabled) C.colors.fg else C.colors.line) }
}

private val monthAbbr = listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez.")
