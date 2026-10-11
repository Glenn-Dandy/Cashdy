package de.kewl.cashdy.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.detect.Detection
import de.kewl.cashdy.domain.Salary

/** Knöpfe in den Cashdy-Benachrichtigungen. Alles läuft ohne die App zu öffnen. */
class ActionReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_VALUE = "value"
        const val ACCEPT = "de.kewl.cashdy.ACCEPT"
        const val IGNORE = "de.kewl.cashdy.IGNORE"
        const val CASH_WALLET = "de.kewl.cashdy.CASH_WALLET"
        const val CASH_EXPENSE = "de.kewl.cashdy.CASH_EXPENSE"
        const val SALARY = "de.kewl.cashdy.SALARY"
        const val UNDO = "de.kewl.cashdy.UNDO"
        const val SALARY_YES = "de.kewl.cashdy.SALARY_YES"
        const val SALARY_NO = "de.kewl.cashdy.SALARY_NO"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context.applicationContext)
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val value = intent.getStringExtra(EXTRA_VALUE)
        when (intent.action) {
            ACCEPT -> Store.update { Detection.accept(it, id, value).first }
            IGNORE -> Store.update { Detection.ignore(it, id) }
            CASH_WALLET -> Store.update { Detection.accept(it, id, null, asCashWithdrawal = true).first }
            CASH_EXPENSE -> Store.update { Detection.accept(it, id, null, asCashWithdrawal = false).first }
            SALARY -> Store.update { d ->
                val (d2, entryId) = Detection.accept(d, id, null).let { (x, e) -> x to e?.id }
                // Als Gehalt zählt der Betrag schon über die regelmäßige Einnahme, deshalb
                // den gerade angelegten Einzel-Eintrag wieder entfernen und nur den Monat starten.
                val det = d2.detected.firstOrNull { it.id == id }
                val cleaned = d2.copy(entries = d2.entries.filterNot { it.id == entryId })
                Salary.startNewPeriod(cleaned, det?.let { Detection.dayOf(it) } ?: java.time.LocalDate.now(), det?.amount)
            }
            UNDO -> Store.update { d ->
                d.copy(
                    entries = d.entries.filterNot { it.id == value },
                    detected = d.detected.map { if (it.id == id) it.copy(status = de.kewl.cashdy.data.DetectedStatus.PENDING, entryId = null) else it },
                )
            }
            SALARY_YES -> Store.update { Salary.startNewPeriod(it, java.time.LocalDate.now(), null) }
            SALARY_NO -> Unit
        }
        Notify.cancel(context, id)
    }
}
