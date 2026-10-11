package de.kewl.cashdy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.ui.theme.C
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Betragseingabe als Text: Ziffern und ein Komma. Hält den Text selbst, damit "12," beim
 * Tippen nicht sofort zu "12,00" wird.
 */
object AmountInput {
    fun press(current: String, key: Char): String = when (key) {
        '⌫' -> current.dropLast(1)
        ',' -> if (current.contains(',')) current else (current.ifEmpty { "0" } + ",")
        else -> {
            val comma = current.indexOf(',')
            when {
                comma >= 0 && current.length - comma > 2 -> current // max. zwei Nachkommastellen
                comma < 0 && current.length >= 7 -> current // max. 9.999.999
                current == "0" -> key.toString()
                else -> current + key
            }
        }
    }

    fun cents(text: String): Cents = Money.parse(text) ?: 0

    fun fromCents(c: Cents): String =
        if (c == 0L) "" else if (c % 100 == 0L) (c / 100).toString() else "${c / 100},${(c % 100).toString().padStart(2, '0')}"

    fun display(text: String): String {
        if (text.isEmpty()) return "0"
        val whole = text.substringBefore(',')
        val grouped = whole.reversed().chunked(3).joinToString(".").reversed()
        return if (text.contains(',')) grouped + "," + text.substringAfter(',') else grouped
    }
}

@Composable
fun Keypad(onKey: (Char) -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf("123", "456", "789", ",0⌫")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                r.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(18.dp))
                            .background(C.colors.surface).clickable { onKey(k) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(k.toString(), style = MaterialTheme.typography.headlineSmall, color = C.colors.fg, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SoftField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, password: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = C.colors.surface, unfocusedContainerColor = C.colors.surface,
            focusedBorderColor = C.colors.primary, unfocusedBorderColor = C.colors.line,
        ),
    )
}

/** Betragsfeld mit normaler Tastatur (für Formulare, in denen der große Ziffernblock zu viel wäre). */
@Composable
fun MoneyField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    SoftField(value, { t -> onChange(t.filter { it.isDigit() || it == ',' || it == '.' }) }, label, modifier, KeyboardType.Decimal)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePick(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("Passt") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    ) { DatePicker(state) }
}

@Composable
fun DateChip(date: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(C.colors.surface).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(if (date == LocalDate.now()) "Heute" else shortDate(date), style = MaterialTheme.typography.labelLarge, color = C.colors.fg)
    }
}
