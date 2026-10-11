package de.kewl.cashdy.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.PayMethod
import java.time.LocalDate

sealed interface Route {
    data object Home : Route
    data object Month : Route
    data object Fixed : Route
    data object More : Route
    data class RecurringEdit(val id: String?, val income: Boolean = false) : Route
    data object Wallet : Route
    data class Inbox(val focusId: String? = null) : Route
    data class Import(val uri: Uri? = null) : Route
    data object Settings : Route
    data object Categories : Route
    data object Detection : Route
    data object Recorder : Route
    data object Backup : Route
    data object About : Route
    data object Help : Route
}

val Tabs = listOf(Route.Home, Route.Month, Route.Fixed, Route.More)

/** Was das Eingabe-Sheet vorbelegen soll. */
data class AddRequest(
    val editId: String? = null,
    val amount: Cents = 0,
    val merchant: String = "",
    val categoryId: String? = null,
    val method: PayMethod? = null,
    val date: LocalDate = LocalDate.now(),
    val kind: EntryKind = EntryKind.EXPENSE,
    val receiptUri: Uri? = null,
    val fromScan: Boolean = false,
    /** Beträge vom Bon zum Antippen, falls die erkannte Summe nicht stimmt. */
    val candidates: List<Cents> = emptyList(),
)

class Ui {
    val stack = mutableStateListOf<Route>(Route.Home)
    var add by mutableStateOf<AddRequest?>(null)
    var scanChooser by mutableStateOf(false)
    var pendingScanUri by mutableStateOf<Uri?>(null)
    var toast by mutableStateOf<String?>(null)
    var confettiKey by mutableStateOf(0)

    val current: Route get() = stack.last()

    fun go(r: Route) {
        if (r in Tabs) { stack.clear(); stack.add(r) } else stack.add(r)
    }

    fun back(): Boolean {
        if (stack.size <= 1) {
            if (stack.last() != Route.Home) { stack.clear(); stack.add(Route.Home); return true }
            return false
        }
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun openAdd(r: AddRequest = AddRequest()) { add = r }

    fun say(text: String) { toast = text }

    /** Routen aus Benachrichtigungen ("inbox/<id>", "wallet" ...). */
    fun openRoute(route: String) {
        when {
            route.startsWith("inbox") -> { go(Route.Home); go(Route.Inbox(route.substringAfter('/', "").ifBlank { null })) }
            route == "wallet" -> { go(Route.Home); go(Route.Wallet) }
            route == "month" -> go(Route.Month)
            route == "fixed" -> go(Route.Fixed)
            else -> go(Route.Home)
        }
    }
}

val LocalUi = staticCompositionLocalOf { Ui() }
