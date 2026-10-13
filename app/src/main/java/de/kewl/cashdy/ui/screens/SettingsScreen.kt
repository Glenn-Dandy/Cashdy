package de.kewl.cashdy.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.dp
import de.kewl.cashdy.data.CashMode
import de.kewl.cashdy.data.PeriodMode
import de.kewl.cashdy.ui.components.MonthStartPicker
import de.kewl.cashdy.ui.components.monthStartText
import de.kewl.cashdy.data.Settings
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.Periods
import de.kewl.cashdy.domain.Salary
import de.kewl.cashdy.notify.Notify
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.components.Card
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.MoneyField
import de.kewl.cashdy.ui.components.Pill
import de.kewl.cashdy.ui.components.ScreenHeader
import de.kewl.cashdy.ui.components.SectionTitle
import de.kewl.cashdy.ui.components.SettingSwitch
import de.kewl.cashdy.ui.components.SoftField
import de.kewl.cashdy.ui.components.TextButtonSoft
import de.kewl.cashdy.ui.theme.C
import de.kewl.cashdy.widget.CashdyWidget
import java.time.LocalDate

fun setSettings(block: (Settings) -> Settings) = Store.update { it.copy(settings = block(it.settings)) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val d by Store.data.collectAsState()
    val s = d.settings
    val c = C.colors
    var canNotify by remember { mutableStateOf(Notify.canPost(ctx)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = Notify.canPost(ctx) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenHeader("Einstellungen", onBack = { ui.back() })

        var name by remember { mutableStateOf(s.name) }
        SoftField(name, { name = it; setSettings { st -> st.copy(name = it.trim()) } }, "Wie soll ich dich nennen?")

        SectionTitle("Wann beginnt dein Monat?")
        MonthStartPicker(s.periodMode, s.periodDay, 27) { m, day -> setSettings { it.copy(periodMode = m, periodDay = day) } }
        Hint(monthStartText(s.periodMode, s.periodDay, false))
        if (s.periodMode != PeriodMode.CALENDAR) {
            SettingSwitch("Wochenende: Werktag davor", "Fällt der Tag auf Samstag oder Sonntag, beginnt der Monat am Freitag.", s.shiftWeekend) { v -> setSettings { it.copy(shiftWeekend = v) } }
        }

        SectionTitle("Bargeld")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Portemonnaie", s.cashMode == CashMode.WALLET, { setSettings { it.copy(cashMode = CashMode.WALLET) } })
            Pill("Einfach-Modus", s.cashMode == CashMode.SIMPLE, { setSettings { it.copy(cashMode = CashMode.SIMPLE) } })
        }
        Hint(
            if (s.cashMode == CashMode.WALLET) "Abheben füllt dein Portemonnaie, Barzahlungen leeren es. Ab und zu ein Abgleich, fertig."
            else "Jede Abhebung zählt sofort als Ausgabe „Bargeld“. Barzahlungen musst du nicht extra eintragen."
        )

        SectionTitle("Erinnerungen")
        if (!canNotify) {
            Card(color = c.soft1) {
                Text("Benachrichtigungen sind aus", style = MaterialTheme.typography.titleSmall, color = c.fg)
                Hint("Ohne die kann ich dich nicht erinnern und erkannte Zahlungen nicht vorschlagen.")
                TextButtonSoft("Erlauben", {
                    if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
            }
        }
        Text("Wenn ich so lange nichts von dir höre:", style = MaterialTheme.typography.bodyLarge, color = c.fg)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "nie", 2 to "2 Tage", 3 to "3 Tage", 5 to "5 Tage", 7 to "1 Woche").forEach { (n, label) ->
                Pill(label, s.remindInactiveDays == n, { setSettings { it.copy(remindInactiveDays = n) } })
            }
        }
        SettingSwitch("Einen Tag vor Fälligkeiten", "„Morgen wird die Kfz-Versicherung fällig“", s.remindDue) { v -> setSettings { it.copy(remindDue = v) } }
        SettingSwitch("Wenn es knapp wird", "Unter ${Money.format(s.lowThreshold)} frei", s.remindLow) { v -> setSettings { it.copy(remindLow = v) } }
        if (s.remindLow) {
            var low by remember { mutableStateOf(Money.format(s.lowThreshold).removeSuffix(" €")) }
            MoneyField(low, { t -> low = t; Money.parse(t)?.takeIf { it > 0 }?.let { v -> setSettings { it.copy(lowThreshold = v) } } }, "Grenze in €")
        }
        SettingSwitch("Monatsabschluss", "„Dein Oktober: 312 € übrig“", s.remindPeriodEnd) { v -> setSettings { it.copy(remindPeriodEnd = v) } }
        if (s.cashMode == CashMode.WALLET) SettingSwitch("Sonntags Bargeld-Check", null, s.remindWallet) { v -> setSettings { it.copy(remindWallet = v) } }
        Spacer(Modifier.height(40.dp))
    }
}
