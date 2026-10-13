package de.kewl.cashdy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import de.kewl.cashdy.ui.components.CategoryDropdown
import de.kewl.cashdy.detect.Detection
import de.kewl.cashdy.data.DetectedStatus
import de.kewl.cashdy.domain.Merchants
import de.kewl.cashdy.notify.Notify
import de.kewl.cashdy.ui.components.RowTitle
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.cashdy.data.CategoryType
import de.kewl.cashdy.data.DefaultCategories
import de.kewl.cashdy.data.PriceStep
import de.kewl.cashdy.data.Recurring
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.Periods
import de.kewl.cashdy.domain.Recurrence
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.components.BigButton
import de.kewl.cashdy.ui.components.Card
import de.kewl.cashdy.ui.components.DateChip
import de.kewl.cashdy.ui.components.DatePick
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.MoneyField
import de.kewl.cashdy.ui.components.Pill
import de.kewl.cashdy.ui.components.ScreenHeader
import de.kewl.cashdy.ui.components.SectionTitle
import de.kewl.cashdy.ui.components.SettingSwitch
import de.kewl.cashdy.ui.components.SoftField
import de.kewl.cashdy.ui.components.TextButtonSoft
import de.kewl.cashdy.ui.components.categoryTint
import de.kewl.cashdy.ui.components.shortDate
import de.kewl.cashdy.ui.theme.C
import de.kewl.cashdy.widget.CashdyWidget
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringEditScreen(id: String?, incomeDefault: Boolean, fromDetected: String? = null) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val d = Store.current
    val existing = id?.let { rid -> d.recurring.firstOrNull { it.id == rid } }
    val c = C.colors
    val today = LocalDate.now()

    // Aus einer erkannten Zahlung: Name, Betrag und Tag sind schon ausgefüllt.
    val det = fromDetected?.let { did -> d.detected.firstOrNull { it.id == did } }
    val income = existing?.income ?: incomeDefault
    var name by remember { mutableStateOf(existing?.name ?: det?.merchant ?: if (income) "Gehalt" else "") }
    var amount by remember { mutableStateOf(existing?.let { Money.format(Recurrence.currentPrice(it, today)).removeSuffix(" €") } ?: det?.let { Money.format(it.amount).removeSuffix(" €") } ?: "") }
    var interval by remember { mutableStateOf(existing?.intervalMonths ?: 1) }
    var customInterval by remember { mutableStateOf(if (interval !in listOf(1, 3, 6, 12)) interval.toString() else "") }
    var firstDue by remember { mutableStateOf(existing?.firstDue ?: det?.let { Detection.dayOf(it) } ?: today) }
    var endDate by remember { mutableStateOf(existing?.endDate) }
    var categoryId by remember {
        mutableStateOf(
            existing?.categoryId ?: det?.let { dt -> Merchants.suggestCategory(d, dt.merchant)?.takeIf { cid -> d.categories.any { it.id == cid && it.fixed } } }
                ?: if (income) DefaultCategories.SALARY else null
        )
    }
    var spread by remember { mutableStateOf(existing?.spread ?: false) }
    var spreadTouched by remember { mutableStateOf(existing != null) }
    var salary by remember { mutableStateOf(existing?.salary ?: (income && d.recurring.none { it.salary })) }
    var prices by remember { mutableStateOf(existing?.prices ?: emptyList()) }
    var overrides by remember { mutableStateOf(existing?.overrides ?: emptyMap()) }
    var pick by remember { mutableStateOf<String?>(null) }
    var priceDialog by remember { mutableStateOf(false) }
    var overrideDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(if (existing == null) (if (income) "Neue Einnahme" else "Neue Fixkosten") else name.ifBlank { "Bearbeiten" }, onBack = { ui.back() })

        if (det != null) Hint("Aus der erkannten Zahlung über ${det.sourceName}. Doch keine Fixkosten? Mit dem Pfeil oben kommst du zurück, es geht nichts verloren.")
        if (existing == null && !income && det == null) {
            Hint("Schnell-Vorlagen:")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.forEach { t ->
                    Pill(t.name, name == t.name, {
                        name = t.name; categoryId = t.cat; interval = t.months
                        if (t.amount > 0 && amount.isBlank()) amount = Money.format(t.amount).removeSuffix(" €")
                        if (!spreadTouched) spread = t.months > 1
                    })
                }
            }
        }

        SoftField(name, { name = it }, "Name, z. B. Miete oder Netflix")
        if (existing == null || prices.size <= 1) {
            MoneyField(amount, { amount = it }, "Betrag in €")
        } else {
            Card {
                Text("Aktuell ${Money.format(Recurrence.currentPrice(existing.copy(prices = prices), today))}", style = MaterialTheme.typography.titleMedium, color = c.fg)
            }
        }

        SectionTitle("Wie oft?")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 6, 12).forEach { m ->
                Pill(Recurrence.intervalLabel(m), interval == m && customInterval.isBlank(), {
                    interval = m; customInterval = ""
                    if (!spreadTouched && !income) spread = m > 1
                })
            }
            Pill("andere", customInterval.isNotBlank(), { customInterval = interval.toString().takeIf { interval !in listOf(1, 3, 6, 12) } ?: "2"; interval = customInterval.toInt() })
        }
        if (customInterval.isNotBlank()) {
            SoftField(customInterval, { t ->
                customInterval = t.filter { it.isDigit() }.take(2)
                customInterval.toIntOrNull()?.takeIf { it in 1..60 }?.let { interval = it }
            }, "Alle wie viele Monate?", keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
        }

        val loan = !income && categoryId == DefaultCategories.LOANS
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (loan) "Erste Rate am" else if (existing == null) "Erste Fälligkeit" else "Fällig ab", style = MaterialTheme.typography.bodyLarge, color = c.fg, modifier = Modifier.weight(1f))
            DateChip(firstDue, { pick = "first" })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (loan) "Letzte Rate am" else "Endet am (optional)", style = MaterialTheme.typography.bodyLarge, color = c.fg, modifier = Modifier.weight(1f))
            if (endDate != null) {
                TextButtonSoft("✕", { endDate = null })
                DateChip(endDate!!, { pick = "end" })
            } else TextButtonSoft("festlegen", { pick = "end" })
        }
        if (loan) Hint("Beide Daten stehen in deinem Kreditvertrag. Ungefähr reicht. Dann zeige ich dir, wie viele Raten noch fehlen, und nach der letzten ist die Rate von selbst weg.")

        SectionTitle("Kategorie")
        val cats = d.categories.filter { it.type == if (income) CategoryType.INCOME else CategoryType.EXPENSE }
            .filter { it.id != DefaultCategories.CASH_UNKNOWN }
            .sortedByDescending { it.fixed }
        val cat = cats.firstOrNull { it.id == categoryId }
        CategoryDropdown(cats, { categoryId = it.id }, Modifier.fillMaxWidth()) { open ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, c.line, RoundedCornerShape(18.dp))
                    .clickable(onClick = open).heightIn(min = 52.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(cat?.let { "${it.emoji} ${it.name}" } ?: "Kategorie wählen", style = RowTitle, color = if (cat == null) c.muted else c.fg, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.muted)
            }
        }

        if (!income && interval > 1) {
            SettingSwitch("Auf die Monate verteilen", "Ich lege jeden Monat ${amountPerMonth(amount, interval)} zurück, damit der Fälligkeitsmonat nicht ins Minus kippt.", spread) { spread = it; spreadTouched = true }
        }

        if (existing != null) {
            SectionTitle(if (income) "Gehaltsverlauf" else "Preisverlauf")
            Card {
                prices.sortedByDescending { it.from }.forEachIndexed { i, p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("ab ${shortDate(p.from)}", style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
                        Text(Money.format(p.amount), style = MaterialTheme.typography.titleSmall, color = c.fg)
                        if (i == 0 && prices.size > 1) Text("  ← aktuell", style = MaterialTheme.typography.bodySmall, color = c.primary)
                        if (prices.size > 1) Text("  ✕", Modifier.clickable { prices = prices - p }, color = c.muted, fontSize = 14.sp)
                    }
                }
                Recurrence.changeHint(existing.copy(prices = prices), today)?.let { Hint(it.replaceFirstChar { ch -> ch.uppercase() }) }
                TextButtonSoft(if (income) "Gehalt ändert sich" else "Preis ändert sich", { priceDialog = true })
                if (income) TextButtonSoft("Nur diesen Monat abweichend", { overrideDialog = true })
                if (overrides.isNotEmpty()) {
                    overrides.toSortedMap().forEach { (k, v) ->
                        Row {
                            Text("Ausnahme ab ${shortDate(LocalDate.parse(k))}: ${Money.format(v)}", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.weight(1f))
                            Text("✕", Modifier.clickable { overrides = overrides - k }, color = c.muted)
                        }
                    }
                }
            }
        }

        val cents = Money.parse(amount)
        val valid = name.isNotBlank() && (prices.isNotEmpty() && existing != null && prices.size > 1 || (cents ?: 0) > 0)
        BigButton(if (existing == null) "Speichern" else "Änderungen speichern", enabled = valid, modifier = Modifier.fillMaxWidth(), onClick = {
            val finalPrices = when {
                existing == null -> listOf(PriceStep(firstDue, cents!!))
                prices.size <= 1 -> listOf(PriceStep(prices.firstOrNull()?.from ?: firstDue, cents ?: prices.first().amount))
                else -> prices
            }.sortedBy { it.from }
            // Der erste Preis gilt ab der ersten Fälligkeit, auch wenn die nachträglich früher gelegt wurde.
            val fixedFirst = finalPrices.mapIndexed { i, p -> if (i == 0 && p.from.isAfter(firstDue)) p.copy(from = firstDue) else p }
            val r = Recurring(
                id = existing?.id ?: Store.newId(), name = name.trim(), categoryId = categoryId, income = income,
                intervalMonths = interval, firstDue = firstDue, endDate = endDate,
                spread = !income && interval > 1 && spread, prices = fixedFirst, salary = income && salary, overrides = overrides,
            )
            Store.update { data ->
                val others = data.recurring.filterNot { it.id == r.id }
                // Es gibt nur ein Gehalt, das den Monat startet.
                val cleaned = if (r.salary) others.map { it.copy(salary = false) } else others
                // Die erkannte Zahlung ist damit erledigt, sie zählt jetzt als Fixkosten.
                val dets = if (det == null) data.detected else data.detected.map {
                    if (it.id == det.id) it.copy(status = DetectedStatus.MERGED, mergeNote = "Als Fixkosten „${r.name}“ angelegt") else it
                }
                data.copy(recurring = cleaned + r, detected = dets)
            }
            if (det != null) Notify.cancel(ctx, det.id)
            CashdyWidget.updateAll(ctx)
            ui.say("Gespeichert")
            ui.back()
        })
        if (existing != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existing.endDate == null || (Recurrence.isLoan(existing) && existing.endDate.isAfter(today))) TextButtonSoft(if (Recurrence.isLoan(existing)) "Schon abbezahlt" else "Gekündigt ab heute", {
                    Store.update { data -> data.copy(recurring = data.recurring.map { if (it.id == existing.id) it.copy(endDate = today) else it }) }
                    ui.say(if (Recurrence.isLoan(existing)) "Abbezahlt, super!" else "Beendet. Ab jetzt rechne ich es nicht mehr mit."); ui.back()
                })
                TextButtonSoft(if (confirmDelete) "Wirklich löschen?" else "Löschen", {
                    if (!confirmDelete) confirmDelete = true else {
                        Store.update { data -> data.copy(recurring = data.recurring.filterNot { it.id == existing.id }) }
                        ui.say("Gelöscht"); ui.back()
                    }
                })
            }
            Hint("Tipp: „Gekündigt“ behält die alten Monate korrekt. Löschen entfernt es auch aus der Vergangenheit.")
        }
        Spacer(Modifier.height(40.dp))
    }

    when (pick) {
        "first" -> DatePick(firstDue, { pick = null }) { firstDue = it }
        "end" -> DatePick(endDate ?: today, { pick = null }) { endDate = it }
    }

    if (priceDialog) PriceChangeDialog(income, onDismiss = { priceDialog = false }) { from, cents ->
        val base = prices.ifEmpty { listOfNotNull(Money.parse(amount)?.let { PriceStep(firstDue, it) }) }
        prices = (base.filter { it.from != from } + PriceStep(from, cents)).sortedBy { it.from }
        priceDialog = false
    }
    if (overrideDialog) {
        val p = Periods.periodFor(today, d.settings)
        PriceChangeDialog(income, single = true, onDismiss = { overrideDialog = false }) { _, cents ->
            overrides = overrides + (p.key to cents)
            overrideDialog = false
        }
    }
}

