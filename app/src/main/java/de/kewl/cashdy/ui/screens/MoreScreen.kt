package de.kewl.cashdy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import de.kewl.cashdy.domain.Periods
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.MoneyField
import de.kewl.cashdy.ui.components.SettingSwitch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.kewl.cashdy.data.CashMode
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.detect.Detection
import de.kewl.cashdy.detect.PaymentListenerService
import de.kewl.cashdy.domain.Budget
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.Route
import de.kewl.cashdy.ui.components.Group
import de.kewl.cashdy.ui.components.GroupDivider
import de.kewl.cashdy.ui.components.MenuRow
import de.kewl.cashdy.ui.components.SectionTitle
import de.kewl.cashdy.ui.theme.C

@Composable
fun MoreScreen() {
    val ui = LocalUi.current
    val d by Store.data.collectAsState()
    val ctx = LocalContext.current
    val pending = Detection.pending(d).size
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Mehr", style = MaterialTheme.typography.headlineMedium, color = C.colors.fg)
        SectionTitle("Einstellungen")
        Group {
            MenuRow("Allgemein", "Monatsstart, Bargeld, Erinnerungen") { ui.go(Route.Settings) }
            GroupDivider()
            MenuRow("Zahlungserkennung", if (PaymentListenerService.isEnabled(ctx)) "Aktiv" else "Aus") { ui.go(Route.Detection) }
            GroupDivider()
            MenuRow("Kategorien") { ui.go(Route.Categories) }
            GroupDivider()
            MenuRow("Sicherung", "Als Datei oder auf deinen Server") { ui.go(Route.Backup) }
            GroupDivider()
            MenuRow("Erkannte Zahlungen", if (pending > 0) "$pending warten auf dich" else "Alles erledigt", badge = pending.takeIf { it > 0 }?.toString()) { ui.go(Route.Inbox()) }
            GroupDivider()
            MenuRow("Kontostand prüfen", "Stimmt Cashdy mit deinem Konto? Oder neu anfangen") { ui.go(Route.BankCheck) }
        }

        SectionTitle("Hilfe")
        Group {
            MenuRow("Hilfe", "Erkennung einschalten, Rücklage, Bargeld, Kassenbon") { ui.go(Route.Help) }
            GroupDivider()
            MenuRow("Über Cashdy", "Version, Feedback, Updates") { ui.go(Route.About) }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/**
 * Neu anfangen: Ab heute rechnet der laufende Monat mit dem echten Kontostand, wie beim
 * Einrichten. Einträge, Fixkosten und Rücklage bleiben.
 */
@Composable
fun RestartDialog(onDone: () -> Unit) {
    val d by Store.data.collectAsState()
    val c = C.colors
    var balance by remember { mutableStateOf("") }
    var minus by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val end = Periods.periodFor(today, d.settings).end.plusDays(1)
    val wallet = d.settings.cashMode == CashMode.WALLET
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = c.surface,
        title = { Text("Neu anfangen", color = c.fg) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Schau in deine Banking-App und trag ein, was jetzt auf dem Konto ist. " +
                        "Bis zum ${end.format(DateTimeFormatter.ofPattern("d.M."))} rechne ich dann mit diesem Betrag, danach wieder ganz normal.",
                    style = MaterialTheme.typography.bodyMedium, color = c.fg,
                )
                MoneyField(balance, { balance = it }, "Kontostand jetzt in €")
                SettingSwitch("Konto ist im Minus", "Zum Beispiel im Dispo", minus) { minus = it }
                Hint(
                    (if (wallet && Budget.wallet(d) > 0) "Dein Bargeld (gerade ${Money.format(Budget.wallet(d))}) rechne ich dazu. " else "") +
                        "Es wird nichts gelöscht, deine Einträge und Fixkosten bleiben. Die Rücklage fängt im nächsten Monat neu an."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = Money.parse(balance) ?: return@TextButton
                // Der Topf fängt im nächsten Monat leer neu an, bis dahin zählt jede Rechnung voll.
                Store.update { it.copy(settings = it.settings.copy(startDay = today, startBalance = if (minus) -v else v, potStart = end, bankCheck = null)) }
                onDone()
            }, enabled = Money.parse(balance) != null) { Text("Neu anfangen") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Abbrechen") } },
    )
}
