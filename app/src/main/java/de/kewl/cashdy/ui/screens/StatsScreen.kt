package de.kewl.cashdy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.data.DefaultCategories
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.data.category
import de.kewl.cashdy.domain.Budget
import de.kewl.cashdy.domain.CategoryStat
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.Insight
import de.kewl.cashdy.domain.MonthStat
import de.kewl.cashdy.domain.Periods
import de.kewl.cashdy.domain.Stats
import de.kewl.cashdy.domain.Trend
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.Route
import de.kewl.cashdy.ui.components.Group
import de.kewl.cashdy.ui.components.GroupDivider
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.Mascot
import de.kewl.cashdy.ui.components.Mood
import de.kewl.cashdy.ui.components.Pill
import de.kewl.cashdy.ui.components.RowTitle
import de.kewl.cashdy.ui.components.ScreenHeader
import de.kewl.cashdy.ui.theme.C
import java.time.LocalDate

/**
 * Reiter „Statistik“: wie die Monate liefen, wohin das Geld geht, was fest weggeht.
 * Oben 6 Monate oder ein ganzes Jahr. Tippen führt in den Monat oder den Verlauf einer Kategorie.
 */
@Composable
fun StatsScreen() {
    val ui = LocalUi.current
    val d by Store.data.collectAsState()
    val today = LocalDate.now()
    val c = C.colors
    val yearMode = d.settings.statsYear
    var year by remember { mutableStateOf(Periods.periodFor(today, d.settings).start.plusDays(14).year) }

    val plan = remember(d, year, yearMode) { if (yearMode) Budget.year(d, year, today) else null }
    val months = remember(d, year, yearMode) {
        if (plan != null) Stats.of(d, plan.months, today) else Stats.lastMonths(d, today)
    }
    val cats = remember(months) { Stats.categories(d, months) }
    val used = months.filter { it.used }
    val done = used.filter { it.done }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Statistik", style = MaterialTheme.typography.headlineMedium, color = c.fg)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("6 Monate", !yearMode, { Store.update { it.copy(settings = it.settings.copy(statsYear = false)) } })
            Pill("Jahr", yearMode, { Store.update { it.copy(settings = it.settings.copy(statsYear = true)) } })
            if (yearMode) {
                Spacer(Modifier.weight(1f))
                SmallArrow("‹") { year-- }
                Text("$year", style = MaterialTheme.typography.titleMedium, color = c.fg, modifier = Modifier.padding(horizontal = 6.dp))
                SmallArrow("›", enabled = year < today.year) { year++ }
            }
        }

        if (plan != null) {
            StatCard {
                Text("Fixkosten $year, jede Rechnung voll gezählt", style = MaterialTheme.typography.bodySmall, color = c.muted)
                Text(Money.format(plan.fixedTotal), style = MaterialTheme.typography.headlineMedium, color = c.fg)
                Text("im Schnitt ${Money.format(plan.fixedPerMonth)} pro Monat", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }

        if (done.isEmpty() && used.none { it.sum.entries.isNotEmpty() }) {
            StatCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mascot(Mood.HAPPY, size = 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Gleich gibt's hier was zu sehen", style = MaterialTheme.typography.titleMedium, color = c.fg)
                        Text("Trag noch ein bisschen ein. Nach dem ersten ganzen Monat zeig ich dir hier, wie es läuft.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    }
                }
            }
        } else {
            MonthsCard(months, plan != null, year, today)
        }

        if (plan != null) YearTable(months, today)

        if (cats.isNotEmpty()) {
            StatCard {
                Text("Wohin geht dein Geld?", style = MaterialTheme.typography.titleMedium, color = c.fg)
                Text("Deine eigenen Ausgaben ohne Fixkosten, im Schnitt pro Monat. Tipp für den Verlauf.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                val top = cats.take(5)
                val max = top.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1) ?: 1
                top.forEachIndexed { i, cs ->
                    if (i > 0) GroupDividerFlat()
                    CategoryLine(d, cs, max, done.size) { ui.go(Route.CategoryStats(cs.categoryId, if (plan != null) year else null)) }
                }
            }
        }

        FixedCard(d, today)

        val saved = used.sumOf { it.sum.saved }
        if (saved > 0) {
            StatCard {
                Text("Gespart", style = MaterialTheme.typography.titleMedium, color = c.fg)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mascot(Mood.HEART, size = 44.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(Money.format(saved), style = MaterialTheme.typography.headlineSmall, color = c.good)
                        Text(if (plan != null) "$year aufs Sparkonto gelegt" else "in diesen Monaten aufs Sparkonto gelegt", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
                ValueBars(used.map { it.sum.saved }, used.map { abbr(it.period, short = used.size > 6) }, c.good)
            }
        }

        val tips = remember(months, cats) { Stats.insights(d, months, cats, today) }
        if (tips.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.soft3).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mascot(Mood.HAPPY, size = 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Mir ist aufgefallen", style = MaterialTheme.typography.titleMedium, color = c.fg)
                }
                tips.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Beim Spar-Hinweis schaut die Münze skeptisch, sonst reicht die oben.
                        if (t.tone == Insight.Tone.DOUBT) {
                            Mascot(Mood.SKEPTIC, size = 40.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(t.text, style = MaterialTheme.typography.bodyMedium, color = c.fg)
                    }
                }
            }
        }
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun MonthsCard(months: List<MonthStat>, yearMode: Boolean, year: Int, today: LocalDate) {
    val ui = LocalUi.current
    val c = C.colors
    val done = months.filter { it.used && it.done }
    val plus = done.count { it.sum.free >= 0 }
    StatCard {
        Text("Wie liefen deine Monate?", style = MaterialTheme.typography.titleMedium, color = c.fg)
        if (done.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(if (plus * 2 >= done.size) Mood.CHEER else Mood.HEART, size = 44.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                (if (plus * 2 >= done.size) "In $plus von ${done.size} Monaten bist du im Plus geblieben." else "Es war nicht leicht, aber du bleibst dran. Das zählt!") +
                    if (months.any { it.current }) " Der ${months.first { it.current }.period.shortLabel} läuft noch." else "",
                style = MaterialTheme.typography.bodyMedium, color = c.fg,
            )
        }
        FreeBars(
            months.map { m ->
                val state = when {
                    m.current -> BarState.OPEN
                    !m.used && m.period.start.isAfter(today) -> BarState.PLAN
                    !m.used -> BarState.EMPTY
                    m.sum.free >= 0 -> BarState.PLUS
                    else -> BarState.MINUS
                }
                Bar(abbr(m.period, short = yearMode), m.sum.free, state) {
                    ui.go(Route.MonthDetail(m.period.start))
                }.let { b -> if (state == BarState.EMPTY || state == BarState.PLAN) b.copy(onClick = null) else b }
            },
            showValues = !yearMode,
        )
        Text(
            "Grün: übrig geblieben. Apricot: drüber. Umrandet: läuft noch" + (if (yearMode) " oder kommt noch" else "") + ". Tipp öffnet den Monat.",
            style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
    }
}

/** Alle Monate des Jahres mit rein, fest, frei und den großen Rechnungen darunter. */
@Composable
private fun YearTable(months: List<MonthStat>, today: LocalDate) {
    val ui = LocalUi.current
    val c = C.colors
    val shown = months.dropWhile { !it.used && it.done }
    if (shown.isEmpty()) return
    Group {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Monat", style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.weight(1.1f))
            Text("rein", style = MaterialTheme.typography.labelMedium, color = c.muted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text("fest", style = MaterialTheme.typography.labelMedium, color = c.muted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text("frei", style = MaterialTheme.typography.labelMedium, color = c.muted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        shown.forEach { m ->
            val s = m.sum
            GroupDivider()
            val past = m.done
            val now = today in m.period
            val tone = when { now -> c.primary; past -> c.muted; else -> c.fg }
            Column(Modifier.fillMaxWidth().clickable { ui.go(Route.MonthDetail(m.period.start)) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row {
                    Text(abbr(m.period), style = RowTitle, color = tone, modifier = Modifier.weight(1.1f))
                    Text(Money.short(s.income), style = MaterialTheme.typography.bodyMedium, color = tone, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text(Money.short(s.fixed + s.reserve), style = MaterialTheme.typography.bodyMedium, color = tone, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    Text(
                        (if (past && s.free > 0) "+" else "") + Money.short(s.free),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (s.free < 0) c.warn else if (now) c.primary else c.fg,
                        textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                    )
                }
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
        Hint("Vorbei: was übrig blieb. Jetzt: was noch frei ist. Danach: was zum Ausgeben bereitsteht, wenn nichts dazukommt.", Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
    }
}

@Composable
private fun CategoryLine(d: AppData, cs: CategoryStat, max: Cents, doneCount: Int, onClick: () -> Unit) {
    val c = C.colors
    val name = d.category(cs.categoryId)?.name ?: "Sonstiges"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = RowTitle, color = c.fg, modifier = Modifier.weight(1f))
            Text(Money.short(cs.avg), style = RowTitle, color = c.fg)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Spark(cs.values.takeLast(6), max)
            Spacer(Modifier.width(8.dp))
            val doneValues = cs.values.take(doneCount)
            Text(
                when (Stats.trend(doneValues)) { Trend.LESS -> "wird weniger"; Trend.MORE -> "wird mehr"; Trend.SAME -> "bleibt ungefähr gleich" },
                style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
        }
    }
}

@Composable
private fun FixedCard(d: AppData, today: LocalDate) {
    val ui = LocalUi.current
    val c = C.colors
    val fixed = remember(d) { Stats.fixed(d, today) }
    if (fixed.isEmpty()) return
    val max = fixed.maxOf { it.perMonth }.coerceAtLeast(1)
    StatCard {
        Text("Was kosten dich deine Fixkosten?", style = MaterialTheme.typography.titleMedium, color = c.fg)
        fixed.forEachIndexed { i, f ->
            if (i > 0) GroupDividerFlat()
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { ui.go(Route.Fixed) }.padding(vertical = 6.dp)) {
                Row {
                    Text(d.category(f.categoryId)?.name ?: "Ohne Kategorie", style = RowTitle, color = c.fg, modifier = Modifier.weight(1f))
                    Text(Money.format(f.perMonth), style = RowTitle, color = c.fg)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(c.line)) {
                        Box(Modifier.fillMaxWidth((f.perMonth.toFloat() / max).coerceIn(0.03f, 1f)).height(6.dp).clip(RoundedCornerShape(50)).background(c.primary))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("${Money.format(f.perYear)} im Jahr", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.line))
        Row {
            Text("Zusammen", style = RowTitle, color = c.fg, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(fixed.sumOf { it.perMonth }), style = RowTitle, color = c.fg)
                Text("${Money.format(fixed.sumOf { it.perYear })} im Jahr", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        fixed.firstOrNull { it.categoryId == DefaultCategories.SUBS }?.let {
            Text("Abos & Streaming kosten dich ${Money.format(it.perYear)} im Jahr. Lohnt sich jedes davon noch?", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}

/** Verlauf einer Kategorie mit Schnitt-Linie und den Läden, wo am meisten zusammenkam. */
@Composable
fun CategoryStatsScreen(categoryId: String, year: Int?) {
    val ui = LocalUi.current
    val d by Store.data.collectAsState()
    val today = LocalDate.now()
    val c = C.colors
    val months = remember(d, year) {
        if (year != null) Stats.of(d, Budget.year(d, year, today).months, today).filter { it.used } else Stats.lastMonths(d, today).filter { it.used }
    }
    val cs = remember(months) { Stats.categories(d, months).firstOrNull { it.categoryId == categoryId } }
    val name = d.category(categoryId)?.name ?: "Sonstiges"
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(name, onBack = { ui.back() })
        if (cs == null) {
            Hint("Hier gibt es noch nichts zu zeigen.")
            return@Column
        }
        val done = months.filter { it.done }
        StatCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(Mood.CHEER, size = 44.dp)
                Spacer(Modifier.width(10.dp))
                val best = done.indices.minByOrNull { cs.values[it] }
                Text(
                    "Im Schnitt ${Money.short(cs.avg)} im Monat." +
                        if (best != null && done.size >= 2) " Am wenigsten im ${done[best].period.shortLabel}: ${Money.short(cs.values[best])}." else "",
                    style = MaterialTheme.typography.bodyMedium, color = c.fg,
                )
            }
            ValueBars(
                cs.values, months.map { abbr(it.period, short = months.size > 6) }, c.primary,
                avg = cs.avg, currentIndex = months.indexOfFirst { it.current },
                showValues = months.size <= 6,
            ) { i -> ui.go(Route.MonthDetail(months[i].period.start, categoryId)) }
            Text("Orange Linie: dein Schnitt. Tipp auf einen Monat öffnet ihn.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        val top = remember(months) { Stats.topMerchants(d, categoryId, months) }
        if (top.isNotEmpty()) StatCard {
            Text("Wo am meisten?", style = MaterialTheme.typography.titleMedium, color = c.fg)
            top.forEachIndexed { i, (m, sum, n) ->
                if (i > 0) GroupDividerFlat()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m, style = RowTitle, color = c.fg)
                        Text(if (n == 1) "1 Eintrag" else "$n Einträge", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Text(Money.format(sum), style = RowTitle, color = c.fg)
                }
            }
            Text("Seit ${months.first().period.shortLabel} zusammen", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ---------- Bausteine ----------

@Composable
private fun StatCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content,
    )
}

@Composable
private fun GroupDividerFlat() = Box(Modifier.fillMaxWidth().height(1.dp).background(C.colors.line))

@Composable
private fun SmallArrow(t: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(if (enabled) C.colors.surface else C.colors.bg).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, fontSize = 20.sp, color = if (enabled) C.colors.fg else C.colors.line) }
}

private val Apricot = Color(0xFFFFB37A)
private val monthAbbr = listOf("Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez.")

/** Monatskürzel nach dem Monat, in dem der größte Teil des Zeitraums liegt. [short]: nur der erste Buchstabe. */
private fun abbr(p: de.kewl.cashdy.domain.Period, short: Boolean = false): String {
    val a = monthAbbr[p.start.plusDays(14).monthValue - 1]
    return if (short) a.take(1) else a
}

private enum class BarState { PLUS, MINUS, OPEN, PLAN, EMPTY }
private data class Bar(val label: String, val value: Cents, val state: BarState, val onClick: (() -> Unit)?)

/** Übrig (nach oben, grün) oder drüber (nach unten, apricot) je Monat. */
@Composable
private fun FreeBars(bars: List<Bar>, showValues: Boolean) {
    val c = C.colors
    val up = 64.dp
    // Platz nach unten nur, wenn ein Monat drüber war.
    val down = if (bars.any { it.value < 0 && it.state != BarState.EMPTY && it.state != BarState.PLAN }) 28.dp else 4.dp
    val max = bars.filter { it.state == BarState.PLUS || it.state == BarState.MINUS }.maxOfOrNull { kotlin.math.abs(it.value) }?.coerceAtLeast(1)
        ?: bars.maxOfOrNull { kotlin.math.abs(it.value) }?.coerceAtLeast(1) ?: 1
    fun h(v: Cents, limit: Dp): Dp = (limit * (kotlin.math.abs(v).toFloat() / max)).coerceIn(4.dp, limit)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        bars.forEach { b ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).then(if (b.onClick != null) Modifier.clickable(onClick = b.onClick) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(up), contentAlignment = Alignment.BottomCenter) {
                    when (b.state) {
                        BarState.PLUS -> Box(Modifier.fillMaxWidth(0.72f).height(h(b.value, up)).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(c.good))
                        BarState.OPEN -> if (b.value > 0) Box(
                            Modifier.fillMaxWidth(0.72f).height(h(b.value, up)).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(c.primary.copy(alpha = 0.18f)).border(1.5.dp, c.primary, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                        )
                        BarState.PLAN -> Box(
                            Modifier.fillMaxWidth(0.72f).height(24.dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .border(1.5.dp, c.muted.copy(alpha = 0.5f), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                        )
                        else -> {}
                    }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(c.line))
                Box(Modifier.fillMaxWidth().height(down), contentAlignment = Alignment.TopCenter) {
                    if (b.state == BarState.MINUS || (b.state == BarState.OPEN && b.value < 0)) Box(
                        Modifier.fillMaxWidth(0.72f).height(h(b.value, down)).clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)).background(Apricot),
                    )
                }
                if (showValues && b.state != BarState.EMPTY && b.state != BarState.PLAN) Text(
                    (if (b.value > 0) "+" else "") + Money.short(b.value), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    color = when { b.value < 0 -> c.warn; b.state == BarState.OPEN -> c.primary; else -> c.good },
                )
                Text(b.label, fontSize = 11.sp, color = c.muted, maxLines = 1)
            }
        }
    }
}

/** Kleiner Verlauf: ein Strich je Monat, der letzte kräftig. */
@Composable
private fun Spark(values: List<Cents>, max: Cents) {
    val c = C.colors
    Row(Modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Box(
                Modifier.width(6.dp).height((16.dp * (v.toFloat() / max)).coerceIn(2.dp, 16.dp)).clip(RoundedCornerShape(2.dp))
                    .background(c.primary.copy(alpha = if (i == values.lastIndex) 1f else 0.35f)),
            )
        }
    }
}

/** Balken je Monat, optional mit Schnitt-Linie und umrandetem laufenden Monat. */
@Composable
private fun ValueBars(
    values: List<Cents>, labels: List<String>, color: Color, avg: Cents? = null, currentIndex: Int = -1,
    showValues: Boolean = false, onClick: ((Int) -> Unit)? = null,
) {
    val c = C.colors
    val height = 90.dp
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(avg ?: 0).coerceAtLeast(1) * 115 / 100
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            values.forEachIndexed { i, v ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).then(if (onClick != null) Modifier.clickable { onClick(i) } else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (showValues) Text(Money.short(v), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.fg, maxLines = 1)
                    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.BottomCenter) {
                        val bh = (height * (v.toFloat() / max)).coerceIn(if (v > 0) 4.dp else 1.dp, height)
                        val shape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)
                        Box(
                            Modifier.fillMaxWidth(0.72f).height(bh).clip(shape)
                                .then(
                                    if (i == currentIndex) Modifier.background(color.copy(alpha = 0.18f)).border(1.5.dp, color, shape)
                                    else Modifier.background(if (v > 0) color else c.line)
                                ),
                        )
                    }
                    Text(labels.getOrElse(i) { "" }, fontSize = 11.sp, color = c.muted, maxLines = 1)
                }
            }
        }
        if (avg != null && avg > 0) {
            // Schnitt-Linie über den Balken, von unten gemessen (Beschriftung unten: 16dp).
            val offset = 16.dp + height * (avg.toFloat() / max)
            Box(Modifier.align(Alignment.BottomStart).padding(bottom = offset).fillMaxWidth().height(2.dp).background(Apricot))
        }
    }
}
