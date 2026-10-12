package de.kewl.cashdy.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.ui.components.MascotEvents
import de.kewl.cashdy.ui.components.RowTitle
import de.kewl.cashdy.ui.components.dayLabel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import de.kewl.cashdy.data.Entry
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.data.Source
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.domain.Merchants
import de.kewl.cashdy.ui.AddRequest
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.components.AmountInput
import de.kewl.cashdy.ui.components.BigButton
import de.kewl.cashdy.ui.components.DatePick
import de.kewl.cashdy.ui.components.Keypad
import de.kewl.cashdy.ui.components.Pill
import de.kewl.cashdy.ui.theme.C
import de.kewl.cashdy.widget.CashdyWidget
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(req: AddRequest, onDismiss: () -> Unit, onScan: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val d = Store.current
    val editing = req.editId?.let { id -> d.entries.firstOrNull { it.id == id } }
    val c = C.colors

    var kind by remember { mutableStateOf(editing?.kind ?: req.kind) }
    var amountText by remember { mutableStateOf(AmountInput.fromCents(editing?.let { kotlin.math.abs(it.amount) } ?: req.amount)) }
    var merchant by remember { mutableStateOf(editing?.merchant ?: req.merchant) }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var date by remember { mutableStateOf(editing?.date ?: req.date) }
    var categoryId by remember { mutableStateOf(editing?.categoryId ?: req.categoryId) }
    var categoryTouched by remember { mutableStateOf(editing != null || req.categoryId != null) }
    val lastMethod = d.entries.lastOrNull { it.source == Source.MANUAL && it.kind == EntryKind.EXPENSE && it.method != PayMethod.TRANSFER }?.method ?: PayMethod.CARD
    // PayPal steht nicht mehr zur Wahl: für die Rechnung zählt nur bar oder nicht bar.
    var method by remember { mutableStateOf(editing?.method ?: req.method ?: lastMethod.takeIf { it == PayMethod.CASH } ?: PayMethod.CARD) }
    var pickDate by remember { mutableStateOf(false) }
    var catMenu by remember { mutableStateOf(false) }
    var keepReceipt by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val cashKind = kind == EntryKind.CASH_WITHDRAWAL || kind == EntryKind.CASH_ADJUST || kind == EntryKind.CASH_START
    val catType = if (kind == EntryKind.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    val cats = remember(kind) {
        val since = LocalDate.now().minusDays(90)
        val usage = d.entries.filter { it.date.isAfter(since) }.groupingBy { it.categoryId }.eachCount()
        d.categories.filter { it.type == catType && (it.id != de.kewl.cashdy.data.DefaultCategories.CASH_UNKNOWN) }
            .sortedWith(compareByDescending<de.kewl.cashdy.data.Category> { usage[it.id] ?: 0 }.thenBy { it.fixed })
    }

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!cashKind) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Segmented(
                        listOf(EntryKind.EXPENSE to "Ausgabe", EntryKind.INCOME to "Einnahme", EntryKind.REFUND to "Erstattung"),
                        kind, Modifier.weight(1f),
                    ) { k ->
                        kind = k
                        when (k) {
                            EntryKind.INCOME -> categoryId = de.kewl.cashdy.data.DefaultCategories.INCOME_OTHER
                            else -> if (!categoryTouched || cats.none { it.id == categoryId }) categoryId = null
                        }
                    }
                    if (editing == null) Box(
                        Modifier.size(40.dp).clip(CircleShape).background(c.bg).clickable(onClick = onScan),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.PhotoCamera, "Kassenbon", tint = c.fg, modifier = Modifier.size(22.dp)) }
                }
            } else {
                Text(
                    when (kind) { EntryKind.CASH_WITHDRAWAL -> "Geld abgehoben"; EntryKind.CASH_START -> "Bargeld-Startbestand"; else -> "Bargeld-Abgleich" },
                    style = MaterialTheme.typography.titleLarge, color = c.fg,
                )
            }

            // Betrag
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                Text(AmountInput.display(amountText), style = MaterialTheme.typography.displayLarge, fontSize = 50.sp, color = if (amountText.isEmpty()) c.muted else c.fg)
                Text(" €", style = MaterialTheme.typography.headlineMedium, color = c.muted, modifier = Modifier.padding(bottom = 8.dp))
            }

            // Vom Bon: die anderen Beträge zum Antippen, falls die Summe nicht stimmt.
            if (req.candidates.size > 1) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Vom Bon:", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    req.candidates.forEach { v ->
                        Pill(Money.format(v), AmountInput.cents(amountText) == v, { amountText = AmountInput.fromCents(v) })
                    }
                }
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, c.line, RoundedCornerShape(18.dp))) {
                if (!cashKind) {
                    Box {
                        val cat = cats.firstOrNull { it.id == categoryId }
                        FieldRow("Kategorie", { catMenu = true }) {
                            Text(cat?.let { "${it.emoji} ${it.name}" } ?: "Wählen", style = RowTitle, color = if (cat == null) c.muted else c.fg, modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.muted)
                        }
                        DropdownMenu(catMenu, { catMenu = false }, containerColor = c.surface, modifier = Modifier.heightIn(max = 360.dp)) {
                            cats.forEach { ct ->
                                DropdownMenuItem(
                                    text = { Text("${ct.emoji}  ${ct.name}", style = MaterialTheme.typography.bodyLarge, color = c.fg) },
                                    onClick = { categoryId = ct.id; categoryTouched = true; catMenu = false },
                                )
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = c.line)
                    FieldRow(if (kind == EntryKind.INCOME) "Von wem?" else "Wo?") {
                        InlineField(merchant, { t ->
                            merchant = t
                            if (!categoryTouched && kind == EntryKind.EXPENSE) Merchants.suggestCategory(d, t)?.let { categoryId = it }
                        }, if (kind == EntryKind.INCOME) "z. B. Oma" else "z. B. REWE", Modifier.weight(1f))
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = c.line)
                }
                FieldRow("Wann?", { pickDate = true }) {
                    Text(dayLabel(date), style = RowTitle, color = c.fg, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = c.muted)
                }
                if (kind == EntryKind.EXPENSE || kind == EntryKind.REFUND) {
                    HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = c.line)
                    // Eine erkannte PayPal-Zahlung bleibt PayPal, wenn der Schalter wieder ausgeht.
                    val notCash = editing?.method?.takeIf { it != PayMethod.CASH } ?: PayMethod.CARD
                    FieldRow("Bar bezahlt", { method = if (method == PayMethod.CASH) notCash else PayMethod.CASH }) {
                        Spacer(Modifier.weight(1f))
                        Switch(
                            method == PayMethod.CASH, { method = if (it) PayMethod.CASH else notCash },
                            colors = SwitchDefaults.colors(checkedTrackColor = c.primary, uncheckedTrackColor = c.line, uncheckedThumbColor = c.muted, uncheckedBorderColor = c.line),
                        )
                    }
                }
                if (!cashKind) {
                    HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = c.line)
                    FieldRow("Notiz") { InlineField(note, { note = it }, "optional", Modifier.weight(1f)) }
                }
            }
            if (req.fromScan && req.receiptUri != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { keepReceipt = !keepReceipt }) {
                    Checkbox(keepReceipt, { keepReceipt = it })
                    Text("Bon-Foto behalten", style = MaterialTheme.typography.bodyMedium, color = c.fg)
                }
            }

            Keypad({ k -> amountText = AmountInput.press(amountText, k) })

            val cents = AmountInput.cents(amountText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editing != null) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(c.surface).clickable { confirmDelete = true }.padding(horizontal = 18.dp, vertical = 15.dp),
                    ) { Text(if (confirmDelete) "Sicher?" else "Löschen", style = MaterialTheme.typography.titleMedium, color = c.warn) }
                    if (confirmDelete) {
                        BigButton("Ja, löschen", {
                            Store.update { it.copy(entries = it.entries.filterNot { e -> e.id == editing.id }) }
                            CashdyWidget.updateAll(ctx)
                            ui.say("Gelöscht")
                            onDismiss()
                        }, color = c.warn)
                    }
                }
                if (!confirmDelete) BigButton(
                    if (editing != null) "Speichern" else "Eintragen",
                    enabled = cents != 0L,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val receiptPath = if (keepReceipt && req.receiptUri != null) runCatching {
                            val dir = File(ctx.filesDir, "receipts").apply { mkdirs() }
                            val f = File(dir, "bon-${System.currentTimeMillis()}.jpg")
                            ctx.contentResolver.openInputStream(req.receiptUri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
                            "receipts/${f.name}"
                        }.getOrNull() else editing?.receipt
                        val finalCat = when {
                            cashKind -> null
                            categoryId != null -> categoryId
                            kind == EntryKind.INCOME -> de.kewl.cashdy.data.DefaultCategories.INCOME_OTHER
                            else -> de.kewl.cashdy.data.DefaultCategories.OTHER
                        }
                        val entry = Entry(
                            id = editing?.id ?: Store.newId(),
                            amount = if (kind == EntryKind.CASH_ADJUST && (editing?.amount ?: 0) < 0) -cents else cents,
                            date = date, kind = kind, categoryId = finalCat,
                            merchant = merchant.trim(), note = note.trim(),
                            method = if (kind == EntryKind.INCOME) (editing?.method ?: PayMethod.TRANSFER) else if (cashKind) PayMethod.CASH else method,
                            source = editing?.source ?: if (req.fromScan) Source.OCR else Source.MANUAL,
                            createdAt = editing?.createdAt ?: System.currentTimeMillis(),
                            receipt = receiptPath,
                        )
                        Store.update { data ->
                            val list = if (editing != null) data.entries.map { if (it.id == entry.id) entry else it } else data.entries + entry
                            val learned = if (kind == EntryKind.EXPENSE && categoryTouched) Merchants.learn(data, entry.merchant, entry.categoryId) else data
                            learned.copy(entries = list)
                        }
                        CashdyWidget.updateAll(ctx)
                        ui.say(if (editing != null) "Gespeichert" else "Eingetragen")
                        if (editing == null) MascotEvents.hop()
                        onDismiss()
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (pickDate) DatePick(date, { pickDate = false }) { date = it }
}

@Composable
private fun Segmented(options: List<Pair<EntryKind, String>>, selected: EntryKind, modifier: Modifier = Modifier, onSelect: (EntryKind) -> Unit) {
    val c = C.colors
    Row(modifier.clip(RoundedCornerShape(50)).background(c.bg).padding(3.dp)) {
        options.forEach { (k, label) ->
            val sel = k == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (sel) c.surface else c.bg)
                    .clickable { onSelect(k) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (sel) c.fg else c.muted, maxLines = 1) }
        }
    }
}

@Composable
private fun FieldRow(label: String, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = C.colors.muted, modifier = Modifier.width(96.dp))
        content()
    }
}

/** Schlichtes Textfeld ohne Rahmen, für die Zeilen im Eingabe-Sheet. */
@Composable
private fun InlineField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = C.colors
    val style = RowTitle.copy(color = c.fg)
    BasicTextField(
        value, onChange, modifier.padding(vertical = 12.dp), singleLine = true, textStyle = style,
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { inner ->
            Box { if (value.isEmpty()) Text(placeholder, style = style.copy(color = c.muted)); inner() }
        },
    )
}
