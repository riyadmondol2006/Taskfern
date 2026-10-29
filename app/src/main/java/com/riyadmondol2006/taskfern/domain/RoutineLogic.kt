package com.riyadmondol2006.taskfern.domain

import java.text.BreakIterator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Helpers for a routine's weekday bit mask (Monday = bit 0 … Sunday = bit 6). */
object WeekdayMask {
    const val EVERY_DAY = 0x7F
    const val WEEKDAYS = 0x1F
    const val WEEKENDS = 0x60

    enum class Preset(val mask: Int) { EVERY_DAY(WeekdayMask.EVERY_DAY), WEEKDAYS(WeekdayMask.WEEKDAYS), WEEKENDS(WeekdayMask.WEEKENDS) }

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0

    fun runsOn(mask: Int, date: LocalDate): Boolean = contains(mask, date.dayOfWeek)

    fun toggle(mask: Int, day: DayOfWeek): Int = (mask xor bit(day)) and EVERY_DAY

    fun count(mask: Int): Int = Integer.bitCount(mask and EVERY_DAY)

    fun preset(mask: Int): Preset? = Preset.entries.firstOrNull { it.mask == mask and EVERY_DAY }

    /** The seven days in display order. */
    fun week(weekStartsMonday: Boolean): List<DayOfWeek> {
        val first = if (weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
        return (0L until 7L).map { first.plus(it) }
    }

    /** The days in [mask], in display order. */
    fun days(mask: Int, weekStartsMonday: Boolean): List<DayOfWeek> = week(weekStartsMonday).filter { contains(mask, it) }

    /** The next date on or after [from] that the mask includes; null for an empty mask. */
    fun nextRun(mask: Int, from: LocalDate): LocalDate? {
        if (mask and EVERY_DAY == 0) return null
        return (0L..6L).map { from.plusDays(it) }.first { runsOn(mask, it) }
    }
}

/** How a single day of a routine looks in the history. */
enum class DayState { FUTURE, BEFORE_START, NOT_SCHEDULED, DONE, PARTIAL, MISSED, TODAY_OPEN }

data class HistoryDay(val date: LocalDate, val state: DayState, val done: Int, val total: Int) {
    val ratio: Float get() = if (total <= 0) 0f else (done.coerceAtMost(total).toFloat() / total)
}

/**
 * Pure history and streak maths for routines. A day counts as complete when every step was ticked. Days the routine is
 * not scheduled on neither extend nor break a streak, and an unfinished *today* does not break it either (there is
 * still time).
 */
object RoutineHistory {

    fun dayState(date: LocalDate, today: LocalDate, mask: Int, done: Int, total: Int, startDate: LocalDate?): DayState = when {
        date.isAfter(today) -> DayState.FUTURE
        startDate != null && date.isBefore(startDate) -> DayState.BEFORE_START
        total > 0 && done >= total -> DayState.DONE
        !WeekdayMask.runsOn(mask, date) || total <= 0 -> DayState.NOT_SCHEDULED
        date == today -> DayState.TODAY_OPEN
        done > 0 -> DayState.PARTIAL
        else -> DayState.MISSED
    }

    /** Consecutive completed scheduled days ending today (or yesterday while today is still open). */
    fun currentStreak(
        today: LocalDate,
        mask: Int,
        doneByDate: Map<LocalDate, Int>,
        total: Int,
        startDate: LocalDate?,
        lookbackDays: Int = 366,
    ): Int {
        if (total <= 0 || mask and WeekdayMask.EVERY_DAY == 0) return 0
        var streak = 0
        for (offset in 0 until lookbackDays) {
            val date = today.minusDays(offset.toLong())
            if (startDate != null && date.isBefore(startDate)) break
            if (!WeekdayMask.runsOn(mask, date)) continue
            val complete = (doneByDate[date] ?: 0) >= total
            when {
                complete -> streak++
                date == today -> continue
                else -> break
            }
        }
        return streak
    }

    /** Longest run of completed scheduled days between [from] and [today], inclusive. */
    fun bestStreak(
        from: LocalDate,
        today: LocalDate,
        mask: Int,
        doneByDate: Map<LocalDate, Int>,
        total: Int,
        startDate: LocalDate?,
    ): Int {
        if (total <= 0 || mask and WeekdayMask.EVERY_DAY == 0) return 0
        var best = 0
        var run = 0
        var date = if (startDate != null && startDate.isAfter(from)) startDate else from
        while (!date.isAfter(today)) {
            if (WeekdayMask.runsOn(mask, date)) {
                val complete = (doneByDate[date] ?: 0) >= total
                if (complete) {
                    run++
                    if (run > best) best = run
                } else if (date != today) {
                    run = 0
                }
            }
            date = date.plusDays(1)
        }
        return best
    }

    /**
     * Share of scheduled days between [from] and [today] that were completed, 0..1. Today only counts once it is done.
     * Null when no scheduled day has passed yet.
     */
    fun completionRate(
        from: LocalDate,
        today: LocalDate,
        mask: Int,
        doneByDate: Map<LocalDate, Int>,
        total: Int,
        startDate: LocalDate?,
    ): Float? {
        if (total <= 0) return null
        var scheduled = 0
        var completed = 0
        var date = if (startDate != null && startDate.isAfter(from)) startDate else from
        while (!date.isAfter(today)) {
            if (WeekdayMask.runsOn(mask, date)) {
                val complete = (doneByDate[date] ?: 0) >= total
                if (date != today || complete) {
                    scheduled++
                    if (complete) completed++
                }
            }
            date = date.plusDays(1)
        }
        return if (scheduled == 0) null else completed.toFloat() / scheduled
    }

    /**
     * The last [weeks] calendar weeks as rows of seven dates, oldest first, the last row containing [today].
     */
    fun weekRows(today: LocalDate, weeks: Int, weekStartsMonday: Boolean): List<List<LocalDate>> {
        val firstDay = if (weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
        val lastWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDay))
        val start = lastWeekStart.minusWeeks((weeks - 1).toLong())
        return (0 until weeks).map { w -> (0L until 7L).map { d -> start.plusWeeks(w.toLong()).plusDays(d) } }
    }

    fun history(
        dates: List<LocalDate>,
        today: LocalDate,
        mask: Int,
        doneByDate: Map<LocalDate, Int>,
        total: Int,
        startDate: LocalDate?,
    ): List<HistoryDay> = dates.map { date ->
        val done = doneByDate[date] ?: 0
        HistoryDay(date, dayState(date, today, mask, done, total, startDate), done, total)
    }
}

/** First user-perceived character of [text] (so "🌿🌱" or a pasted word becomes a single emoji or letter). */
fun firstGrapheme(text: String): String {
    val t = text.trim()
    if (t.isEmpty()) return ""
    val it = BreakIterator.getCharacterInstance()
    it.setText(t)
    val end = it.next()
    return if (end == BreakIterator.DONE) t else t.substring(0, end)
}
