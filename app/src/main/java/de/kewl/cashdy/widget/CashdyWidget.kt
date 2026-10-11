package de.kewl.cashdy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import de.kewl.cashdy.MainActivity
import de.kewl.cashdy.R
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.domain.Budget
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.Periods
import java.time.LocalDate

/** Homescreen-Widget: was noch frei ist, plus Knöpfe für Ausgabe und Kassenbon. */
class CashdyWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context.applicationContext)
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun updateAll(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, CashdyWidget::class.java))
            if (ids.isNotEmpty()) {
                val v = views(ctx)
                ids.forEach { m.updateAppWidget(it, v) }
            }
        }

        private fun launch(ctx: Context, action: String, code: Int): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java).apply {
                this.action = action
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        fun views(ctx: Context): RemoteViews {
            val d = Store.current
            val today = LocalDate.now()
            val sum = Budget.summary(d, Periods.periodFor(today, d.settings), today)
            return RemoteViews(ctx.packageName, R.layout.widget_cashdy).apply {
                setTextViewText(R.id.w_amount, Money.euros(sum.free) + Money.centsPart(sum.free))
                setTextViewText(R.id.w_day, if (sum.perDay > 0) "☀️ heute ${Money.short(sum.perDay)} drin" else "noch ${sum.daysLeft} Tage")
                setOnClickPendingIntent(R.id.w_root, launch(ctx, MainActivity.ACTION_OPEN, 1))
                setOnClickPendingIntent(R.id.w_add, launch(ctx, MainActivity.ACTION_ADD, 2))
                setOnClickPendingIntent(R.id.w_scan, launch(ctx, MainActivity.ACTION_SCAN, 3))
            }
        }
    }
}
