package de.kewl.cashdy.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CodeOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import de.kewl.cashdy.BuildConfig
import de.kewl.cashdy.R
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.Route
import de.kewl.cashdy.ui.components.RowTitle
import de.kewl.cashdy.ui.components.BigButton
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

        // Kopf: echtes App-Symbol, Name, Version (Aufbau wie bei BoatSpeedy)
        Spacer(Modifier.height(8.dp))
        val appIcon = remember {
            runCatching { ctx.packageManager.getApplicationIcon(ctx.packageName).toBitmap(width = 144, height = 144).asImageBitmap() }.getOrNull()
        }
        if (appIcon != null) {
            Image(appIcon, null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
        } else {
            Mascot(Mood.PARTY, size = 72.dp, animate = false)
        }
        Spacer(Modifier.height(10.dp))
        Text("Cashdy", style = MaterialTheme.typography.headlineSmall, color = c.fg)
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
                        ui.say("Entwicklermodus an")
                    }
                })
            },
        )
        val left = 7 - taps
        if (!s.devMode && taps > 0 && left in 1..3) {
            Text("Noch $left× tippen …", style = MaterialTheme.typography.bodySmall, color = c.primary, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(24.dp))

        Section("Feedback") {
            ActionRow(Icons.Rounded.BugReport, "Fehler melden") { openIssue("[Bug] ", "bug") }
            RowDivider()
            ActionRow(Icons.Rounded.Lightbulb, "Idee vorschlagen") { openIssue("[Idee] ", "enhancement") }
        }
        Section("Projekt") {
            ActionRow(Icons.Rounded.Star, "Stern auf GitHub geben") { openUrl(Repo.URL) }
            RowDivider()
            ActionRow(Icons.Rounded.Code, "Quelltext ansehen") { openUrl(Repo.URL) }
            RowDivider()
            ActionRow(Icons.Rounded.Favorite, "Projekt unterstützen", tint = c.warn) { openUrl(Repo.SUPPORT_URL) }
        }
        Section("Datenschutz") {
            Text(
                "Keine Anmeldung, kein Bank-Login, kein Server. Alles, was du einträgst, bleibt auf deinem Handy. " +
                    "Benachrichtigungen lese ich nur von den Apps, die du freigibst. Kassenbons lese ich direkt auf dem Handy. " +
                    "Ins Internet geht nur die Update-Prüfung (GitHub), und nur wenn du sie antippst.",
                style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(16.dp),
            )
        }
        Section("Mit dabei") {
            Text(
                "Texterkennung Tesseract (Apache 2.0) · Schriften Fredoka und Nunito (OFL)",
                style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(16.dp),
            )
        }
        Section("Update") {
            when (val u = update) {
                UpdateUi.Idle -> ActionRow(Icons.Rounded.SystemUpdate, "Nach Update suchen") { check() }
                UpdateUi.Checking -> Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = c.primary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Ich schau mal nach …", color = c.fg)
                }
                is UpdateUi.Done -> when (val r = u.result) {
                    UpdateResult.UpToDate -> Column(Modifier.padding(16.dp)) {
                        Text("Du hast die neueste Version.", color = c.fg, style = MaterialTheme.typography.bodyLarge)
                        TextButtonSoft("Nochmal prüfen", { check() })
                    }
                    is UpdateResult.Available -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Version ${r.version} ist da!", color = c.primary, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            BigButton("APK laden", { openUrl(r.downloadUrl ?: r.releaseUrl) })
                            TextButtonSoft("Was ist neu?", { openUrl(r.releaseUrl) })
                        }
                    }
                    UpdateResult.Failed -> Column(Modifier.padding(16.dp)) {
                        Text("GitHub war gerade nicht erreichbar.", color = c.warn)
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
                ActionRow(Icons.Rounded.FiberManualRecord, "Rekorder für Benachrichtigungen") { ui.go(Route.Recorder) }
                RowDivider()
                ActionRow(Icons.Rounded.CodeOff, "Entwicklermodus ausschalten") { setSettings { it.copy(devMode = false, devUpdates = false) } }
            }
        }

        Spacer(Modifier.height(8.dp))
        // Fuß wie bei BoatSpeedy: Lizenz, Copyright, wozise.de und das Logo, beides antippbar.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MIT-Lizenz · © ${Repo.OWNER} · ", style = MaterialTheme.typography.bodySmall, color = c.muted)
            Text(
                Repo.WEBSITE_LABEL, style = MaterialTheme.typography.bodySmall, color = c.primary,
                modifier = Modifier.clickable { openUrl(Repo.WEBSITE_URL) },
            )
        }
        Spacer(Modifier.height(12.dp))
        Image(
            painterResource(if (c.dark) R.drawable.ic_wozise_logo_dark else R.drawable.ic_wozise_logo),
            contentDescription = Repo.WEBSITE_LABEL,
            modifier = Modifier.clickable { openUrl(Repo.WEBSITE_URL) },
        )
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
private fun ActionRow(icon: ImageVector, label: String, tint: Color = C.colors.primary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, style = RowTitle, color = C.colors.fg, modifier = Modifier.weight(1f))
        Box { Text("›", color = C.colors.muted, fontSize = 22.sp) }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp), color = C.colors.line)
}
