package de.kewl.cashdy.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.cashdy.data.Category
import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.data.Entry
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.Summary
import de.kewl.cashdy.ui.theme.C
import de.kewl.cashdy.ui.theme.Fredoka
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

@Composable
fun Card(modifier: Modifier = Modifier, color: Color = C.colors.surface, radius: Dp = 22.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun EmojiBubble(emoji: String, color: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = (size.value * 0.48f).sp)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = C.colors
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.primary else c.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) c.onPrimary else c.fg, maxLines = 1)
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = C.colors.primary, textColor: Color = C.colors.onPrimary) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) color else color.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

@Composable
fun TextButtonSoft(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge, color = C.colors.primary,
    )
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, action: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", tint = C.colors.fg) }
        } else Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = C.colors.fg, modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 6.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall, color = C.colors.muted)
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall, color = C.colors.muted)
}

@Composable
fun SettingSwitch(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onChange(!checked) }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = C.colors.fg)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = C.colors.muted)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = C.colors.primary,
                uncheckedTrackColor = C.colors.line, uncheckedThumbColor = C.colors.muted, uncheckedBorderColor = C.colors.muted,
            ),
        )
    }
}

/**
 * Schrift für Zeilen in Listen, überall gleich: Nunito wie aller Text. Fredoka bleibt
 * Überschriften und Beträgen vorbehalten.
 */
val RowTitle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)

/** Weiße Fläche für zusammengehörige Zeilen, getrennt durch [GroupDivider]. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.colors.surface), content = content)
}

@Composable
fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = if (LocalNested.current) NestIndent else 16.dp, end = 16.dp), color = C.colors.line)
}

/** True innerhalb von [FoldChildren]: Zeilen rücken ein und werden schlichter. */
val LocalNested = compositionLocalOf { false }
val NestIndent = 32.dp

/** Linker Abstand für Zeilen, eingerückt innerhalb von [FoldChildren]. */
val rowStart: Dp @Composable get() = if (LocalNested.current) NestIndent else 16.dp

/** Titel einer Zeile: fett, aufgeklappt unter einer Kategorie normal. */
val ItemTitle: TextStyle
    @Composable get() = if (LocalNested.current) MaterialTheme.typography.bodyLarge else RowTitle

/** Betrag einer Zeile, aufgeklappt etwas leiser. */
val ItemValue: TextStyle
    @Composable get() = if (LocalNested.current) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleSmall

/** Aufgeklappter Inhalt unter einer [FoldRow]: eingerückt auf leicht getöntem Grund. */
@Composable
fun FoldChildren(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.colors.bg)) {
        HorizontalDivider(color = C.colors.line)
        CompositionLocalProvider(LocalNested provides true) { content() }
    }
}

/** Zeile in einer [Group]: Titel, optional Untertitel und Zähler, Pfeil nach rechts. */
@Composable
fun MenuRow(title: String, subtitle: String? = null, badge: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = RowTitle, color = C.colors.fg)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = C.colors.muted)
        }
        if (badge != null) {
            Box(Modifier.clip(CircleShape).background(C.colors.primary).padding(horizontal = 9.dp, vertical = 3.dp)) {
                Text(badge, color = C.colors.onPrimary, style = MaterialTheme.typography.labelMedium)
            }
        }
        Text("›", color = C.colors.muted, fontSize = 22.sp)
    }
}

/** Beschriftung links, Betrag rechts: für die kleinen Rechnungen auf Start und Monat. */
@Composable
fun ValueRow(label: String, value: String, strong: Boolean = false, valueColor: Color = C.colors.fg, note: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = if (strong) RowTitle else MaterialTheme.typography.bodyLarge, color = C.colors.fg)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = C.colors.muted)
        }
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium, color = valueColor)
    }
}

/**
 * Kopfzeile einer zuklappbaren Gruppe: Name, darunter eine kleine Notiz, rechts die Summe und
 * der Pfeil. [bar] (0 bis 1) zeigt darunter einen dünnen Balken, z. B. Anteil an allen Ausgaben.
 */
