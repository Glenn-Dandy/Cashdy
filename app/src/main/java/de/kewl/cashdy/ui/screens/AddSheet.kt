package de.kewl.cashdy.ui.screens

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
import de.kewl.cashdy.ui.components.DateChip
import de.kewl.cashdy.ui.components.DatePick
import de.kewl.cashdy.ui.components.Keypad
import de.kewl.cashdy.ui.components.Pill
import de.kewl.cashdy.ui.components.SoftField
import de.kewl.cashdy.ui.components.TextButtonSoft
import de.kewl.cashdy.ui.components.categoryTint
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
    var method by remember { mutableStateOf(editing?.method ?: req.method ?: lastMethod) }
    var pickDate by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.bg) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!cashKind) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill("Ausgabe", kind == EntryKind.EXPENSE, { kind = EntryKind.EXPENSE; if (!categoryTouched) categoryId = null })
                    Pill("Einnahme", kind == EntryKind.INCOME, { kind = EntryKind.INCOME; categoryId = de.kewl.cashdy.data.DefaultCategories.INCOME_OTHER })
                    Pill("Erstattung", kind == EntryKind.REFUND, { kind = EntryKind.REFUND })
                    Spacer(Modifier.weight(1f))
                    if (editing == null) TextButtonSoft("📷 Bon", onScan)
                }
            } else {
                Text(
                    when (kind) { EntryKind.CASH_WITHDRAWAL -> "🏧 Geld abgehoben"; EntryKind.CASH_START -> "💶 Bargeld-Startbestand"; else -> "💶 Bargeld-Abgleich" },
                    style = MaterialTheme.typography.titleLarge, color = c.fg,
                )
            }

            // Betrag
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                Text(AmountInput.display(amountText), style = MaterialTheme.typography.displayLarge, fontSize = 52.sp, color = if (amountText.isEmpty()) c.muted else c.fg)
                Text(" €", style = MaterialTheme.typography.headlineMedium, color = c.muted, modifier = Modifier.padding(bottom = 8.dp))
            }

            if (kind == EntryKind.EXPENSE || kind == EntryKind.REFUND) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("💳 Karte", method == PayMethod.CARD, { method = PayMethod.CARD })
                    Pill("💶 Bar", method == PayMethod.CASH, { method = PayMethod.CASH })
                    Pill("🅿️ PayPal", method == PayMethod.PAYPAL, { method = PayMethod.PAYPAL })
                }
            }

            if (!cashKind) {
                // Eine wischbare Zeile, meistgenutzte zuerst: hält das Sheet kurz genug, dass
                // Ziffernblock und Speichern-Knopf ohne Scrollen sichtbar bleiben.
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cats.forEach { cat ->
                        val sel = cat.id == categoryId
                        Row(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(if (sel) c.primary else categoryTint(cat, c.dark))
                                .clickable { categoryId = cat.id; categoryTouched = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(cat.emoji, fontSize = 16.sp)
                            Spacer(Modifier.padding(2.dp))
                            Text(cat.name, style = MaterialTheme.typography.labelLarge, color = if (sel) c.onPrimary else c.fg, maxLines = 1)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SoftField(merchant, { t ->
                        merchant = t
                        if (!categoryTouched && kind == EntryKind.EXPENSE) Merchants.suggestCategory(d, t)?.let { categoryId = it }
                    }, if (kind == EntryKind.INCOME) "Von wem?" else "Wo? z. B. REWE", Modifier.weight(1f))
                    DateChip(date, { pickDate = true })
                }
                if (showNote || note.isNotBlank()) SoftField(note, { note = it }, "Notiz")
            } else {
                DateChip(date, { pickDate = true })
            }
            if (!cashKind && !showNote && note.isBlank() || req.fromScan && req.receiptUri != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!cashKind && !showNote && note.isBlank()) TextButtonSoft("＋ Notiz", { showNote = true })
                    Spacer(Modifier.weight(1f))
                    if (req.fromScan && req.receiptUri != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { keepReceipt = !keepReceipt }) {
                            Checkbox(keepReceipt, { keepReceipt = it })
                            Text("Bon-Foto behalten", style = MaterialTheme.typography.bodyMedium, color = c.fg)
                        }
                    }
                }
            }

            Keypad({ k -> amountText = AmountInput.press(amountText, k) })

            val cents = AmountInput.cents(amountText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editing != null) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(c.surface).clickable { confirmDelete = true }.padding(horizontal = 18.dp, vertical = 15.dp),
                    ) { Text(if (confirmDelete) "Sicher?" else "🗑️", style = MaterialTheme.typography.titleMedium, color = c.warn) }
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
                    if (editing != null) "Speichern" else "Eintragen ✓",
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
                        ui.say(if (editing != null) "Gespeichert ✓" else "Eingetragen ✓")
                        onDismiss()
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (pickDate) DatePick(date, { pickDate = false }) { date = it }
}
