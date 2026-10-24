package de.kewl.cashdy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.kewl.cashdy.data.BankCheck
import de.kewl.cashdy.data.DefaultCategories
import de.kewl.cashdy.data.Entry
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.data.category
import de.kewl.cashdy.domain.AccountCheck
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.ui.AddRequest
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.Route
import de.kewl.cashdy.ui.components.BigButton
import de.kewl.cashdy.ui.components.Card
import de.kewl.cashdy.ui.components.EntryRow
import de.kewl.cashdy.ui.components.Group
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.Mascot
import de.kewl.cashdy.ui.components.MoneyField
import de.kewl.cashdy.ui.components.Mood
import de.kewl.cashdy.ui.components.ScreenHeader
import de.kewl.cashdy.ui.components.SettingSwitch
import de.kewl.cashdy.ui.components.ValueRow
import de.kewl.cashdy.ui.theme.C
import de.kewl.cashdy.widget.CashdyWidget
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Kontostand prüfen: echten Kontostand eintippen, Cashdy sagt, ob es passt. Wenn nicht, schlägt
 * es Einträge vor, die genau die Differenz erklären, oder gleicht sie aus.
 */
@Composable
fun BankCheckScreen() {
    val ui = LocalUi.current
    val d by Store.data.collectAsState()
    val c = C.colors
    val today = LocalDate.now()
    var text by remember { mutableStateOf("") }
    var minus by remember { mutableStateOf(false) }
    val real = Money.parse(text)?.let { if (minus) -it else it }
    val anchor = AccountCheck.anchor(d)
    val expected = AccountCheck.expected(d, today)
    val day = DateTimeFormatter.ofPattern("d.M.")

    fun saveCheck(balance: Long) {
        Store.update { it.copy(settings = it.settings.copy(bankCheck = BankCheck(today, balance, System.currentTimeMillis()))) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Kontostand prüfen", onBack = { ui.back() })
        Card {
            Text("Schau in deine Banking-App: Wie viel ist gerade auf dem Konto?", style = MaterialTheme.typography.bodyLarge, color = c.fg)
            Spacer(Modifier.height(10.dp))
            MoneyField(text, { text = it }, "Kontostand jetzt in €")
            SettingSwitch("Konto ist im Minus", "Zum Beispiel im Dispo", minus) { minus = it }
        }
        Hint(
            when {
                anchor == null -> "Ich kenne deinen Kontostand noch nicht. Ich merke ihn mir und sage dir beim nächsten Mal, ob alles passt."
                anchor.fromStart -> "Ich rechne ab dem ${anchor.date.format(day)}, da hast du mit Kontostand angefangen. Bargeld zählt hier nicht mit."
                else -> "Ich rechne ab deiner letzten Prüfung am ${anchor.date.format(day)}. Bargeld zählt hier nicht mit."
            }
        )

        if (real != null && (anchor == null || expected == null)) {
            BigButton("Kontostand merken", {
                saveCheck(real)
                ui.say("Gemerkt!")
                ui.back()
            }, Modifier.fillMaxWidth())
        } else if (real != null && expected != null) {
            val diff = expected - real
            if (kotlin.math.abs(diff) < 100) {
                Card(color = c.soft2) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Mascot(Mood.CHEER, size = 56.dp, animate = false)
                        Text("Passt! Dein Konto und Cashdy stimmen überein.", style = MaterialTheme.typography.titleMedium, color = c.fg, modifier = Modifier.weight(1f))
                    }
                    if (diff != 0L) {
                        Spacer(Modifier.height(6.dp))
                        Text("Nur ${Money.format(kotlin.math.abs(diff))} Unterschied, das sind Kleinigkeiten.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    }
                }
                BigButton("Fertig", {
                    saveCheck(real)
                    ui.say("Super, alles im Blick!")
                    ui.back()
                }, Modifier.fillMaxWidth())
            } else {
                Result(diff, expected, onRemember = { saveCheck(real) })
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Result(diff: Long, expected: Long, onRemember: () -> Unit) {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val d by Store.data.collectAsState()
    val c = C.colors
    val less = diff > 0
    val amount = kotlin.math.abs(diff)
    val matches = AccountCheck.matches(d, diff)

    Card(color = c.soft3) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Mascot(Mood.SKEPTIC, size = 56.dp, animate = false)
            Text(
                "Auf dem Konto sind ${Money.format(amount)} ${if (less) "weniger" else "mehr"}, als ich dachte.",
                style = MaterialTheme.typography.titleMedium, color = c.fg, modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("Laut mir müssten es ${Money.format(expected)} sein.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }

    matches.forEach { m ->
        when (m) {
            is AccountCheck.Match.WrongMethod -> {
                Text(
                    if (less) "Das passt genau zu diesem Eintrag. Er steht auf bar, ging er vielleicht vom Konto?"
                    else "Das passt genau zu diesem Eintrag. Hast du den vielleicht bar bezahlt?",
                    style = MaterialTheme.typography.bodyMedium, color = c.fg, modifier = Modifier.padding(horizontal = 4.dp),
                )
                Group { EntryRow(m.entry, d.category(m.entry.categoryId), showDay = true) { ui.openAdd(AddRequest(editId = m.entry.id)) } }
                BigButton(if (less) "Ja, ging vom Konto" else "Ja, war bar", {
                    val method = if (less) PayMethod.CARD else PayMethod.CASH
                    Store.update { data -> data.copy(entries = data.entries.map { if (it.id == m.entry.id) it.copy(method = method) else it }) }
                    CashdyWidget.updateAll(ctx)
                    ui.say("Geändert")
                }, Modifier.fillMaxWidth())
            }
            is AccountCheck.Match.Pending -> {
                Text("Diese erkannte Zahlung wartet noch darauf, dass du sie einträgst:", style = MaterialTheme.typography.bodyMedium, color = c.fg, modifier = Modifier.padding(horizontal = 4.dp))
                Group { ValueRow(m.detected.merchant.ifBlank { m.detected.sourceName }, Money.format(m.detected.amount), note = m.detected.sourceName, onClick = { ui.go(Route.Inbox(m.detected.id)) }) }
                BigButton("Ansehen", { ui.go(Route.Inbox(m.detected.id)) }, Modifier.fillMaxWidth())
            }
        }
    }

    if (matches.isEmpty()) {
        Text(
            if (less) "Vielleicht fehlt eine Ausgabe, zum Beispiel eine Lastschrift, Überweisung oder PayPal-Zahlung. Schau im Kontoauszug nach Beträgen, die du nicht eingetragen hast."
            else "Vielleicht fehlt eine Einnahme, oder eine Abbuchung ist noch nicht durch.",
            style = MaterialTheme.typography.bodyMedium, color = c.fg, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    BigButton(
        if (less) "Ausgabe nachtragen" else "Einnahme nachtragen",
        { ui.openAdd(AddRequest(amount = amount, kind = if (less) EntryKind.EXPENSE else EntryKind.INCOME)) },
        Modifier.fillMaxWidth(), color = c.soft2, textColor = c.fg,
    )
    BigButton("Weiß nicht, ausgleichen", {
        Store.update { data ->
            data.copy(entries = data.entries + Entry(
                id = Store.newId(), amount = amount, date = LocalDate.now(),
                kind = if (less) EntryKind.EXPENSE else EntryKind.INCOME,
                categoryId = if (less) DefaultCategories.OTHER else DefaultCategories.INCOME_OTHER,
                merchant = "Kontoabgleich", note = "nicht zugeordnet", method = PayMethod.CARD,
            ))
        }
        onRemember()
        CashdyWidget.updateAll(ctx)
        ui.say("Ausgeglichen, jetzt stimmt es wieder")
        ui.back()
    }, Modifier.fillMaxWidth(), color = c.soft2, textColor = c.fg)
    Hint("„Ausgleichen“ trägt die Differenz als „Kontoabgleich“ ein, damit „Noch frei“ wieder stimmt.")
}
