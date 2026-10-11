package de.kewl.cashdy.detect

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.notify.Notify

/**
 * Liest Benachrichtigungen der freigegebenen Banking-Apps mit.
 *
 * Alles andere wird sofort verworfen; gespeichert wird nur, was nach einer Zahlung aussieht
 * (und im Rekorder-Modus der Rohtext von Meldungen mit Betrag). Nichts verlässt das Gerät.
 */
class PaymentListenerService : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        Store.init(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        val s = Store.current.settings
        if (sbn.packageName !in s.watchedApps && !s.recorder) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        var result: DetectionResult = DetectionResult.Nothing
        Store.update { d ->
            val (d2, r) = Detection.handle(d, sbn.packageName, title, text, sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis())
            result = r
            Detection.prune(d2, System.currentTimeMillis())
        }
        when (val r = result) {
            is DetectionResult.Pending -> Notify.showDetected(this, r.detected, r.suggestedCategory)
            is DetectionResult.AutoAccepted -> Notify.showAutoAccepted(this, r.detected, r.entry)
            else -> Unit
        }
    }

    companion object {
        fun isEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(ctx, PaymentListenerService::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
