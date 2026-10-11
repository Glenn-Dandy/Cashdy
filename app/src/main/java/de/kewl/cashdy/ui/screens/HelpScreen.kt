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
                    "Android schützt den Zugriff auf Benachrichtigungen besonders, wenn eine App nicht aus dem Play Store kommt. " +
                        "Deshalb lässt sich der Schalter beim ersten Mal nicht umlegen, und Play Protect meldet, dass der Zugriff verweigert wurde. So geht es trotzdem:"
                )
                Step(1, "Tippe auf „Zugriff öffnen“ und versuch, Cashdy einzuschalten. Die Meldung „Eingeschränkte Einstellung“ einfach schließen.")
                Step(2, "Tippe auf „App-Infos öffnen“. Oben rechts das Menü mit den drei Punkten öffnen und „Eingeschränkte Einstellungen zulassen“ wählen.")
                Step(3, "Noch einmal „Zugriff öffnen“ und Cashdy einschalten. Jetzt klappt es.")
                Para("Der Menüpunkt in Schritt 2 erscheint erst, nachdem du Schritt 1 einmal versucht hast. Cashdy liest nur die Apps, die du unter Zahlungserkennung freigibst, und schickt nichts ins Internet.")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButtonSoft("Zugriff öffnen", { openListenerSettings(ctx) })
                    TextButtonSoft("App-Infos öffnen", { openAppInfo(ctx) })
                }
                if (listenerOn) Para("Bei dir ist die Erkennung schon an.")
            }
            GroupDivider()
            Topic("Was heißt „Noch frei“?", open == 1, { open = if (open == 1) -1 else 1 }) {
                Para("Deine Einnahmen im Monat, minus Fixkosten, minus Rücklage, minus alles, was du schon ausgegeben hast. Das, was übrig bleibt, kannst du ohne schlechtes Gewissen ausgeben.")
                Para("„Heute drin“ ist dieser Betrag geteilt durch die Tage, die der Monat noch hat.")
            }
            GroupDivider()
            Topic("Wie funktioniert die Rücklage?", open == 2, { open = if (open == 2) -1 else 2 }) {
                Para("Kosten, die nur alle paar Monate kommen (Kfz-Versicherung, Rundfunkbeitrag), werden auf die Monate verteilt. 480 € im Jahr sind 40 € pro Monat, die jeden Monat von „Noch frei“ abgehen und im Topf landen.")
                Para("Kommt die Rechnung, bezahlt sie der Topf. Dein „Noch frei“ bleibt in dem Monat gleich. Das Geld bleibt dafür einfach auf dem Konto liegen.")
                Para("Hast du erst kurz vor der Rechnung angefangen, reicht der Topf noch nicht. Dann zählt der fehlende Rest im Monat der Rechnung, und unter „Fest“ siehst du vorher, wie viel fehlt.")
            }
            GroupDivider()
            Topic("Bargeld", open == 3, { open = if (open == 3) -1 else 3 }) {
                Para("Portemonnaie führen: Abheben füllt dein Portemonnaie, Barzahlungen leeren es. Zählt als ausgegeben, sobald du bar bezahlst.")
                Para("Einfach-Modus: Abheben zählt sofort als ausgegeben, Barzahlungen musst du nicht eintragen. Umstellen kannst du das unter Mehr › Allgemein.")
            }
            GroupDivider()
            Topic("Kassenbon fotografieren", open == 4, { open = if (open == 4) -1 else 4 }) {
                Para("Bon glatt hinlegen, gut ausleuchten und so fotografieren, dass die Summe gut lesbar ist. Ich lese Summe, Laden, Datum und ob bar oder mit Karte bezahlt wurde.")
                Para("Stimmt die Summe nicht, tipp im Eingabefenster auf einen der anderen Beträge vom Bon.")
            }
            GroupDivider()
            Topic("Kontoauszug einlesen", open == 5, { open = if (open == 5) -1 else 5 }) {
                Para("Lade bei DKB oder PayPal die Umsätze als CSV-Datei herunter und öffne sie mit Cashdy (Mehr › Kontoauszug importieren). Ich erkenne doppelte Einträge und schlage dir regelmäßige Zahlungen als Fixkosten vor.")
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
