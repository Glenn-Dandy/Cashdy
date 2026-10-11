package de.kewl.cashdy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.kewl.cashdy.data.Category
import de.kewl.cashdy.data.CategoryType
import de.kewl.cashdy.data.DefaultCategories
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.components.EmojiBubble
import de.kewl.cashdy.ui.components.Hint
import de.kewl.cashdy.ui.components.Pill
import de.kewl.cashdy.ui.components.ScreenHeader
import de.kewl.cashdy.ui.components.SectionTitle
import de.kewl.cashdy.ui.components.SoftField
import de.kewl.cashdy.ui.components.categoryTint
import de.kewl.cashdy.ui.theme.C

private val palette = listOf(0xFF6C4DF6, 0xFFFFB37A, 0xFFFF7EB6, 0xFF7ED9B5, 0xFF7DA7FF, 0xFFFFD36B, 0xFFB59CFF, 0xFFFF8F70, 0xFF5FC7E8, 0xFFC3BED6)

@Composable
fun CategoriesScreen() {
    val ui = LocalUi.current
    val d by Store.data.collectAsState()
    val c = C.colors
    var edit by remember { mutableStateOf<Category?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader("Kategorien", onBack = { ui.back() })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("＋ Ausgabe-Kategorie", true, { edit = Category(Store.newId(), "", "🏷️", palette.random()) })
            Pill("＋ Einnahme", false, { edit = Category(Store.newId(), "", "💸", palette.random(), CategoryType.INCOME) })
        }
        listOf(CategoryType.EXPENSE to "Ausgaben", CategoryType.INCOME to "Einnahmen").forEach { (type, title) ->
            SectionTitle(title)
            d.categories.filter { it.type == type }.forEach { cat ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).clickable { edit = cat }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EmojiBubble(cat.emoji, categoryTint(cat, c.dark), 36.dp)
                    Text(cat.name, style = MaterialTheme.typography.titleSmall, color = c.fg, modifier = Modifier.weight(1f))
                    val n = d.entries.count { it.categoryId == cat.id }
                    if (n > 0) Text("$n×", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    edit?.let { cat -> CategoryDialog(cat, isNew = d.categories.none { it.id == cat.id }) { edit = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(cat: Category, isNew: Boolean, onClose: () -> Unit) {
    var name by remember { mutableStateOf(cat.name) }
    var emoji by remember { mutableStateOf(cat.emoji) }
    var color by remember { mutableStateOf(cat.color) }
    val builtIn = DefaultCategories.all.any { it.id == cat.id }
    val protected = cat.id in setOf(DefaultCategories.OTHER, DefaultCategories.CASH_UNKNOWN, DefaultCategories.SALARY, DefaultCategories.INCOME_OTHER)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (isNew) "Neue Kategorie" else "Kategorie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SoftField(emoji, { t -> emoji = t.take(4) }, "Emoji", Modifier.weight(0.35f))
                    SoftField(name, { name = it }, "Name", Modifier.weight(0.65f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    palette.forEach { p ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(p))
                                .then(if (p == color) Modifier.border(3.dp, C.colors.fg, CircleShape) else Modifier)
                                .clickable { color = p },
                        )
                    }
                }
                if (!isNew && !protected) Hint(if (builtIn) "Löschen: Einträge wandern zu „Sonstiges“." else "Einträge dieser Kategorie wandern beim Löschen zu „Sonstiges“.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                val updated = cat.copy(name = name.trim(), emoji = emoji.ifBlank { "🏷️" }, color = color)
                Store.update { d -> d.copy(categories = if (isNew) d.categories + updated else d.categories.map { if (it.id == cat.id) updated else it }) }
                onClose()
            }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (!isNew && !protected) TextButton(onClick = {
                    val fallback = if (cat.type == CategoryType.INCOME) DefaultCategories.INCOME_OTHER else DefaultCategories.OTHER
                    Store.update { d ->
                        d.copy(
                            categories = d.categories.filterNot { it.id == cat.id },
                            entries = d.entries.map { if (it.categoryId == cat.id) it.copy(categoryId = fallback) else it },
                            recurring = d.recurring.map { if (it.categoryId == cat.id) it.copy(categoryId = fallback) else it },
                            merchantRules = d.merchantRules.mapValues { (_, v) -> if (v == cat.id) fallback else v },
                        )
                    }
                    onClose()
                }) { Text("Löschen", color = C.colors.warn) }
                TextButton(onClick = onClose) { Text("Abbrechen") }
            }
        },
    )
}
