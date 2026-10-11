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
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.data.category
import de.kewl.cashdy.domain.Budget
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.Periods
import de.kewl.cashdy.ui.AddRequest
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.components.BarValue
import de.kewl.cashdy.ui.components.Card
import de.kewl.cashdy.ui.components.Donut
import de.kewl.cashdy.ui.components.EmojiBubble
import de.kewl.cashdy.ui.components.EntryRow
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.MonthBars
import de.kewl.cashdy.ui.components.Pill
import de.kewl.cashdy.ui.components.SectionTitle
import de.kewl.cashdy.ui.components.Slice
import de.kewl.cashdy.ui.components.categoryTint
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
    var filter by remember { mutableStateOf<String?>(null) }
    val c = C.colors

    val period = Periods.periodFor(anchor, d.settings)
    val sum = remember(d, period) { Budget.summary(d, period, today) }
    val prevSum = remember(d, period) { Budget.summary(d, Periods.previous(period, d.settings), today) }
    val canGoNext = period.end.isBefore(today)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            .pointerInput(period) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (total > 120 && !yearView) anchor = Periods.previous(period, d.settings).start
                        if (total < -120 && canGoNext && !yearView) anchor = Periods.next(period, d.settings).start
                        total = 0f
                    },
                ) { _, dx -> total += dx }
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (yearView) "Dein Jahr" else period.label, style = MaterialTheme.typography.headlineMedium, color = c.fg, modifier = Modifier.weight(1f))
            Pill("Monat", !yearView, { yearView = false })
            Spacer(Modifier.padding(3.dp))
            Pill("Jahr", yearView, { yearView = true })
        }

        if (yearView) {
            YearView()
            Spacer(Modifier.height(150.dp))
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Arrow("‹") { anchor = Periods.previous(period, d.settings).start }
            Text(period.rangeLabel, style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Arrow("›", enabled = canGoNext) { anchor = Periods.next(period, d.settings).start }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mini("💰", "rein", Money.short(sum.income), Modifier.weight(1f))
            Mini("🔁", "fest", Money.short(sum.fixed + sum.reserve), Modifier.weight(1f))
            Mini("🛒", "ausgegeben", Money.short(sum.spent), Modifier.weight(1f))
        }

        Card {
            val slices = sum.byCategory.map { cs -> Slice(cs.amount, Color(d.category(cs.categoryId)?.color ?: 0xFFC3BED6)) }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Donut(slices, size = 200.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (sum.free >= 0) "übrig" else "drüber", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        Text(Money.short(kotlin.math.abs(sum.free)), style = MaterialTheme.typography.headlineSmall, color = if (sum.free >= 0) c.fg else c.warn)
                    }
                }
            }
            if (sum.byCategory.isEmpty()) Hint("Noch nichts in diesem Monat.", Modifier.padding(top = 8.dp))
        }

        if (sum.byCategory.isNotEmpty()) {
            SectionTitle("Wohin ging's?")
            val prevBy = prevSum.byCategory.associate { it.categoryId to it.amount }
            sum.byCategory.forEach { cs ->
                val cat = d.category(cs.categoryId)
                val prev = prevBy[cs.categoryId] ?: 0
                val sel = filter == cs.categoryId
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (sel) c.soft2 else c.surface)
                        .clickable { filter = if (sel) null else cs.categoryId }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EmojiBubble(cat?.emoji ?: "✨", categoryTint(cat, c.dark), 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(cat?.name ?: "Sonstiges", style = MaterialTheme.typography.titleSmall, color = c.fg)
                        if (prev > 0) {
                            val diff = cs.amount - prev
                            Text(
                                if (diff > 0) "▲ ${Money.short(diff)} mehr als im Vormonat" else if (diff < 0) "▼ ${Money.short(-diff)} weniger als im Vormonat" else "wie im Vormonat",
                                style = MaterialTheme.typography.bodySmall, color = if (diff > 0) c.warn else c.good,
                            )
                        }
                    }
                    Text(Money.format(cs.amount), style = MaterialTheme.typography.titleSmall, color = c.fg)
                }
            }
        }

        val list = sum.entries.filter { filter == null || it.categoryId == filter }
        if (list.isNotEmpty()) {
            SectionTitle(if (filter != null) "Einträge: ${d.category(filter)?.name}" else "Alle Einträge")
            list.groupBy { it.date }.toSortedMap(compareByDescending { it }).forEach { (day, es) ->
                Text(dayLabel(day, today), style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
                es.forEach { e -> EntryRow(e, d.category(e.categoryId)) { ui.openAdd(AddRequest(editId = e.id)) } }
            }
        }
        if (sum.fixedItems.isNotEmpty() || sum.reserveItems.isNotEmpty()) {
            SectionTitle("Fest in diesem Monat")
            (sum.fixedItems + sum.reserveItems).sortedBy { it.date }.forEach { o ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(
                        (d.category(o.recurring.categoryId)?.emoji ?: "🔁") + " " + o.recurring.name + if (o.recurring.spread) " (Rücklage)" else "",
                        style = MaterialTheme.typography.bodyMedium, color = c.fg, modifier = Modifier.weight(1f),
                    )
                    Text(Money.format(o.amount), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
            }
        }
        Spacer(Modifier.height(150.dp))
    }
}

@Composable
private fun YearView() {
    val d by Store.data.collectAsState()
    val today = LocalDate.now()
    val c = C.colors
    val periods = remember(d) { Periods.lastN(today, d.settings, 12) }
    val sums = remember(d) { periods.map { Budget.summary(d, it, today) } }
    Card {
        MonthBars(sums.mapIndexed { i, s ->
            BarValue(s.period.shortLabel.take(3), s.income, s.fixed + s.reserve + s.spent, highlight = i == sums.lastIndex)
        })
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 6.dp)) {
            Legend(c.primary, "rein")
            Legend(c.coin, "raus")
        }
    }
    val saved = sums.filter { it.period.end.isBefore(today) }.sumOf { it.free }
    Card(color = c.soft3) {
        Text(if (saved >= 0) "🐷 In den letzten Monaten übrig geblieben" else "📉 In den letzten Monaten drüber", style = MaterialTheme.typography.titleSmall, color = c.fg)
        Text(Money.format(kotlin.math.abs(saved)), style = MaterialTheme.typography.headlineMedium, color = c.fg)
    }
    sums.reversed().forEach { s ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).padding(12.dp)) {
            Text(s.period.label, style = MaterialTheme.typography.titleSmall, color = c.fg, modifier = Modifier.weight(1f))
            Text(Money.format(s.free, withSign = true), style = MaterialTheme.typography.titleSmall, color = if (s.free >= 0) c.good else c.warn)
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(" $text", style = MaterialTheme.typography.bodySmall, color = C.colors.muted)
    }
}

@Composable
private fun Mini(emoji: String, label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(C.colors.surface).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 20.sp)
        Text(value, style = MaterialTheme.typography.titleSmall, color = C.colors.fg, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = C.colors.muted)
    }
}

@Composable
private fun Arrow(t: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (enabled) C.colors.surface else C.colors.bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, fontSize = 24.sp, color = if (enabled) C.colors.fg else C.colors.line) }
}