private fun amountPerMonth(amount: String, interval: Int): String =
    Money.parse(amount)?.takeIf { it > 0 }?.let { Money.format(it / interval) + " pro Monat" } ?: "einen Teil"

@Composable
private fun PriceChangeDialog(income: Boolean, single: Boolean = false, onDismiss: () -> Unit, onSave: (LocalDate, Long) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var from by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1).plusMonths(1)) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (single) "Diesen Monat abweichend" else if (income) "Neues Gehalt" else "Neuer Preis") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyField(amount, { amount = it }, "Neuer Betrag in €")
                if (!single) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("gilt ab", Modifier.weight(1f))
                    DateChip(from, { pick = true })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { Money.parse(amount)?.takeIf { it > 0 }?.let { onSave(from, it) } }) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
    if (pick) DatePick(from, { pick = false }) { from = it }
}

private data class Template(val name: String, val cat: String, val months: Int, val amount: Long = 0)

private val templates = listOf(
    Template("Miete", DefaultCategories.HOME, 1),
    Template("Strom", DefaultCategories.ENERGY, 1),
    Template("Handy", DefaultCategories.PHONE, 1),
    Template("Internet", DefaultCategories.PHONE, 1),
    Template("Netflix", DefaultCategories.SUBS, 1),
    Template("Spotify", DefaultCategories.SUBS, 1),
    Template("Rundfunkbeitrag", DefaultCategories.SUBS, 3, 5508),
    Template("Kfz-Versicherung", DefaultCategories.INSURANCE, 12),
    Template("Kfz-Steuer", DefaultCategories.CAR, 12),
    Template("Haftpflicht", DefaultCategories.INSURANCE, 12),
    Template("Fitnessstudio", DefaultCategories.FUN, 1),
)
