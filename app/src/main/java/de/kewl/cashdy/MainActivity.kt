package de.kewl.cashdy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.IntentCompat
import de.kewl.cashdy.data.PayMethod
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.ui.AddRequest
import de.kewl.cashdy.ui.CashdyRoot
import de.kewl.cashdy.ui.LocalUi
import de.kewl.cashdy.ui.Route
import de.kewl.cashdy.ui.Ui
import de.kewl.cashdy.ui.theme.CashdyTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_ADD = "de.kewl.cashdy.ADD"
        const val ACTION_ADD_CASH = "de.kewl.cashdy.ADD_CASH"
        const val ACTION_SCAN = "de.kewl.cashdy.SCAN"
        const val ACTION_OPEN = "de.kewl.cashdy.OPEN"
        const val EXTRA_ROUTE = "route"
    }

    private val ui = Ui()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(applicationContext)
        if (savedInstanceState == null) handle(intent)
        setContent {
            CashdyTheme {
                CompositionLocalProvider(LocalUi provides ui) { CashdyRoot(ui) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        i ?: return
        if (!Store.current.settings.onboarded) return
        when (i.action) {
            ACTION_ADD -> ui.openAdd()
            ACTION_ADD_CASH -> ui.openAdd(AddRequest(method = PayMethod.CASH))
            ACTION_SCAN -> ui.scanChooser = true
            ACTION_OPEN -> i.getStringExtra(EXTRA_ROUTE)?.let { ui.openRoute(it) }
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java) ?: return
                if (i.type?.startsWith("image/") == true) ui.pendingScanUri = uri
                else { ui.go(Route.Home); ui.go(Route.Import(uri)) }
            }
        }
    }
}
