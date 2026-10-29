package com.riyadmondol2006.taskfern.ui.settings

import com.riyadmondol2006.taskfern.data.db.RoutineDayProgress
import com.riyadmondol2006.taskfern.data.db.RoutineEntity
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class InsightsMathTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val thursday = LocalDate.of(2026, 10, 29)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `week start follows the setting`() {
        assertEquals(LocalDate.of(2026, 10, 26), InsightsMath.weekStart(thursday, weekStartsMonday = true))
        assertEquals(LocalDate.of(2026, 10, 25), InsightsMath.weekStart(thursday, weekStartsMonday = false))
        val monday = LocalDate.of(2026, 10, 26)
        assertEquals(monday, InsightsMath.weekStart(monday, true))
        val sunday = LocalDate.of(2026, 10, 25)
        assertEquals(sunday, InsightsMath.weekStart(sunday, false))
        assertEquals(LocalDate.of(2026, 10, 19), InsightsMath.weekStart(sunday, true))
    }

    @Test
    fun `week has seven consecutive days`() {
        val days = InsightsMath.weekDays(LocalDate.of(2026, 12, 28))
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2027, 1, 3), days.last())
    }

    @Test
    fun `completions are bucketed by local day, including around midnight`() {
        val week = InsightsMath.weekDays(LocalDate.of(2026, 10, 26))
        val times = listOf(
            at(LocalDate.of(2026, 10, 26), 0, 1),
            at(LocalDate.of(2026, 10, 26), 23, 59),
            at(LocalDate.of(2026, 10, 27), 12),
            at(thursday, 7), at(thursday, 8), at(thursday, 22),
            at(LocalDate.of(2026, 10, 25), 23, 59), // before the week
        )
        assertEquals(listOf(2, 1, 0, 3, 0, 0, 0), InsightsMath.completionsPerDay(times, week, zone))
        assertEquals(3, InsightsMath.completionsOn(times, thursday, zone))
        assertEquals(0, InsightsMath.completionsOn(emptyList(), thursday, zone))
    }

    @Test
    fun `daylight saving change does not move completions to another day`() {
        // Europe/Berlin leaves summer time on 25 October 2026 at 03:00.
        val sunday = LocalDate.of(2026, 10, 25)
        val week = InsightsMath.weekDays(LocalDate.of(2026, 10, 19))
        val times = listOf(at(sunday, 2, 30), at(sunday, 23, 30))
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 2), InsightsMath.completionsPerDay(times, week, zone))
        assertEquals(at(sunday, 0), InsightsMath.startOfDayMillis(sunday, zone))
    }

    @Test
    fun `routine week counts scheduled and fully done days up to today`() {
        val week = InsightsMath.weekDays(LocalDate.of(2026, 10, 26)) // Mon..Sun, today = Thu
        val created = at(LocalDate.of(2026, 10, 1), 9)
        val daily = RoutineEntity(id = 1, name = "Morning", weekdays = 0x7F, createdAt = created)
        val weekdaysOnly = RoutineEntity(id = 2, name = "Work", weekdays = RecurrenceRule.WEEKDAYS_MASK, createdAt = created)
        val paused = RoutineEntity(id = 3, name = "Paused", paused = true, createdAt = created)
        val empty = RoutineEntity(id = 4, name = "No steps", createdAt = created)
        val newThisWeek = RoutineEntity(
            id = 5, name = "New", weekdays = RecurrenceRule.weekdayMask(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
            createdAt = at(LocalDate.of(2026, 10, 28), 10),
        )
        val steps = mapOf(1L to 3, 2L to 2, 3L to 1, 5L to 1)
        val progress = listOf(
            RoutineDayProgress(1, LocalDate.of(2026, 10, 26), 3), // done
            RoutineDayProgress(1, LocalDate.of(2026, 10, 27), 2), // partial
            RoutineDayProgress(1, thursday, 3), // done
            RoutineDayProgress(2, LocalDate.of(2026, 10, 28), 2), // done
            RoutineDayProgress(3, thursday, 1), // paused: ignored
            RoutineDayProgress(5, LocalDate.of(2026, 10, 28), 1), // done on its first day
            RoutineDayProgress(1, LocalDate.of(2026, 10, 31), 3), // future: ignored
        )
        val result = InsightsMath.routineWeek(listOf(daily, weekdaysOnly, paused, empty, newThisWeek), steps, progress, week, thursday, zone)
        // Scheduled: daily Mon–Thu (4) + weekdays Mon–Thu (4) + new on Wed (1; Tue was before it existed) = 9.
        assertEquals(RoutineWeek(completedDays = 4, scheduledDays = 9), result)
    }

    @Test
    fun `no routines means nothing scheduled`() {
        val week = InsightsMath.weekDays(LocalDate.of(2026, 10, 26))
        assertEquals(RoutineWeek(0, 0), InsightsMath.routineWeek(emptyList(), emptyMap(), emptyList(), week, thursday, zone))
    }

    @Test
    fun `focus minutes round down and never go negative`() {
        assertEquals(0L, InsightsMath.focusMinutes(59))
        assertEquals(25L, InsightsMath.focusMinutes(25 * 60 + 59))
        assertEquals(0L, InsightsMath.focusMinutes(-30))
    }
}
