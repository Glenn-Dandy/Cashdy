package de.kewl.cashdy.domain

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

    fun periodFor(date: LocalDate, s: Settings): Period = when (s.periodMode) {
        PeriodMode.CALENDAR -> calendar(date)
        PeriodMode.FIXED_DAY -> fixedDay(date, s.periodDay, s.shiftWeekend)
        PeriodMode.INCOME -> income(date, s)
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
        return if (nextKnown != null) Period(start, nextKnown.minusDays(1))
        else Period(start, start.plusMonths(1).minusDays(1), estimatedEnd = true)
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
