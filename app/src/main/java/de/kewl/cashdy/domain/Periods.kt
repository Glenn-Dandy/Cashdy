package de.kewl.cashdy.domain

import de.kewl.cashdy.data.PeriodChange
import de.kewl.cashdy.data.PeriodMode
import de.kewl.cashdy.data.Settings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Ein "Monat" in Cashdy ist ein Zeitraum von [start] bis [end] (beide einschließlich).
 * Je nach Einstellung ist das der Kalendermonat, ein fester Tag X bis Tag X-1, oder die
 * Spanne von einem Gehaltseingang bis zum nächsten.
 */
data class Period(val start: LocalDate, val end: LocalDate, val estimatedEnd: Boolean = false) {
    operator fun contains(d: LocalDate) = !d.isBefore(start) && !d.isAfter(end)
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
    fun daysLeft(today: LocalDate): Int =
        if (today.isAfter(end)) 0 else ChronoUnit.DAYS.between(maxOf(today, start), end).toInt() + 1
    val key: String get() = start.toString()

    /** Name für die Anzeige: Monat, in dem der größte Teil des Zeitraums liegt. */
    val label: String get() {
        val mid = start.plusDays(ChronoUnit.DAYS.between(start, end) / 2)
        return mid.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN))
            .replaceFirstChar { it.uppercase() }
    }
    val shortLabel: String get() {
        val mid = start.plusDays(ChronoUnit.DAYS.between(start, end) / 2)
        return mid.format(DateTimeFormatter.ofPattern("LLLL", Locale.GERMAN)).replaceFirstChar { it.uppercase() }
    }
    val rangeLabel: String get() {
        val f = DateTimeFormatter.ofPattern("d.M.")
        return "${start.format(f)} bis ${end.format(f)}"
    }
}

object Periods {

    fun periodFor(date: LocalDate, s: Settings): Period {
        if (s.periodChanges.isEmpty()) return byRule(date, s.periodMode, s.periodDay, s.shiftWeekend, s)
        // Wurde der Monatsanfang umgestellt, gilt vor jedem Wechsel die alte Regel. Der Monat,
        // in dem gewechselt wurde, endet am Tag davor, der neue beginnt genau am Wechseltag.
        val changes = s.periodChanges.sortedBy { it.from }
        val i = changes.indexOfFirst { date.isBefore(it.from) }
        val rule = if (i < 0) null else changes[i]
        val p = if (rule == null) byRule(date, s.periodMode, s.periodDay, s.shiftWeekend, s)
            else byRule(date, rule.mode, rule.day, rule.shiftWeekend, s)
        val segStart = if (i == 0) null else changes[(if (i < 0) changes.size else i) - 1].from
        val segEnd = rule?.from?.minusDays(1)
        val start = if (segStart != null && p.start.isBefore(segStart)) segStart else p.start
        val end = if (segEnd != null && p.end.isAfter(segEnd)) segEnd else p.end
        return if (start == p.start && end == p.end) p else Period(start, end, p.estimatedEnd && end == p.end)
    }

    private fun byRule(date: LocalDate, mode: PeriodMode, day: Int, shiftWeekend: Boolean, s: Settings): Period = when (mode) {
        PeriodMode.CALENDAR -> calendar(date)
        PeriodMode.FIXED_DAY -> fixedDay(date, day, shiftWeekend)
        PeriodMode.INCOME -> income(date, s)
    }

    /** Ab [from] beginnt der Monat am [day]. Was davor war, bleibt eingeteilt wie bisher. */
    fun changeStart(s: Settings, from: LocalDate, day: Int): Settings {
        val kept = s.periodChanges.filter { it.from.isBefore(from) }
        val old = PeriodChange(from, s.periodMode, s.periodDay, s.shiftWeekend)
        return s.copy(periodChanges = kept + old, periodMode = PeriodMode.FIXED_DAY, periodDay = day)
    }

    fun previous(p: Period, s: Settings): Period = periodFor(p.start.minusDays(1), s)
    fun next(p: Period, s: Settings): Period = periodFor(p.end.plusDays(1), s)

    fun calendar(d: LocalDate): Period {
        val start = d.withDayOfMonth(1)
        return Period(start, start.plusMonths(1).minusDays(1))
    }

    /** Starttag im Monat von [ym] (clamped auf Monatslänge), optional auf den Werktag davor. */
    fun startInMonth(ym: LocalDate, day: Int, shiftWeekend: Boolean): LocalDate {
        val first = ym.withDayOfMonth(1)
        var d = first.withDayOfMonth(minOf(day.coerceIn(1, 31), first.lengthOfMonth()))
        if (shiftWeekend) {
            while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.minusDays(1)
        }
        return d
    }

    fun fixedDay(d: LocalDate, day: Int, shiftWeekend: Boolean): Period {
        // Gesucht ist der Nennmonat m mit start(m) <= d < start(m+1). Durch die
        // Wochenend-Verschiebung kann start(m+1) noch im Monat m liegen, deshalb wird über
        // die Nennmonate gerechnet und nie vom verschobenen Datum aus weitergezählt.
        val month = d.withDayOfMonth(1)
        for (m in listOf(month.plusMonths(1), month, month.minusMonths(1), month.minusMonths(2))) {
            val start = startInMonth(m, day, shiftWeekend)
            if (!d.isBefore(start)) {
                val nextStart = startInMonth(m.plusMonths(1), day, shiftWeekend)
                return Period(start, nextStart.minusDays(1))
            }
        }
        return calendar(d)
    }

    /**
     * Ab Geldeingang: bestätigte Gehaltseingänge sind die Grenzen. Vor dem ersten bekannten
     * Eingang gilt ersatzweise der feste Tag (periodDay). Der laufende Zeitraum hat noch kein
     * bekanntes Ende; geschätzt wird ein Monat nach dem Start.
     */
    fun income(d: LocalDate, s: Settings): Period {
        val starts = s.periodStarts.sorted()
        val idx = starts.indexOfLast { !it.isAfter(d) }
        if (idx < 0) {
            val fallback = fixedDay(d, s.periodDay, false)
            val firstKnown = starts.firstOrNull()
            return if (firstKnown != null && firstKnown.isBefore(fallback.end.plusDays(1)))
                Period(fallback.start, firstKnown.minusDays(1)) else fallback
        }
        val start = starts[idx]
        val nextKnown = starts.getOrNull(idx + 1)
        if (nextKnown != null) return Period(start, nextKnown.minusDays(1))
        // Nach dem letzten bekannten Eingang: geschätzt monatlich weiter, auch weit in die
        // Zukunft (Jahresansicht, Rücklage). Gezählt wird immer ab dem bekannten Start.
        var k = 0L
        while (!d.isBefore(start.plusMonths(k + 1))) k++
        return Period(start.plusMonths(k), start.plusMonths(k + 1).minusDays(1), estimatedEnd = true)
    }

    /** Die letzten [n] Zeiträume bis einschließlich dem von [today], älteste zuerst. */
    fun lastN(today: LocalDate, s: Settings, n: Int): List<Period> {
        val out = ArrayDeque<Period>()
        var p = periodFor(today, s)
        repeat(n) {
            out.addFirst(p)
            p = previous(p, s)
        }
        return out.toList()
    }
}
