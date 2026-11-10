package de.kewl.cashdy.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.components.RowTitle
import de.kewl.cashdy.ui.components.Group
import de.kewl.cashdy.ui.components.GroupDivider
import de.kewl.cashdy.ui.components.ScreenHeader
import de.kewl.cashdy.ui.components.TextButtonSoft
import de.kewl.cashdy.ui.theme.C

/** App-Infos von Cashdy, dort sitzt "Eingeschränkte Einstellungen zulassen". */
fun openAppInfo(ctx: Context) {
    ctx.startActivity(
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
fun HelpScreen() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val listenerOn = rememberListenerEnabled()
    // Ist die Erkennung noch aus, steht die Anleitung dazu gleich offen.
    var open by remember { mutableStateOf(if (listenerOn) -1 else 0) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader("Hilfe", onBack = { ui.back() })
        Group {
            Topic("Zahlungserkennung einschalten", open == 0, { open = if (open == 0) -1 else 0 }) {
                Para(
                    "Bei Apps, die nicht aus dem Play Store kommen, sperrt Android diesen Schalter erst einmal. So geht es trotzdem:"
                )
                Step(1, "Tippe auf „Zugriff öffnen“ und versuch, Cashdy einzuschalten. Die Meldung „Eingeschränkte Einstellung“ einfach schließen.")
                Step(2, "Tippe auf „App-Infos öffnen“. Oben rechts das Menü mit den drei Punkten öffnen und „Eingeschränkte Einstellungen zulassen“ wählen. Keine drei Punkte da? App-Infos schließen, Schritt 1 wiederholen, App-Infos neu öffnen.")
                Step(3, "Noch einmal „Zugriff öffnen“ und Cashdy einschalten. Jetzt klappt es.")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButtonSoft("Zugriff öffnen", { openListenerSettings(ctx) })
                    TextButtonSoft("App-Infos öffnen", { openAppInfo(ctx) })
                }
                if (listenerOn) Para("Bei dir ist die Erkennung schon an.")
            }
            GroupDivider()
            Topic("Was heißt „Noch frei“?", open == 1, { open = if (open == 1) -1 else 1 }) {
                Para("Einnahmen minus Fixkosten, Rücklage und Ausgaben. Das kannst du ohne schlechtes Gewissen ausgeben.")
                Para("„Heute drin“ ist das geteilt durch die restlichen Tage.")
                Para("Im ersten Monat starte ich mit deinem Kontostand statt mit dem Gehalt. Ab dem nächsten Monat geht es normal weiter.")
                Para("Ob alles stimmt, prüfst du unter Mehr › Kontostand prüfen.")
                Para("Passt gar nichts mehr? Dort unter „Neu anfangen“ rechne ich ab heute mit deinem echten Kontostand. Es wird nichts gelöscht.")
            }
            GroupDivider()
            Topic("Wie funktioniert die Rücklage?", open == 2, { open = if (open == 2) -1 else 2 }) {
                Para("Seltene Kosten wie die Kfz-Versicherung verteile ich auf die Monate: 480 € im Jahr sind 40 € pro Monat für den Topf.")
                Para("Kommt die Rechnung, zahlt sie der Topf. Das Geld bleibt dafür einfach auf dem Konto.")
                Para("Reicht der Topf noch nicht, zählt der Rest im Monat der Rechnung.")
            }
            GroupDivider()
            Topic("Sparen und Tagesgeld", open == 7, { open = if (open == 7) -1 else 7 }) {
                Para("Geld aufs Sparkonto: Plus, dann „Sparen“. Geht von „Noch frei“ ab, zählt aber nicht als ausgegeben.")
                Para("Am Monatsende sage ich dir, wie viel du sparen kannst. Die Rücklage bleibt auf dem Konto.")
            }
            GroupDivider()
            Topic("Kredite und Raten", open == 6, { open = if (open == 6) -1 else 6 }) {
                Para("Rate als Fixkosten mit Kategorie „Kredite & Raten“ eintragen, dazu die letzte Rate aus dem Vertrag.")
                Para("Unter „Fest“ siehst du dann, wie viele Raten noch fehlen.")
            }
            GroupDivider()
            Topic("Bargeld", open == 3, { open = if (open == 3) -1 else 3 }) {
                Para("Portemonnaie führen: Abheben füllt es, Barzahlungen leeren es.")
                Para("Einfach-Modus: Abheben zählt sofort als ausgegeben. Umstellen unter Mehr › Allgemein.")
            }
            GroupDivider()
            Topic("Kassenbon fotografieren", open == 4, { open = if (open == 4) -1 else 4 }) {
                Para("Bon glatt hinlegen, gut ausleuchten, Summe gut lesbar fotografieren.")
                Para("Falsche Summe? Tipp auf einen der anderen Beträge vom Bon.")
            }
            GroupDivider()
            Topic("Kontoauszug einlesen", open == 5, { open = if (open == 5) -1 else 5 }) {
                Para("Umsätze als CSV herunterladen, dann unter „Fest“ auf Plus und „Aus Kontoauszug finden“. Ich schlage dir regelmäßige Zahlungen als Fixkosten vor.")
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Topic(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val c = C.colors
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = RowTitle, color = c.fg, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = c.muted)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

@Composable
private fun Para(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = C.colors.fg)
}

@Composable
private fun Step(n: Int, text: String) {
    Row {
        Text("$n.", style = MaterialTheme.typography.titleSmall, color = C.colors.primary, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = C.colors.fg)
    }
}
