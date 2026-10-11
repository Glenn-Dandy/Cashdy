package de.kewl.cashdy.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.cashdy.BuildConfig
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.Route
import de.kewl.cashdy.ui.components.BigButton
import de.kewl.cashdy.ui.components.EmojiBubble
import de.kewl.cashdy.ui.components.Mascot
import de.kewl.cashdy.ui.components.Mood
import de.kewl.cashdy.ui.components.ScreenHeader
import de.kewl.cashdy.ui.components.SettingSwitch
import de.kewl.cashdy.ui.components.TextButtonSoft
import de.kewl.cashdy.ui.theme.C
import de.kewl.cashdy.update.Repo
import de.kewl.cashdy.update.UpdateChecker
import de.kewl.cashdy.update.UpdateResult
import kotlinx.coroutines.launch

private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class Done(val result: UpdateResult) : UpdateUi
}

@Composable
fun AboutScreen() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val d by Store.data.collectAsState()
    val s = d.settings
    val c = C.colors
    var update by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }

    fun openUrl(url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openIssue(titlePrefix: String, label: String) {
        val body = "\n\n---\n" +
            "**App:** ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA})\n" +
            "**Android:** ${Build.VERSION.RELEASE}\n" +
            "**Gerät:** ${Build.MANUFACTURER} ${Build.MODEL}\n"
        openUrl(Repo.ISSUES_NEW_URL + "?labels=" + Uri.encode(label) + "&title=" + Uri.encode(titlePrefix) + "&body=" + Uri.encode(body))
    }

    fun check() {
        update = UpdateUi.Checking
        scope.launch { update = UpdateUi.Done(UpdateChecker.check(BuildConfig.VERSION_NAME, s.devUpdates)) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader("Über Cashdy", onBack = { ui.back() })

        // Kopf: Maskottchen, Name, Version
        Mascot(Mood.PARTY, size = 110.dp)
        Text("Cashdy", style = MaterialTheme.typography.headlineLarge, color = c.fg)
        // Sieben Tipper auf die Version schalten den Entwicklermodus frei (wie bei Android).
        // Gezählt wird beim Aufsetzen, sonst schluckt der scrollbare Bereich schnelle Tipper.
        var taps by remember { mutableIntStateOf(0) }
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium, color = c.muted,
            modifier = Modifier.pointerInput(s.devMode) {
                detectTapGestures(onPress = {
                    if (s.devMode) return@detectTapGestures
                    taps++
                    if (taps >= 7) {
                        taps = 0
                        setSettings { it.copy(devMode = true) }
                        ui.say("🛠️ Entwicklermodus an")
                    }
                })
            },
        )
        val left = 7 - taps
        if (!s.devMode && taps > 0 && left in 1..3) {
            Text("Noch $left× tippen …", style = MaterialTheme.typography.bodySmall, color = c.primary, modifier = Modifier.padding(top = 4.dp))
        }
        Text("Deine Finanzen, ganz einfach.", style = MaterialTheme.typography.titleSmall, color = c.fg, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(20.dp))

        Section("Feedback") {
            ActionRow("🐞", "Fehler melden") { openIssue("[Bug] ", "bug") }
            RowDivider()
            ActionRow("💡", "Idee vorschlagen") { openIssue("[Idee] ", "enhancement") }
        }
        Section("Projekt") {
            ActionRow("⭐", "Stern auf GitHub geben") { openUrl(Repo.URL) }
            RowDivider()
            ActionRow("🧑‍💻", "Quelltext ansehen") { openUrl(Repo.URL) }
            RowDivider()
            ActionRow("❤️", "Projekt unterstützen") { openUrl(Repo.SUPPORT_URL) }
        }
        Section("Datenschutz") {
            Text(
                "Keine Anmeldung, kein Bank-Login, kein Server. Alles, was du einträgst, bleibt auf deinem Handy. " +
                    "Benachrichtigungen lese ich nur von den Apps, die du freigibst. Ins Internet geht nur die Update-Prüfung " +
                    "(GitHub) und, wenn du sie einrichtest, die Sicherung auf deine Nextcloud.",
                style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(16.dp),
            )
        }
        Section("Update") {
            when (val u = update) {
                UpdateUi.Idle -> ActionRow("🔄", "Nach Update suchen") { check() }
                UpdateUi.Checking -> Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = c.primary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Ich schau mal nach …", color = c.fg)
                }
                is UpdateUi.Done -> when (val r = u.result) {
                    UpdateResult.UpToDate -> Column(Modifier.padding(16.dp)) {
                        Text("✅ Du hast die neueste Version.", color = c.fg, style = MaterialTheme.typography.bodyLarge)
                        TextButtonSoft("Nochmal prüfen", { check() })
                    }
                    is UpdateResult.Available -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🎉 Version ${r.version} ist da!", color = c.primary, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            BigButton("APK laden", { openUrl(r.downloadUrl ?: r.releaseUrl) })
                            TextButtonSoft("Was ist neu?", { openUrl(r.releaseUrl) })
                        }
                    }
                    UpdateResult.Failed -> Column(Modifier.padding(16.dp)) {
                        Text("😕 GitHub war gerade nicht erreichbar.", color = c.warn)
                        TextButtonSoft("Nochmal prüfen", { check() })
                    }
                }
            }
        }
        if (s.devMode) {
            Section("Entwickler") {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SettingSwitch("Auch Dev-Builds anbieten", "Bei der Update-Prüfung auch den neuesten Entwicklungsstand zeigen.", s.devUpdates) { v ->
                        setSettings { it.copy(devUpdates = v) }; update = UpdateUi.Idle
                    }
                }
                RowDivider()
                ActionRow("🎙️", "Rekorder für Benachrichtigungen") { ui.go(Route.Recorder) }
                RowDivider()
                ActionRow("🙈", "Entwicklermodus ausschalten") { setSettings { it.copy(devMode = false, devUpdates = false) } }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Build ${BuildConfig.GIT_SHA} · MIT-Lizenz · © ${Repo.OWNER}", style = MaterialTheme.typography.bodySmall, color = c.muted)
        Text("Schriften Fredoka und Nunito (OFL) · Texterkennung Google ML Kit", style = MaterialTheme.typography.bodySmall, color = c.muted)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = C.colors.primary, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(C.colors.surface)) { content() }
    }
}

@Composable
private fun ActionRow(emoji: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBubble(emoji, C.colors.soft2, 36.dp)
        Text(label, style = MaterialTheme.typography.titleSmall, color = C.colors.fg, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Box { Text("›", color = C.colors.muted, fontSize = 22.sp) }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 60.dp), color = C.colors.line)
}
