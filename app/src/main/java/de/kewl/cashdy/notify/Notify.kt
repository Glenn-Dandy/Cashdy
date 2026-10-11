package de.kewl.cashdy.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.kewl.cashdy.MainActivity
import de.kewl.cashdy.R
import de.kewl.cashdy.data.Detected
import de.kewl.cashdy.data.DetectedKind
import de.kewl.cashdy.data.Entry
import de.kewl.cashdy.data.PeriodMode
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.data.category
import de.kewl.cashdy.domain.Money

object Notify {
    const val CH_DETECTED = "detected"
    const val CH_REMINDER = "reminder"
    const val CH_BACKUP = "backup"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DETECTED, "Erkannte Zahlungen", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Wenn Cashdy eine Zahlung aus PayPal, DKB & Co. erkennt"
        })
        nm.createNotificationChannel(NotificationChannel(CH_REMINDER, "Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Fälligkeiten, Monatsende, lange nichts eingetragen"
        })
        nm.createNotificationChannel(NotificationChannel(CH_BACKUP, "Sicherung", NotificationManager.IMPORTANCE_LOW))
    }

    fun canPost(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun notifId(key: String) = key.hashCode() and 0x7fffffff

    fun cancel(ctx: Context, key: String) = NotificationManagerCompat.from(ctx).cancel(notifId(key))

    private fun action(ctx: Context, action: String, detectedId: String?, extra: String? = null): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).apply {
            this.action = action
            putExtra(ActionReceiver.EXTRA_ID, detectedId)
            putExtra(ActionReceiver.EXTRA_VALUE, extra)
        }
        val code = (action + detectedId + extra).hashCode()
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun openApp(ctx: Context, route: String, code: Int = route.hashCode()): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN
            putExtra(MainActivity.EXTRA_ROUTE, route)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @Suppress("MissingPermission")
    private fun post(ctx: Context, key: String, b: NotificationCompat.Builder) {
        if (!canPost(ctx)) return
        NotificationManagerCompat.from(ctx).notify(notifId(key), b.build())
    }

    private fun base(ctx: Context, channel: String) = NotificationCompat.Builder(ctx, channel)
        .setSmallIcon(R.drawable.ic_stat_coin)
        .setColor(0xFF6C4DF6.toInt())
        .setAutoCancel(true)

    fun showDetected(ctx: Context, det: Detected, suggestedCategory: String?) {
        val d = Store.current
        val amount = Money.format(det.amount)
        val where = if (det.merchant.isNotBlank()) " bei ${det.merchant}" else ""
        val (title, text) = when (det.kind) {
            DetectedKind.EXPENSE -> "💳 $amount$where erkannt" to "Über ${det.sourceName}. Passt die Kategorie?"
            DetectedKind.CASH -> "🏧 $amount abgehoben" to "Ins Portemonnaie stecken?"
            DetectedKind.INCOME -> "💰 $amount eingegangen" + (if (det.merchant.isNotBlank()) " von ${det.merchant}" else "") to
                (if (d.settings.periodMode == PeriodMode.INCOME) "Ist das dein Gehalt? Dann starte ich einen neuen Monat." else "Als Einnahme eintragen?")
            DetectedKind.REFUND -> "↩️ $amount zurück$where" to "Als Erstattung eintragen?"
        }
        val b = base(ctx, CH_DETECTED)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(ctx, "inbox"))
        when (det.kind) {
            DetectedKind.EXPENSE, DetectedKind.REFUND -> {
                val cat = d.category(suggestedCategory)
                if (cat != null) b.addAction(0, "${cat.emoji} ${cat.name}", action(ctx, ActionReceiver.ACCEPT, det.id, cat.id))
                b.addAction(0, if (cat != null) "Was anderes" else "Kategorie wählen", openApp(ctx, "inbox/${det.id}", det.id.hashCode()))
                b.addAction(0, "Nö", action(ctx, ActionReceiver.IGNORE, det.id))
            }
            DetectedKind.CASH -> {
                b.addAction(0, "💶 Ins Portemonnaie", action(ctx, ActionReceiver.CASH_WALLET, det.id))
                b.addAction(0, "War eine Ausgabe", action(ctx, ActionReceiver.CASH_EXPENSE, det.id))
                b.addAction(0, "Nö", action(ctx, ActionReceiver.IGNORE, det.id))
            }
            DetectedKind.INCOME -> {
                if (d.settings.periodMode == PeriodMode.INCOME) {
                    b.addAction(0, "💰 Gehalt, neuer Monat", action(ctx, ActionReceiver.SALARY, det.id))
                }
                b.addAction(0, "Einnahme", action(ctx, ActionReceiver.ACCEPT, det.id, suggestedCategory))
                b.addAction(0, "Nö", action(ctx, ActionReceiver.IGNORE, det.id))
            }
        }
        post(ctx, det.id, b)
    }

    fun showAutoAccepted(ctx: Context, det: Detected, entry: Entry) {
        val cat = Store.current.category(entry.categoryId)
        val b = base(ctx, CH_DETECTED)
            .setContentTitle("✅ ${Money.format(entry.amount)} bei ${entry.merchant} eingetragen")
            .setContentText("${cat?.emoji ?: ""} ${cat?.name ?: ""}, automatisch erkannt")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp(ctx, "home"))
            .addAction(0, "Rückgängig", action(ctx, ActionReceiver.UNDO, det.id, entry.id))
        post(ctx, det.id, b)
    }

    fun reminder(ctx: Context, key: String, title: String, text: String, route: String = "home", actions: List<Triple<String, String, String?>> = emptyList()) {
        val b = base(ctx, CH_REMINDER)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx, route))
        actions.forEach { (label, act, value) -> b.addAction(0, label, action(ctx, act, key, value)) }
        post(ctx, key, b)
    }

    fun info(ctx: Context, key: String, title: String, text: String) {
        post(ctx, key, base(ctx, CH_BACKUP).setContentTitle(title).setContentText(text).setPriority(NotificationCompat.PRIORITY_LOW))
    }
}
