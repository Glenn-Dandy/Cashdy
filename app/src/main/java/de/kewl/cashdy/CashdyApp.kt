package de.kewl.cashdy

import android.app.Application
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.notify.Notify
import de.kewl.cashdy.notify.ReminderWorker
import de.kewl.cashdy.widget.CashdyWidget

class CashdyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notify.createChannels(this)
        ReminderWorker.schedule(this)
        // Widget folgt jeder Änderung, egal ob aus der App, einer Benachrichtigung oder dem Hintergrund.
        Store.addListener { runCatching { CashdyWidget.updateAll(this) } }
    }
}
