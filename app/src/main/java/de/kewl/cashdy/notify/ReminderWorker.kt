package de.kewl.cashdy.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.kewl.cashdy.backup.Backup
import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.CashMode
import de.kewl.cashdy.data.PeriodMode
import de.kewl.cashdy.data.Store
import de.kewl.cashdy.domain.Budget
import de.kewl.cashdy.domain.Money
import de.kewl.cashdy.domain.Periods
import de.kewl.cashdy.domain.Recurrence
import de.kewl.cashdy.domain.Salary
import de.kewl.cashdy.widget.CashdyWidget
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

data class Reminder(val key: String, val title: String, val text: String, val route: String = "home", val salaryQuestion: Boolean = false)

/**
 * Läuft einmal am Tag (gegen 19 Uhr) und entscheidet, ob es etwas zu sagen gibt.
 * Jede Art von Erinnerung höchstens einmal pro Anlass; der Ton bleibt freundlich.
 */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        Store.init(applicationContext)
        val today = LocalDate.now()
        val due = reminders(Store.current, today, System.currentTimeMillis())
        for (r in due) {
            Notify.reminder(
                applicationContext, r.key, r.title, r.text, r.route,
                if (r.salaryQuestion) listOf(
                    Triple("Ja, neuer Monat", ActionReceiver.SALARY_YES, null),
                    Triple("Noch nicht", ActionReceiver.SALARY_NO, null),
                ) else emptyList(),
            )
        }
        if (due.isNotEmpty()) Store.update { d ->
            d.copy(settings = d.settings.copy(reminderLog = d.settings.reminderLog + due.associate { it.key.substringBefore('#') to it.key }))
        }
        val s = Store.current.settings
        if (s.autoBackup && s.webdavUrl.isNotBlank() && System.currentTimeMillis() - s.lastBackupAt > 20 * 3_600_000L) {
            Backup.uploadWebDav().onFailure {
                Notify.info(applicationContext, "backup", "Sicherung hat nicht geklappt", it.message ?: "Unbekannter Fehler")
            }
        }
        CashdyWidget.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "daily-reminders"

        fun schedule(ctx: Context) {
            val now = LocalDateTime.now()
            var next = now.withHour(19).withMinute(0).withSecond(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes()
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        /**
         * Welche Erinnerungen heute fällig sind. Der Schlüssel enthält den Anlass
         * ("due#<id>#<datum>"), der Teil vor dem ersten # ist die Art; im Log steht je Art
         * der zuletzt verschickte Anlass, damit nichts doppelt kommt.
         */
        fun reminders(d: AppData, today: LocalDate, now: Long): List<Reminder> {
            val s = d.settings
            if (!s.onboarded) return emptyList()
            val log = s.reminderLog
            val out = mutableListOf<Reminder>()
            fun once(key: String, r: () -> Reminder) {
                if (log[key.substringBefore('#')] != key) out += r()
            }

            // Lange nichts eingetragen
            if (s.remindInactiveDays > 0) {
                val last = d.entries.maxOfOrNull { it.createdAt } ?: 0L
                val lastDay = if (last == 0L) null else Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault()).toLocalDate()
                if (lastDay != null && !lastDay.plusDays(s.remindInactiveDays.toLong()).isAfter(today)) {
                    val days = java.time.temporal.ChronoUnit.DAYS.between(lastDay, today)
                    once("inactive#$lastDay") {
                        Reminder("inactive#$lastDay", "Alles im Griff? 🙂", "Du hast seit $days Tagen nichts eingetragen. Ein Tipp auf ＋ und fertig.")
                    }
                }
            }

            // Morgen fällig
            if (s.remindDue) {
                val tomorrow = today.plusDays(1)
                d.recurring.filter { !it.income }.forEach { r ->
                    if (Recurrence.dueDates(r, tomorrow, tomorrow).isNotEmpty()) {
                        val key = "due-${r.id}#$tomorrow"
                        once(key) {
                            val extra = if (r.spread) " Dafür hast du schon zurückgelegt. 🐷" else ""
                            Reminder(key, "📅 Morgen: ${r.name}", "${Money.format(Recurrence.priceAt(r, tomorrow))} werden fällig.$extra", "fixed")
                        }
                    }
                }
            }

            val p = Periods.periodFor(today, s)
            val sum = Budget.summary(d, p, today)

            // Wird knapp
            if (s.remindLow && sum.income > 0 && sum.free < s.lowThreshold) {
                once("low#${p.key}") {
                    Reminder("low#${p.key}", "Hui, das wird knapp 😬", "Bis ${p.end.dayOfMonth}.${p.end.monthValue}. sind noch ${Money.format(sum.free)} frei.")
                }
            }

            // Monatsende
            if (s.remindPeriodEnd && today == p.end && !p.estimatedEnd) {
                once("end#${p.key}") {
                    val t = if (sum.free >= 0) "Dein ${p.shortLabel}: ${Money.format(sum.free)} übrig 🎉" else "Dein ${p.shortLabel}: ${Money.format(-sum.free)} drüber"
                    Reminder("end#${p.key}", t, if (sum.free >= 0) "Stark! Schau dir an, wohin das Geld ging." else "Kein Drama. Schau mal, wo es hakt.", "month")
                }
            }

            // Gehalt schon da? (nur im Modus "ab Geldeingang")
            if (s.periodMode == PeriodMode.INCOME) {
                val expected = Salary.expectedNext(d, today)
                if (expected != null && !today.isBefore(expected) && today.isBefore(expected.plusDays(7))) {
                    once("salary#$expected") {
                        Reminder("salary#$expected", "💰 Ist dein Gehalt schon da?", "Dann starte ich deinen neuen Monat.", salaryQuestion = true)
                    }
                }
            }

            // Portemonnaie-Abgleich sonntags
            if (s.cashMode == CashMode.WALLET && s.remindWallet && today.dayOfWeek == DayOfWeek.SUNDAY && Budget.wallet(d) > 0) {
                once("wallet#$today") {
                    Reminder("wallet#$today", "💶 Kurzer Bargeld-Check", "Wie viel Bargeld hast du gerade? Laut mir sind es ${Money.format(Budget.wallet(d))}.", "wallet")
                }
            }
            return out
        }
    }
}
