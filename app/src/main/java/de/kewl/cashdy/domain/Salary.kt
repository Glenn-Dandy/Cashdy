package de.kewl.cashdy.domain

import de.kewl.cashdy.data.AppData
import de.kewl.cashdy.data.Cents
import de.kewl.cashdy.data.PeriodMode
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

    /** Erwarteter nächster Gehaltstag laut regelmäßiger Einnahme. */
    fun expectedNext(d: AppData, today: LocalDate): LocalDate? {
        val salary = d.recurring.firstOrNull { it.income && it.salary } ?: return null
        val p = Periods.periodFor(today, d.settings)
        // Der nächste Gehaltstag nach Beginn des laufenden Zeitraums.
        return Recurrence.dueDates(salary, p.start.plusDays(10), p.start.plusMonths(2)).firstOrNull()
    }
}
