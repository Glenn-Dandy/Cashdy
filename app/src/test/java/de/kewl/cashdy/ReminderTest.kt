package de.kewl.cashdy

import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.Entry
import de.kewl.cashdy.data.EntryKind
import de.kewl.cashdy.data.PriceStep
import de.kewl.cashdy.data.Recurring
import de.kewl.cashdy.data.Settings
import de.kewl.cashdy.notify.ReminderWorker
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReminderTest {
    private val today = LocalDate.of(2026, 10, 14)

    @Test fun dueTomorrowAndInactivity() {
        val created = today.minusDays(4).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val d = AppData(
            settings = Settings(onboarded = true),
            recurring = listOf(Recurring("nf", "Netflix", firstDue = LocalDate.of(2026, 1, 15), prices = listOf(PriceStep(LocalDate.of(2026, 1, 15), 1399)))),
            entries = listOf(Entry("e", 500, today.minusDays(4), EntryKind.EXPENSE, createdAt = created)),
        )
        val keys = ReminderWorker.reminders(d, today, 0).map { it.key }
        assertTrue(keys.toString(), keys.any { it.startsWith("due-nf#") })
        assertTrue(keys.toString(), keys.any { it.startsWith("inactive#") })

        // Schon verschickt -> nicht nochmal
        val logged = d.copy(settings = d.settings.copy(reminderLog = ReminderWorker.reminders(d, today, 0).associate { it.key.substringBefore('#') to it.key }))
        assertTrue(ReminderWorker.reminders(logged, today, 0).isEmpty())
    }
}