@Composable
fun FoldRow(
    title: String, value: String, expanded: Boolean, note: String? = null,
    valueColor: Color = C.colors.fg, bar: Float? = null, onClick: () -> Unit,
) {
    val c = C.colors
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 10.dp, top = 13.dp, bottom = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = RowTitle, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor)
            Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = c.muted, modifier = Modifier.padding(start = 4.dp))
        }
        if (bar != null) {
            Box(Modifier.padding(top = 6.dp, end = 28.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(c.line)) {
                Box(Modifier.fillMaxWidth(bar.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(50)).background(c.primary))
            }
        }
    }
}

/** Schlichtes Suchfeld mit Lupe und Löschen-Kreuz. */
@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = C.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.surface).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 13.dp)) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.muted)
            BasicTextField(
                value, onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.fg), cursorBrush = SolidColor(c.primary),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Leeren", tint = c.muted) }
        }
    }
}

/**
 * Erste Zeile der Rechnung: Einnahmen. Im Startmonat stattdessen der Kontostand beim Start,
 * darunter was seitdem schon reingekommen ist und was bis zum Monatsende noch reinkommt.
 */
@Composable
fun IncomeRows(sum: Summary, startDay: LocalDate?, withCash: Boolean, onClick: (() -> Unit)? = null, today: LocalDate = LocalDate.now()) {
    val start = sum.startMoney
    if (start == null || startDay == null) {
        ValueRow("Einnahmen", Money.format(sum.income), onClick = onClick)
        return
    }
    val dm = DateTimeFormatter.ofPattern("d.M.")
    ValueRow(
        (if (withCash) "Konto und Bargeld am " else "Kontostand am ") + startDay.format(dm), Money.format(start),
        note = "Startmonat, ab dem ${sum.period.end.plusDays(1).format(dm)} rechne ich normal", onClick = onClick,
    )
    val rest = sum.income - start
    val received = sum.incomeItems.filter { !it.date.isAfter(today) }.sumOf { it.amount } +
        sum.entries.filter { it.kind == EntryKind.INCOME && !it.date.isBefore(startDay) && !it.date.isAfter(today) }.sumOf { it.amount }
    if (received != 0L) {
        GroupDivider()
        ValueRow("Seitdem reingekommen", Money.format(received), onClick = onClick)
    }
    if (rest - received != 0L) {
        GroupDivider()
        ValueRow("Kommt noch rein", Money.format(rest - received), onClick = onClick)
    }
}

fun categoryTint(cat: Category?, dark: Boolean): Color {
    val base = Color(cat?.color ?: 0xFFC3BED6)
    return if (dark) base.copy(alpha = 0.28f) else base.copy(alpha = 0.32f)
}

private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val shortFmt = DateTimeFormatter.ofPattern("d.M.yyyy")

fun dayLabel(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
    today -> "Heute"
    today.minusDays(1) -> "Gestern"
    else -> d.format(dayFmt)
}

fun shortDate(d: LocalDate): String = d.format(shortFmt)

private val weekdayFmt = DateTimeFormatter.ofPattern("EEE", Locale.GERMAN)

/** "heute", "gestern", "morgen", "Do", "am 30.12." oder "3.10." für Zeilen in Listen. */
fun shortDay(d: LocalDate, today: LocalDate = LocalDate.now()): String = when {
    d == today -> "heute"
    d == today.minusDays(1) -> "gestern"
    d == today.plusDays(1) -> "morgen"
    d.isAfter(today) -> "am " + d.format(DateTimeFormatter.ofPattern("d.M."))
    d.isAfter(today.minusDays(7)) -> d.format(weekdayFmt).trimEnd('.')
    else -> d.format(DateTimeFormatter.ofPattern("d.M."))
}

fun entryTitle(e: Entry, cat: Category?): String = when (e.kind) {
    EntryKind.CASH_WITHDRAWAL -> "Geld abgehoben"
    EntryKind.CASH_ADJUST -> "Bargeld-Abgleich"
    EntryKind.CASH_START -> "Bargeld-Startbestand"
    EntryKind.SAVING -> e.merchant.ifBlank { "Gespart" }
    else -> e.merchant.ifBlank { cat?.name ?: "Ausgabe" }
}

