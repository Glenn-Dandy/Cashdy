package de.kewl.cashdy.domain

import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.data.PeriodMode
import de.kewl.cashdy.data.Recurring
import java.time.LocalDate

object Salary {

    /**
     * Gehalt ist da: neuer Zeitraum ab [day]. Weicht der Betrag vom geplanten Gehalt ab,
     * wird er für diesen Zeitraum als Ausnahme hinterlegt ("dieser Monat abweichend").
     */
    fun startNewPeriod(d: AppData, day: LocalDate, amount: Cents?): AppData {
        val s = d.settings
        if (s.periodStarts.any { it == day }) return d
        // Zwei Starts innerhalb weniger Tage sind ein Versehen (doppelt bestätigt).
        if (s.periodStarts.any { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(it, day)) < 10 }) return d
        var out = d.copy(settings = s.copy(periodStarts = (s.periodStarts + day).sorted()))
        if (amount != null && s.periodMode == PeriodMode.INCOME) {
            val salary = out.recurring.firstOrNull { it.income && it.salary }
            if (salary != null && Recurrence.priceAt(salary, day) != amount) {
                out = out.copy(recurring = out.recurring.map {
                    if (it.id == salary.id) it.copy(overrides = it.overrides + (day.toString() to amount)) else it
                })
            }
        }
        return out
    }

    /** Vorschlag: Ab [from] soll der Monat mit dem Hauptgeld [income] anfangen. */
    data class StartSuggestion(val income: Recurring, val from: LocalDate)

    /**
     * Das Hauptgeld ist die monatliche feste Einnahme, die noch läuft: das markierte Gehalt,
     * sonst die größte. Kommt es nicht zum Monatsanfang, schlägt Cashdy vor, den Monat ab dem
     * nächsten Eingang dort beginnen zu lassen. Ein „Nein“ gilt für diese Einnahme und diesen Tag.
     */
    fun suggestStart(d: AppData, today: LocalDate): StartSuggestion? {
        val s = d.settings
        if (!s.onboarded || s.periodMode == PeriodMode.INCOME) return null
        val running = d.recurring.filter { it.income && it.intervalMonths == 1 && (it.endDate == null || it.endDate.isAfter(today)) }
        val main = running.firstOrNull { it.salary } ?: running.maxByOrNull { Recurrence.priceAt(it, today) } ?: return null
        val now = Periods.periodFor(today, s)
        val from = Recurrence.dueDates(main, now.start, now.end.plusMonths(1)).firstOrNull() ?: return null
        // Ein paar Tage früher oder später (Wochenende, Bank) gilt noch als Monatsanfang.
        val start = Periods.periodFor(from, s).start
        if (kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(start, from)) <= 3) return null
        if (s.reminderLog[ASK_KEY] == askValue(main, from)) return null
        return StartSuggestion(main, from)
    }

    /** Ja: ab [from] beginnt der Monat am Tag des Hauptgelds, das ab jetzt als Gehalt zählt. */
    fun acceptStart(d: AppData, sug: StartSuggestion): AppData {
        val s = Periods.changeStart(d.settings, sug.from, sug.from.dayOfMonth)
        return d.copy(
            settings = s.copy(reminderLog = s.reminderLog + (ASK_KEY to askValue(sug.income, sug.from))),
            recurring = d.recurring.map { it.copy(salary = it.id == sug.income.id) },
        )
    }

    /** Nein: für diese Einnahme an diesem Tag nicht mehr fragen. */
    fun declineStart(d: AppData, sug: StartSuggestion): AppData =
        d.copy(settings = d.settings.copy(reminderLog = d.settings.reminderLog + (ASK_KEY to askValue(sug.income, sug.from))))

    private const val ASK_KEY = "monthStartAsk"
    private fun askValue(r: Recurring, from: LocalDate) = "${r.id}#${from.dayOfMonth}"

    /** Erwarteter nächster Gehaltstag laut regelmäßiger Einnahme. */
    fun expectedNext(d: AppData, today: LocalDate): LocalDate? {
        val salary = d.recurring.firstOrNull { it.income && it.salary } ?: return null
        val p = Periods.periodFor(today, d.settings)
        // Der nächste Gehaltstag nach Beginn des laufenden Zeitraums.
        return Recurrence.dueDates(salary, p.start.plusDays(10), p.start.plusMonths(2)).firstOrNull()
    }
}
