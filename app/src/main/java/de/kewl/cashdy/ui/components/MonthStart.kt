package de.kewl.cashdy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import de.kewl.cashdy.data.PeriodMode
import de.kewl.cashdy.ui.theme.C

/** Tag für „Mitte“. „Ende“ ist 31, das wird in kurzen Monaten zum letzten Tag. */
private const val MID = 15
private const val END = 31

/**
 * Wann beginnt dein Monat: Anfang (Kalendermonat), Mitte, Ende oder ein eigener Tag.
 * [suggest] ist der Tag, mit dem „Datum“ startet, wenn noch keiner gewählt ist.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthStartPicker(mode: PeriodMode, day: Int, suggest: Int, onChange: (PeriodMode, Int) -> Unit) {
    val c = C.colors
    val fixed = mode != PeriodMode.CALENDAR
    var custom by remember { mutableStateOf(fixed && day != MID && day != END) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Anfang", !custom && !fixed, { custom = false; onChange(PeriodMode.CALENDAR, 1) })
            Pill("Mitte", !custom && fixed && day == MID, { custom = false; onChange(PeriodMode.FIXED_DAY, MID) })
            Pill("Ende", !custom && fixed && day == END, { custom = false; onChange(PeriodMode.FIXED_DAY, END) })
            Pill("Datum", custom, {
                custom = true
                onChange(PeriodMode.FIXED_DAY, if (fixed && day != MID && day != END) day else suggest.coerceIn(1, 30))
            })
        }
        if (custom) {
            Card {
                Text("Dein Monat beginnt am ${day.coerceAtMost(30)}.", style = MaterialTheme.typography.titleSmall, color = c.fg)
                Slider(
                    value = day.coerceAtMost(30).toFloat(), onValueChange = { onChange(PeriodMode.FIXED_DAY, it.toInt()) },
                    valueRange = 1f..30f,
                    colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary),
                )
            }
        }
    }
}

/** Ein Satz, der den gewählten Monatsstart erklärt. */
fun monthStartText(mode: PeriodMode, day: Int, shiftWeekend: Boolean): String {
    if (mode == PeriodMode.CALENDAR) return "Ganz klassisch: vom 1. bis zum Monatsende."
    val weekend = if (shiftWeekend) " Fällt der Tag aufs Wochenende, nehme ich den Freitag davor." else ""
    return when (day) {
        END -> "Dein Monat beginnt immer am letzten Tag eines Monats.$weekend"
        1 -> "Dein Monat läuft vom 1. bis zum Monatsende.$weekend"
        else -> "Dein Monat läuft vom $day. bis zum ${day - 1}. im nächsten Monat.$weekend"
    }
}

/** Kurz für Sätze wie „bis zum nächsten …“. */
fun monthStartShort(mode: PeriodMode, day: Int): String = when {
    mode == PeriodMode.CALENDAR -> "zum Monatsende"
    day == END -> "zum letzten Tag des Monats"
    else -> "zum nächsten $day."
}
