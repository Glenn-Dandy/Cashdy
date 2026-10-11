package de.kewl.cashdy.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.kewl.cashdy.data.DefaultCategories
import de.kewl.cashdy.data.Entry
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.data.Source
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.data.category
import de.kewl.cashdy.domain.Merchants
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.Recurrence
import de.kewl.cashdy.importer.CsvImport
import de.kewl.cashdy.importer.CsvResult
import de.kewl.cashdy.importer.CsvRow
import de.kewl.cashdy.importer.RecurringDetector
import de.kewl.cashdy.importer.RecurringSuggestion
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.components.BigButton
import de.kewl.cashdy.ui.components.Card
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.Mascot
import de.kewl.cashdy.ui.components.Mood
import de.kewl.cashdy.ui.components.ScreenHeader
import de.kewl.cashdy.ui.components.SectionTitle
import de.kewl.cashdy.ui.components.shortDate
import de.kewl.cashdy.ui.theme.C
import de.kewl.cashdy.widget.CashdyWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class RowState(val row: CsvRow, val note: String?, val defaultOn: Boolean)

@Composable
fun ImportScreen(initial: Uri?) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val c = C.colors
    var uri by remember { mutableStateOf(initial) }
    var result by remember { mutableStateOf<CsvResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<RecurringSuggestion>>(emptyList()) }
    val pickedSugg = remember { mutableStateMapOf<String, Boolean>() }
    val pickedRows = remember { mutableStateMapOf<Int, Boolean>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) uri = u }

    LaunchedEffect(uri) {
        val u = uri ?: return@LaunchedEffect
        error = null
        val r = withContext(Dispatchers.IO) {
            runCatching { CsvImport.parse(CsvImport.decode(ctx.contentResolver.openInputStream(u)!!.use { it.readBytes() })) }
        }
        r.onSuccess { res ->
            result = res
            suggestions = RecurringDetector.detect(res.rows, Store.current.recurring)
            pickedSugg.clear(); suggestions.forEach { pickedSugg[it.recurring.id] = true }
            pickedRows.clear()
        }.onFailure { error = it.message ?: "Datei konnte nicht gelesen werden" }
    }

    val d = Store.current
    val res = result
    val states: List<RowState> = remember(res, suggestions, pickedSugg.toMap()) {
        if (res == null) emptyList() else res.rows.map { row ->
            val dup = CsvImport.isDuplicate(row, d.entries)
            val recExisting = d.recurring.firstOrNull { RecurringDetector.belongsTo(row, it, Recurrence::priceAt) }
            val recNew = suggestions.firstOrNull { s -> pickedSugg[s.recurring.id] == true && row in s.rows }
            val note = when {
                dup -> "schon eingetragen"
                recExisting != null -> "gehört zu „${recExisting.name}“"
                recNew != null -> "wird regelmäßig: ${recNew.recurring.name}"
                row.amount > 0 -> "Eingang"
                row.counterparty.contains("paypal", true) -> "PayPal-Abbuchung"
                else -> null
            }
            RowState(row, note, note == null || note == "PayPal-Abbuchung" && d.entries.none { it.method == PayMethod.PAYPAL })
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenHeader("Kontoauszug", onBack = { ui.back() }) }
        if (res == null) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                    Mascot(Mood.HAPPY, size = 100.dp)
                }
                Card {
                    Text("So geht's", style = MaterialTheme.typography.titleMedium, color = c.fg)
                    Hint("1. Im Online-Banking (DKB, Sparkasse ...) oder bei PayPal die Umsätze als CSV exportieren, am besten die letzten 3 bis 12 Monate.")
                    Hint("2. Datei hier auswählen oder aus der Download-App an Cashdy teilen.")
                    Hint("3. Ich finde regelmäßige Zahlungen (Miete, Netflix, Gehalt) und schlage sie als Fixkosten vor. Einzelne Ausgaben kannst du mit übernehmen.")
                }
                Spacer(Modifier.height(12.dp))
                BigButton("📄 CSV-Datei wählen", { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "*/*")) }, Modifier.fillMaxWidth())
                error?.let { Text("😕 $it", color = c.warn, modifier = Modifier.padding(top = 10.dp)) }
            }
            return@LazyColumn
        }

        item {
            Card(color = c.soft2) {
                Text("${res.source}: ${res.rows.size} Umsätze gelesen", style = MaterialTheme.typography.titleMedium, color = c.fg)
                if (res.skipped > 0) Hint("${res.skipped} Zeilen übersprungen (Aufladungen, Stornos, andere Währung)")
            }
        }
        if (suggestions.isNotEmpty()) {
            item { SectionTitle("🔁 Regelmäßig gefunden (${suggestions.size})") }
            items(suggestions, key = { it.recurring.id }) { s ->
                val r = s.recurring
                val on = pickedSugg[r.id] == true
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).clickable { pickedSugg[r.id] = !on }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(on, { pickedSugg[r.id] = it }, colors = CheckboxDefaults.colors(checkedColor = c.primary))
                    Column(Modifier.weight(1f)) {
                        Text("${d.category(r.categoryId)?.emoji ?: ""} ${r.name}" + if (r.salary) " (Gehalt)" else "", style = MaterialTheme.typography.titleSmall, color = c.fg)
                        Text(
                            Recurrence.intervalLabel(r.intervalMonths) + " · ${s.rows.size}× gefunden" +
                                if (r.prices.size > 1) " · Preis geändert ${r.prices.joinToString(" → ") { Money.format(it.amount) }}" else "",
                            style = MaterialTheme.typography.bodySmall, color = c.muted,
                        )
                    }
                    Text(Money.format(r.prices.last().amount), style = MaterialTheme.typography.titleSmall, color = if (r.income) c.good else c.fg)
                }
            }
        }
        item { SectionTitle("Einzelne Umsätze") }
        item { Hint("Vorausgewählt ist nur, was noch nicht da ist und nicht zu regelmäßigen Zahlungen gehört.") }
        items(states.withIndex().toList(), key = { it.index }) { (i, st) ->
            val on = pickedRows[i] ?: st.defaultOn
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).clickable { pickedRows[i] = !on }.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(on, { pickedRows[i] = it }, colors = CheckboxDefaults.colors(checkedColor = c.primary))
                Column(Modifier.weight(1f)) {
                    Text(Merchants.pretty(st.row.counterparty).ifBlank { "?" }, style = MaterialTheme.typography.bodyLarge, color = c.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(shortDate(st.row.date) + (st.note?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Text(Money.format(st.row.amount, withSign = true), style = MaterialTheme.typography.titleSmall, color = if (st.row.amount > 0) c.good else c.fg)
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            val nRec = suggestions.count { pickedSugg[it.recurring.id] == true }
            val nRows = states.indices.count { pickedRows[it] ?: states[it].defaultOn }
            BigButton("Übernehmen ($nRec regelmäßig, $nRows einzeln)", modifier = Modifier.fillMaxWidth(), enabled = nRec + nRows > 0, onClick = {
                val newRec = suggestions.filter { pickedSugg[it.recurring.id] == true }.map { it.recurring }
                val newEntries = states.indices.filter { pickedRows[it] ?: states[it].defaultOn }.map { idx ->
                    val row = states[idx].row
                    val cash = row.method == PayMethod.CASH && row.amount < 0
                    Entry(
                        id = Store.newId(), amount = kotlin.math.abs(row.amount), date = row.date,
                        kind = when { cash -> EntryKind.CASH_WITHDRAWAL; row.amount > 0 -> EntryKind.INCOME; else -> EntryKind.EXPENSE },
                        categoryId = when {
                            cash -> null
                            row.amount > 0 -> DefaultCategories.INCOME_OTHER
                            else -> Merchants.suggestCategory(d, row.counterparty + " " + row.purpose) ?: DefaultCategories.OTHER
                        },
                        merchant = Merchants.pretty(row.counterparty), note = "", method = row.method, source = Source.CSV,
                    )
                }
                Store.update { data ->
                    val cleaned = if (newRec.any { it.salary }) data.recurring.map { it.copy(salary = false) } else data.recurring
                    data.copy(recurring = cleaned + newRec, entries = data.entries + newEntries)
                }
                CashdyWidget.updateAll(ctx)
                ui.say("✅ ${newRec.size} regelmäßig, ${newEntries.size} einzeln übernommen")
                ui.back()
            })
            Spacer(Modifier.height(40.dp))
        }
    }
}
