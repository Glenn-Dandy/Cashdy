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
        if (d.settings.cashMode == CashMode.WALLET) MenuRow("💶", "Portemonnaie", "Gerade ${Money.format(Budget.wallet(d))}") { ui.go(Route.Wallet) }
        MenuRow("📬", "Erkannte Zahlungen", if (pending > 0) "$pending warten auf dich" else "Alles erledigt", badge = pending.takeIf { it > 0 }?.toString()) { ui.go(Route.Inbox()) }
        MenuRow("📸", "Kassenbon scannen", "Summe und Laden automatisch auslesen") { ui.scanChooser = true }
        MenuRow("📄", "Kontoauszug importieren", "CSV von DKB, PayPal & Co.") { ui.go(Route.Import()) }

        SectionTitle("Einstellungen")
        MenuRow("⚙️", "Allgemein", "Monatsstart, Bargeld, Erinnerungen") { ui.go(Route.Settings) }
        MenuRow("✨", "Zahlungserkennung", if (PaymentListenerService.isEnabled(ctx)) "Aktiv" else "Aus") { ui.go(Route.Detection) }
        MenuRow("🏷️", "Kategorien") { ui.go(Route.Categories) }
        MenuRow("☁️", "Sicherung", "Datei oder Nextcloud") { ui.go(Route.Backup) }
        MenuRow("🪙", "Über Cashdy") { ui.go(Route.About) }
        Spacer(Modifier.height(40.dp))
    }
}