/** Ein Eintrag als Zeile in einer [Group]: Händler, darunter Kategorie, rechts der Betrag. */
@Composable
fun EntryRow(e: Entry, cat: Category?, showDay: Boolean = false, onClick: () -> Unit) {
    val c = C.colors
    val positive = e.kind == EntryKind.INCOME || e.kind == EntryKind.REFUND
    val neutral = e.kind == EntryKind.CASH_WITHDRAWAL || e.kind == EntryKind.CASH_START
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = rowStart, end = 16.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(entryTitle(e, cat), style = ItemTitle, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(
                when {
                    e.kind == EntryKind.REFUND -> "Erstattung"
                    e.kind == EntryKind.SAVING && e.merchant.isNotBlank() -> "Gespart"
                    e.merchant.isNotBlank() && (e.kind == EntryKind.EXPENSE || e.kind == EntryKind.INCOME) -> cat?.name
                    else -> null
                },
                if (e.method == PayMethod.CASH && e.kind == EntryKind.EXPENSE) "🪙 bar" else null,
                if (showDay) shortDay(e.date) else null,
                e.note.ifBlank { null },
            ).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            when {
                positive -> "+" + Money.format(e.amount)
                e.kind == EntryKind.CASH_ADJUST && e.amount < 0 -> "+" + Money.format(-e.amount)
                else -> Money.format(e.amount)
            },
            style = ItemValue,
            color = if (positive) c.good else if (neutral) c.muted else c.fg,
        )
    }
}

/** Mehrere Einträge in einer Gruppe, mit Trennlinien. */
@Composable
fun EntryGroup(entries: List<Entry>, categories: (String?) -> Category?, showDay: Boolean = false, onClick: (Entry) -> Unit) {
    Group {
        entries.forEachIndexed { i, e ->
            if (i > 0) GroupDivider()
            EntryRow(e, categories(e.categoryId), showDay) { onClick(e) }
        }
    }
}

@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp))
            .background(C.colors.surface).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Fredoka, fontWeight = FontWeight.Normal, fontSize = 15.sp), color = C.colors.fg)
    }
}

/** Konfetti für einen Monat im Plus. Fällt einmal herunter und ist dann weg. */
@Composable
fun Confetti(trigger: Any, modifier: Modifier = Modifier) {
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(2600, easing = LinearEasing)) }
    val colors = listOf(Color(0xFF6C4DF6), Color(0xFFFFB37A), Color(0xFFFFF1BF), Color(0xFFFF7EB6), Color(0xFF7ED9B5))
    val pieces = remember(trigger) {
        val r = Random(trigger.hashCode())
        List(70) { Triple(r.nextFloat(), r.nextFloat() * 0.5f + 0.6f, r.nextFloat() * 360f) }
    }
    if (progress.value < 1f) Canvas(modifier.fillMaxSize()) {
        pieces.forEachIndexed { i, (x, speed, rot) ->
            val y = (progress.value * speed * 1.4f - 0.15f) * size.height
            val px = x * size.width + kotlin.math.sin((progress.value * 8 + i) * 1.3f) * 18f
            rotate(rot + progress.value * 540f, Offset(px, y)) {
                drawRect(colors[i % colors.size], Offset(px - 6f, y - 3f), Size(12f, 7f))
            }
        }
    }
}

/**
 * Kategorie-Auswahl als Klappliste, so breit wie das Feld, an dem sie hängt.
 * [anchor] bekommt die Funktion zum Aufklappen.
 */
@Composable
fun CategoryDropdown(
    cats: List<Category>,
    onPick: (Category) -> Unit,
    modifier: Modifier = Modifier,
    anchor: @Composable (open: () -> Unit) -> Unit,
) {
    val c = C.colors
    var open by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box(modifier.onSizeChanged { width = it.width }) {
        anchor { open = true }
        DropdownMenu(
            open, { open = false }, containerColor = c.surface,
            modifier = Modifier.width(with(density) { width.toDp() }).heightIn(max = 360.dp),
        ) {
            cats.forEach { ct ->
                DropdownMenuItem(
                    text = { Text("${ct.emoji}  ${ct.name}", style = MaterialTheme.typography.bodyLarge, color = c.fg) },
                    onClick = { open = false; onPick(ct) },
                )
            }
        }
    }
}

/**
 * Was man am Monatsende aufs Sparkonto legen darf, ohne die Rücklage mitzunehmen.
 * Leer, wenn nichts übrig ist.
 */
fun moveText(free: Cents, held: Cents): String? {
    if (free <= 0) return null
    return "${Money.format(free)} kannst du aufs Sparkonto legen." +
        if (held > 0) " ${Money.format(held)} bitte auf dem Konto lassen, das ist deine Rücklage." else ""
}
