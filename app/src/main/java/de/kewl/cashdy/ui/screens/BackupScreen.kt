package de.kewl.cashdy.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.kewl.cashdy.backup.Backup
import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.components.Card
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.Pill
import de.kewl.cashdy.ui.components.ScreenHeader
import de.kewl.cashdy.ui.components.SectionTitle
import de.kewl.cashdy.ui.components.SettingSwitch
import de.kewl.cashdy.ui.components.SoftField
import de.kewl.cashdy.ui.theme.C
import de.kewl.cashdy.widget.CashdyWidget
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BackupScreen() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d by Store.data.collectAsState()
    val s = d.settings
    val c = C.colors
    var restoreCandidate by remember { mutableStateOf<AppData?>(null) }
    var busy by remember { mutableStateOf(false) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            Backup.exportTo(ctx, uri).onSuccess { ui.say("Gesichert ✓") }.onFailure { ui.say("Hat nicht geklappt: ${it.message}") }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            Backup.readFrom(ctx, uri).onSuccess { restoreCandidate = it }.onFailure { ui.say("Keine gültige Cashdy-Sicherung") }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader("Sicherung", onBack = { ui.back() })
        Hint("Deine Daten liegen nur auf diesem Handy. Mach ab und zu eine Sicherung, damit beim Handywechsel nichts verloren geht. Kassenbon-Fotos sind nicht enthalten.")

        SectionTitle("📁 Als Datei")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Sichern", true, { create.launch("cashdy-${java.time.LocalDate.now()}.json") })
            Pill("Wiederherstellen", false, { open.launch(arrayOf("application/json", "*/*")) })
        }

        SectionTitle("☁️ Nextcloud / WebDAV")
        Card {
            var url by remember { mutableStateOf(s.webdavUrl) }
            var user by remember { mutableStateOf(s.webdavUser) }
            var pass by remember { mutableStateOf(s.webdavPass) }
            Hint("Adresse des Ordners, z. B. https://cloud.example.de/remote.php/dav/files/NAME/Cashdy. Den Ordner vorher in der Nextcloud anlegen. Am besten ein App-Passwort nutzen (Nextcloud: Einstellungen › Sicherheit).")
            Spacer(Modifier.height(6.dp))
            SoftField(url, { url = it; setSettings { st -> st.copy(webdavUrl = it.trim()) } }, "WebDAV-Adresse", keyboard = KeyboardType.Uri)
            Spacer(Modifier.height(6.dp))
            SoftField(user, { user = it; setSettings { st -> st.copy(webdavUser = it.trim()) } }, "Benutzername")
            Spacer(Modifier.height(6.dp))
            SoftField(pass, { pass = it; setSettings { st -> st.copy(webdavPass = it) } }, "App-Passwort", password = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (busy) "…" else "Jetzt sichern", true, {
                    if (!busy) { busy = true; scope.launch {
                        Backup.uploadWebDav().onSuccess { ui.say("In der Nextcloud gesichert ✓") }.onFailure { ui.say("😕 ${it.message}") }
                        busy = false
                    } }
                })
                Pill("Von dort holen", false, {
                    if (!busy) { busy = true; scope.launch {
                        Backup.downloadWebDav().onSuccess { restoreCandidate = it }.onFailure { ui.say("😕 ${it.message}") }
                        busy = false
                    } }
                })
            }
            if (s.lastBackupAt > 0) Hint("Zuletzt gesichert: " + Instant.ofEpochMilli(s.lastBackupAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")))
            SettingSwitch("Täglich automatisch sichern", null, s.autoBackup) { v -> setSettings { it.copy(autoBackup = v) } }
        }
        Spacer(Modifier.height(40.dp))
    }

    restoreCandidate?.let { cand ->
        AlertDialog(
            onDismissRequest = { restoreCandidate = null },
            title = { Text("Sicherung einspielen?") },
            text = { Text("Darin: ${cand.entries.size} Einträge, ${cand.recurring.size} regelmäßige Posten. Das ersetzt alles, was gerade in der App ist.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = {
                Backup.restore(cand); restoreCandidate = null; CashdyWidget.updateAll(ctx); ui.say("Wiederhergestellt ✓")
            }) { Text("Ersetzen", color = c.warn) } },
            dismissButton = { TextButton(onClick = { restoreCandidate = null }) { Text("Abbrechen") } },
        )
    }
}
