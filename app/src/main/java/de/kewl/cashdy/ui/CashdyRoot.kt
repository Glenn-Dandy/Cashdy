package de.kewl.cashdy.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.ocr.ReceiptScanner
import de.kewl.cashdy.ui.components.Confetti
import de.kewl.cashdy.ui.screens.AboutScreen
import de.kewl.cashdy.ui.screens.AddSheet
import de.kewl.cashdy.ui.screens.BackupScreen
import de.kewl.cashdy.ui.screens.CategoriesScreen
import de.kewl.cashdy.ui.screens.DetectionScreen
import de.kewl.cashdy.ui.screens.FixedScreen
import de.kewl.cashdy.ui.screens.HomeScreen
import de.kewl.cashdy.ui.screens.ImportScreen
import de.kewl.cashdy.ui.screens.InboxScreen
import de.kewl.cashdy.ui.screens.MonthScreen
import de.kewl.cashdy.ui.screens.MoreScreen
import de.kewl.cashdy.ui.screens.OnboardingScreen
import de.kewl.cashdy.ui.screens.RecorderScreen
import de.kewl.cashdy.ui.screens.RecurringEditScreen
import de.kewl.cashdy.ui.screens.SettingsScreen
import de.kewl.cashdy.ui.screens.WalletScreen
import de.kewl.cashdy.ui.theme.C
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CashdyRoot(ui: Ui) {
    val data by Store.data.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }

    fun runScan(uri: Uri) {
        scanning = true
        scope.launch {
            val res = ReceiptScanner.scan(ctx, uri)
            scanning = false
            res.onSuccess { info ->
                ui.openAdd(
                    AddRequest(
                        amount = info.total ?: 0, merchant = info.merchant, categoryId = info.categoryGuess,
                        method = if (info.paidCash) PayMethod.CASH else null,
                        date = info.date ?: java.time.LocalDate.now(), receiptUri = uri, fromScan = true,
                    )
                )
                if (info.total == null) ui.say("Summe nicht gefunden, bitte kurz eintippen")
            }.onFailure { ui.say("Bon konnte nicht gelesen werden 😕") }
        }
    }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) runScan(u)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) runScan(uri) }

    fun startCamera() {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "bon-${System.currentTimeMillis()}.jpg")
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        cameraUri = u
        runCatching { camera.launch(u) }.onFailure { ui.say("Keine Kamera-App gefunden") }
    }

    LaunchedEffect(ui.pendingScanUri) {
        ui.pendingScanUri?.let { runScan(it); ui.pendingScanUri = null }
    }
    LaunchedEffect(ui.toast) {
        if (ui.toast != null) { delay(2600); ui.toast = null }
    }

    if (!data.settings.onboarded) {
        OnboardingScreen(onDone = { ui.confettiKey++ })
        return
    }

    BackHandler(enabled = ui.stack.size > 1 || ui.current != Route.Home) { ui.back() }

    val route = ui.current
    val isTab = route in Tabs
    Box(Modifier.fillMaxSize().background(C.colors.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(route, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "route") { r ->
                    when (r) {
                        Route.Home -> HomeScreen()
                        Route.Month -> MonthScreen()
                        Route.Fixed -> FixedScreen()
                        Route.More -> MoreScreen()
                        is Route.RecurringEdit -> RecurringEditScreen(r.id, r.income)
                        Route.Wallet -> WalletScreen()
                        is Route.Inbox -> InboxScreen(r.focusId)
                        is Route.Import -> ImportScreen(r.uri)
                        Route.Settings -> SettingsScreen()
                        Route.Categories -> CategoriesScreen()
                        Route.Detection -> DetectionScreen()
                        Route.Recorder -> RecorderScreen()
                        Route.Backup -> BackupScreen()
                        Route.About -> AboutScreen()
                    }
                }
            }
            if (isTab) BottomBar(route) { ui.go(it) }
        }

        if (route == Route.Home || route == Route.Month) {
            Box(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 74.dp)
                    .shadow(10.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
                    .background(C.colors.primary).clickable { ui.openAdd() }
                    .padding(horizontal = 30.dp, vertical = 15.dp),
            ) {
                Text("＋ Ausgabe", style = MaterialTheme.typography.titleLarge, color = C.colors.onPrimary)
            }
        }

        ui.add?.let { req -> AddSheet(req, onDismiss = { ui.add = null }, onScan = { ui.add = null; ui.scanChooser = true }) }

        if (ui.scanChooser) {
            AlertDialog(
                onDismissRequest = { ui.scanChooser = false },
                title = { Text("📸 Kassenbon") },
                text = { Text("Fotografier den Bon, ich lese Summe, Laden und Datum raus. Alles bleibt auf deinem Handy.") },
                confirmButton = { TextButton(onClick = { ui.scanChooser = false; startCamera() }) { Text("Foto machen") } },
                dismissButton = {
                    TextButton(onClick = {
                        ui.scanChooser = false
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Aus Galerie") }
                },
            )
        }

        if (scanning) {
            Box(Modifier.fillMaxSize().background(C.colors.bg.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = C.colors.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Ich lese den Bon …", style = MaterialTheme.typography.titleMedium, color = C.colors.fg)
                }
            }
        }

        ui.toast?.let { t ->
            Box(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp, start = 16.dp, end = 16.dp)
                    .shadow(8.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                    .background(C.colors.surface).padding(horizontal = 18.dp, vertical = 12.dp),
            ) { Text(t, style = MaterialTheme.typography.titleSmall, color = C.colors.fg) }
        }

        if (ui.confettiKey > 0) Confetti(ui.confettiKey)
    }
}

@Composable
private fun BottomBar(current: Route, onSelect: (Route) -> Unit) {
    val items = listOf(Triple(Route.Home, "🏠", "Start"), Triple(Route.Month, "📊", "Monat"), Triple(Route.Fixed, "🔁", "Fest"), Triple(Route.More, "✨", "Mehr"))
    Row(
        Modifier.fillMaxWidth().background(C.colors.surface).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        items.forEach { (r, emoji, label) ->
            val sel = r == current
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).background(if (sel) C.colors.soft2 else C.colors.surface)
                    .clickable { onSelect(r) }.padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(emoji, fontSize = 20.sp)
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (sel) C.colors.fg else C.colors.muted)
            }
        }
    }
}
