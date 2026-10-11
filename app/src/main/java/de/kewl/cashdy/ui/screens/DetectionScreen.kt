package de.kewl.cashdy.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.kewl.cashdy.data.DetectedKind
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.detect.NotificationParser
import de.kewl.cashdy.detect.PaymentListenerService
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.Route
import de.kewl.cashdy.ui.components.Card
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.MenuRow
import de.kewl.cashdy.ui.components.ScreenHeader
import de.kewl.cashdy.ui.components.SectionTitle
import de.kewl.cashdy.ui.components.SettingSwitch
import de.kewl.cashdy.ui.components.SoftField
import de.kewl.cashdy.ui.components.TextButtonSoft
import de.kewl.cashdy.ui.theme.C
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun rememberListenerEnabled(): Boolean {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(PaymentListenerService.isEnabled(ctx)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) on = PaymentListenerService.isEnabled(ctx) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return on
}

fun openListenerSettings(ctx: Context) {
    ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun DetectionScreen() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val d by Store.data.collectAsState()
    val s = d.settings
    val c = C.colors
    val enabled = rememberListenerEnabled()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader("Zahlungserkennung", onBack = { ui.back() })
        Card(color = if (enabled) c.soft3 else c.soft1) {
            Text(if (enabled) "✅ Aktiv" else "⏸️ Aus", style = MaterialTheme.typography.titleMedium, color = c.fg)
            Hint(
                if (enabled) "Ich lese nur die Benachrichtigungen der Apps unten. Alles andere ignoriere ich sofort. Nichts verlässt dein Handy."
                else "Erlaube mir den Zugriff auf Benachrichtigungen. Dann schlage ich dir Zahlungen von PayPal, DKB & Co. direkt vor."
            )
            TextButtonSoft(if (enabled) "Systemeinstellung öffnen" else "Einschalten", { openListenerSettings(ctx) })
        }

        SettingSwitch(
            "Bekannte Händler automatisch eintragen",
            "Wenn ich den Händler schon kenne (z. B. REWE = Lebensmittel), trage ich direkt ein. Rückgängig per Benachrichtigung.",
            s.autoAccept,
        ) { v -> setSettings { it.copy(autoAccept = v) } }

        SectionTitle("Diese Apps lese ich")
        val extra = s.watchedApps.filter { NotificationParser.app(it) == null }
        (NotificationParser.knownApps.map { it.pkg to it.name } + extra.map { it to it }).forEach { (pkg, name) ->
            SettingSwitch(name, pkg, pkg in s.watchedApps) { v ->
                setSettings { it.copy(watchedApps = if (v) it.watchedApps + pkg else it.watchedApps - pkg) }
            }
        }

        MenuRow("🎙️", "Rekorder", if (s.recorder) "Läuft, ${d.recorded.size} Meldungen" else "Benachrichtigungstexte sammeln, um neue Banken einzubauen") { ui.go(Route.Recorder) }

        SectionTitle("Ausprobieren")
        var test by remember { mutableStateOf("Du hast eine Zahlung in Höhe von 12,49 € an REWE autorisiert.") }
        SoftField(test, { test = it }, "Text einer Benachrichtigung", singleLine = false)
        val parsed = NotificationParser.parse("com.paypal.android.p2pmobile", "", test)
        Card(Modifier.fillMaxWidth()) {
            if (parsed == null) Text("🤷 Keine Zahlung erkannt", color = c.fg)
            else {
                Text(
                    kindLabel(parsed.kind) +
                        " · ${Money.format(parsed.amount)}",
                    style = MaterialTheme.typography.titleSmall, color = c.fg,
                )
                Text("Händler: ${parsed.merchant.ifBlank { "?" }}", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun RecorderScreen() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val d by Store.data.collectAsState()
    val s = d.settings
    val c = C.colors
    val fmt = DateTimeFormatter.ofPattern("d.M. HH:mm")

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader("Rekorder", onBack = { ui.back() })
        Hint("Der Rekorder merkt sich alle Benachrichtigungen mit einem Geldbetrag, egal von welcher App. So sehen wir, wie deine Bank ihre Meldungen formuliert, und können sie einbauen. Höchstens 200 Meldungen, nur auf diesem Handy.")
        SettingSwitch("Rekorder an", null, s.recorder) { v -> setSettings { it.copy(recorder = v) } }
        if (d.recorded.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButtonSoft("📋 Alles kopieren", {
                val text = d.recorded.joinToString("\n\n") { "[${it.pkg}] ${it.title}\n${it.text}" }
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Cashdy Rekorder", text))
                ui.say("Kopiert")
            })
            TextButtonSoft("🗑️ Leeren", { Store.update { it.copy(recorded = emptyList()) } })
        }
        d.recorded.forEach { r ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(NotificationParser.app(r.pkg)?.name ?: r.pkg, style = MaterialTheme.typography.titleSmall, color = c.fg, modifier = Modifier.weight(1f))
                    Text(Instant.ofEpochMilli(r.at).atZone(ZoneId.systemDefault()).format(fmt), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Text(r.title, style = MaterialTheme.typography.bodyMedium, color = c.fg)
                Text(r.text, style = MaterialTheme.typography.bodySmall, color = c.muted)
                val p = NotificationParser.parse(r.pkg, r.title, r.text)
                Hint(if (p == null) "→ nicht erkannt" else "→ ${kindLabel(p.kind)} ${Money.format(p.amount)} ${p.merchant}")
                if (r.pkg !in s.watchedApps) TextButtonSoft("Diese App lesen", { setSettings { it.copy(watchedApps = it.watchedApps + r.pkg) } })
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

private fun kindLabel(k: DetectedKind) = when (k) {
    DetectedKind.EXPENSE -> "💳 Ausgabe"
    DetectedKind.CASH -> "🏧 Bargeld"
    DetectedKind.INCOME -> "💰 Eingang"
    DetectedKind.REFUND -> "↩️ Erstattung"
}
