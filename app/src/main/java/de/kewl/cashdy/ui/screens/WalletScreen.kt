package de.kewl.cashdy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.kewl.cashdy.data.DefaultCategories
import de.kewl.cashdy.data.Entry
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.data.category
import de.kewl.cashdy.domain.Budget
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.ui.AddRequest
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.components.Card
import de.kewl.cashdy.ui.components.EntryRow
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.Mascot
import de.kewl.cashdy.ui.components.MoneyField
import de.kewl.cashdy.ui.components.Mood
import de.kewl.cashdy.ui.components.Pill
import de.kewl.cashdy.ui.components.ScreenHeader
import de.kewl.cashdy.ui.components.SectionTitle
import de.kewl.cashdy.ui.theme.C
import de.kewl.cashdy.widget.CashdyWidget
import java.time.LocalDate

@Composable
fun WalletScreen() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val d by Store.data.collectAsState()
    val wallet = Budget.wallet(d)
    val c = C.colors
    var check by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Portemonnaie", onBack = { ui.back() })
        Card(color = c.soft1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bargeld laut mir", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    Text(Money.format(wallet), style = MaterialTheme.typography.displayLarge, color = c.fg)
                }
                Mascot(if (wallet >= 0) Mood.HAPPY else Mood.HOPE, size = 70.dp, animate = false)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Abgehoben", true, { ui.openAdd(AddRequest(kind = EntryKind.CASH_WITHDRAWAL)) })
            Pill("Bar bezahlt", false, { ui.openAdd(AddRequest(method = PayMethod.CASH)) })
            Pill("Abgleich", false, { check = true })
        }
        Hint("Abheben ist keine Ausgabe, das Geld wandert nur ins Portemonnaie. Ausgegeben ist es erst, wenn du bar bezahlst. Keine Lust, jeden Kaugummi einzutragen? Mach ab und zu einen Abgleich, den Rest buche ich als „Bargeld, nicht zugeordnet“.")

        val cashEntries = d.entries.filter {
            it.kind in setOf(EntryKind.CASH_WITHDRAWAL, EntryKind.CASH_ADJUST, EntryKind.CASH_START) ||
                it.method == PayMethod.CASH && it.kind != EntryKind.CASH_WITHDRAWAL
        }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.createdAt }).take(40)
        if (cashEntries.isNotEmpty()) {
            SectionTitle("Bewegungen")
            cashEntries.forEach { e -> EntryRow(e, d.category(e.categoryId)) { ui.openAdd(AddRequest(editId = e.id)) } }
        }
        Spacer(Modifier.height(40.dp))
    }

    if (check) {
        var real by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { check = false },
            title = { Text("Wie viel hast du gerade?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zähl kurz nach. Laut mir sind es ${Money.format(wallet)}.")
                    MoneyField(real, { real = it }, "Echtes Bargeld in €")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val actual = Money.parse(real.ifBlank { "0" }) ?: return@TextButton
                    val diff = wallet - actual
                    if (diff != 0L) {
                        Store.update {
                            it.copy(entries = it.entries + Entry(
                                id = Store.newId(), amount = diff, date = LocalDate.now(), kind = EntryKind.CASH_ADJUST,
                                categoryId = DefaultCategories.CASH_UNKNOWN, method = PayMethod.CASH,
                                note = if (diff > 0) "nicht eingetragene Barausgaben" else "mehr Bargeld als gedacht",
                            ))
                        }
                        CashdyWidget.updateAll(ctx)
                    }
                    ui.say(if (diff == 0L) "Passt auf den Cent!" else if (diff > 0) "${Money.format(diff)} als Bargeld-Ausgabe gebucht" else "${Money.format(-diff)} mehr, schön!")
                    check = false
                }) { Text("Abgleichen") }
            },
            dismissButton = { TextButton(onClick = { check = false }) { Text("Abbrechen") } },
        )
    }
}
