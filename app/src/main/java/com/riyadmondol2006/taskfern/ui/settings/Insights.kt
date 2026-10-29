package com.riyadmondol2006.taskfern.ui.settings

import com.riyadmondol2006.taskfern.data.db.RoutineDayProgress
import com.riyadmondol2006.taskfern.data.db.RoutineEntity
import com.riyadmondol2006.taskfern.data.repo.RoutineRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Days of the week that had every step of a scheduled routine ticked, out of the routine-days scheduled so far. */
data class RoutineWeek(val completedDays: Int, val scheduledDays: Int)

/** Pure aggregations behind the Insights screen. No Android, no clock: callers pass "today" and the zone. */
object InsightsMath {

    /** The first day of the week containing [today]. */
    fun weekStart(today: LocalDate, weekStartsMonday: Boolean): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(if (weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY))

    fun weekDays(start: LocalDate): List<LocalDate> = (0L until 7L).map { start.plusDays(it) }

    fun startOfDayMillis(day: LocalDate, zone: ZoneId): Long = com.riyadmondol2006.taskfern.domain.LogicalDay.startOf(day, zone).toEpochMilli()

    /** How many completion timestamps fall on each of [days] (local calendar days in [zone]). */
    fun completionsPerDay(times: List<Long>, days: List<LocalDate>, zone: ZoneId): List<Int> {
        val byDay = times.groupingBy { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.eachCount()
        return days.map { byDay[it] ?: 0 }
    }

    fun completionsOn(times: List<Long>, day: LocalDate, zone: ZoneId): Int =
        times.count { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == day }

    /**
     * Routine completion for the days of [week] up to and including [today]. A routine-day counts as scheduled when
     * the routine is active (not paused), runs on that weekday, has at least one step and already existed that day;
     * it counts as completed when all its steps were ticked.
     */
    fun routineWeek(
        routines: List<RoutineEntity>,
        stepCounts: Map<Long, Int>,
        progress: List<RoutineDayProgress>,
        week: List<LocalDate>,
        today: LocalDate,
        zone: ZoneId,
    ): RoutineWeek {
        val done = progress.associate { (it.routineId to it.date) to it.done }
        var scheduled = 0
        var completed = 0
        week.filter { !it.isAfter(today) }.forEach { day ->
            routines.forEach { r ->
                val steps = stepCounts[r.id] ?: 0
                val created = Instant.ofEpochMilli(r.createdAt).atZone(zone).toLocalDate()
                if (r.paused || steps == 0 || created.isAfter(day) || !RoutineRepository.runsOn(r, day)) return@forEach
                scheduled++
                if ((done[r.id to day] ?: 0) >= steps) completed++
            }
        }
        return RoutineWeek(completed, scheduled)
    }

    /** Whole minutes of focus, rounded down. */
    fun focusMinutes(seconds: Long): Long = seconds.coerceAtLeast(0) / 60
}
