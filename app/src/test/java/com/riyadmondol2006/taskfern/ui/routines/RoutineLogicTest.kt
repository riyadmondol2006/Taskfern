package com.riyadmondol2006.taskfern.ui.routines

import com.riyadmondol2006.taskfern.domain.WeekdayMask
import com.riyadmondol2006.taskfern.domain.DayState
import com.riyadmondol2006.taskfern.domain.RoutineHistory
import com.riyadmondol2006.taskfern.domain.firstGrapheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeekdayMaskTest {

    @Test fun bitsFollowMondayFirst() {
        assertEquals(1, WeekdayMask.bit(DayOfWeek.MONDAY))
        assertEquals(64, WeekdayMask.bit(DayOfWeek.SUNDAY))
        assertTrue(WeekdayMask.contains(WeekdayMask.WEEKDAYS, DayOfWeek.FRIDAY))
        assertFalse(WeekdayMask.contains(WeekdayMask.WEEKDAYS, DayOfWeek.SATURDAY))
        assertTrue(WeekdayMask.contains(WeekdayMask.WEEKENDS, DayOfWeek.SUNDAY))
    }

    @Test fun runsOnUsesTheDateWeekday() {
        val monday = LocalDate.of(2026, 3, 2)
        assertTrue(WeekdayMask.runsOn(WeekdayMask.WEEKDAYS, monday))
        assertFalse(WeekdayMask.runsOn(WeekdayMask.WEEKDAYS, monday.plusDays(5)))
    }

    @Test fun toggleAddsAndRemovesDays() {
        val mask = WeekdayMask.toggle(0, DayOfWeek.WEDNESDAY)
        assertEquals(4, mask)
        assertEquals(0, WeekdayMask.toggle(mask, DayOfWeek.WEDNESDAY))
        assertEquals(WeekdayMask.EVERY_DAY, WeekdayMask.toggle(WeekdayMask.WEEKDAYS or WeekdayMask.bit(DayOfWeek.SATURDAY), DayOfWeek.SUNDAY))
    }

    @Test fun recognisesPresets() {
        assertEquals(WeekdayMask.Preset.EVERY_DAY, WeekdayMask.preset(0x7F))
        assertEquals(WeekdayMask.Preset.WEEKDAYS, WeekdayMask.preset(0x1F))
        assertEquals(WeekdayMask.Preset.WEEKENDS, WeekdayMask.preset(0x60))
        assertNull(WeekdayMask.preset(0x15))
        assertEquals(3, WeekdayMask.count(0x15))
    }

    @Test fun weekOrderFollowsTheSetting() {
        assertEquals(DayOfWeek.MONDAY, WeekdayMask.week(true).first())
        assertEquals(DayOfWeek.SUNDAY, WeekdayMask.week(false).first())
        assertEquals(DayOfWeek.SATURDAY, WeekdayMask.week(false).last())
        assertEquals(
            listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            WeekdayMask.days(WeekdayMask.bit(DayOfWeek.MONDAY) or WeekdayMask.bit(DayOfWeek.FRIDAY) or WeekdayMask.bit(DayOfWeek.SUNDAY), false),
        )
    }

    @Test fun nextRunFindsTheNextScheduledDay() {
        val wednesday = LocalDate.of(2026, 3, 4)
        assertEquals(wednesday, WeekdayMask.nextRun(WeekdayMask.EVERY_DAY, wednesday))
        assertEquals(LocalDate.of(2026, 3, 7), WeekdayMask.nextRun(WeekdayMask.WEEKENDS, wednesday))
        assertNull(WeekdayMask.nextRun(0, wednesday))
    }
}

class RoutineHistoryTest {
    // Wednesday 4 March 2026.
    private val today = LocalDate.of(2026, 3, 4)
    private fun d(daysAgo: Long) = today.minusDays(daysAgo)
    private fun done(vararg days: Long, count: Int = 3) = days.associate { d(it) to count }

    @Test fun dayStates() {
        val mask = WeekdayMask.EVERY_DAY
        assertEquals(DayState.FUTURE, RoutineHistory.dayState(today.plusDays(1), today, mask, 0, 3, null))
        assertEquals(DayState.BEFORE_START, RoutineHistory.dayState(d(5), today, mask, 0, 3, d(2)))
        assertEquals(DayState.DONE, RoutineHistory.dayState(d(1), today, mask, 3, 3, null))
        assertEquals(DayState.PARTIAL, RoutineHistory.dayState(d(1), today, mask, 1, 3, null))
        assertEquals(DayState.MISSED, RoutineHistory.dayState(d(1), today, mask, 0, 3, null))
        assertEquals(DayState.TODAY_OPEN, RoutineHistory.dayState(today, today, mask, 1, 3, null))
        assertEquals(DayState.DONE, RoutineHistory.dayState(today, today, mask, 3, 3, null))
        // Tuesday is not a weekend day.
        assertEquals(DayState.NOT_SCHEDULED, RoutineHistory.dayState(d(1), today, WeekdayMask.WEEKENDS, 0, 3, null))
        // Doing the whole routine on a day off still shows as done.
        assertEquals(DayState.DONE, RoutineHistory.dayState(d(1), today, WeekdayMask.WEEKENDS, 3, 3, null))
    }

    @Test fun streakCountsBackFromToday() {
        assertEquals(3, RoutineHistory.currentStreak(today, WeekdayMask.EVERY_DAY, done(0, 1, 2), 3, null))
    }

    @Test fun openTodayDoesNotBreakTheStreak() {
        assertEquals(2, RoutineHistory.currentStreak(today, WeekdayMask.EVERY_DAY, done(1, 2), 3, null))
        assertEquals(2, RoutineHistory.currentStreak(today, WeekdayMask.EVERY_DAY, done(1, 2) + (today to 1), 3, null))
    }

    @Test fun aMissedDayEndsTheStreak() {
        assertEquals(1, RoutineHistory.currentStreak(today, WeekdayMask.EVERY_DAY, done(1, 3, 4), 3, null))
        assertEquals(0, RoutineHistory.currentStreak(today, WeekdayMask.EVERY_DAY, done(2, 3), 3, null))
    }

    @Test fun partialDaysDoNotCount() {
        assertEquals(0, RoutineHistory.currentStreak(today, WeekdayMask.EVERY_DAY, done(1, 2, count = 2), 3, null))
    }

    @Test fun daysOffAreSkipped() {
        // Weekdays only: today is Wednesday; Mon, Tue done, then the previous Fri and Thu, with the weekend between.
        val doneDays = done(1, 2, 5, 6)
        assertEquals(4, RoutineHistory.currentStreak(today, WeekdayMask.WEEKDAYS, doneDays, 3, null))
    }

    @Test fun streakStopsAtTheStartDate() {
        assertEquals(2, RoutineHistory.currentStreak(today, WeekdayMask.EVERY_DAY, done(0, 1, 2, 3), 3, startDate = d(1)))
    }

    @Test fun noStepsOrNoDaysMeansNoStreak() {
        assertEquals(0, RoutineHistory.currentStreak(today, WeekdayMask.EVERY_DAY, done(0, 1), 0, null))
        assertEquals(0, RoutineHistory.currentStreak(today, 0, done(0, 1), 3, null))
    }

    @Test fun lookbackLimitsTheStreak() {
        val all = (0L until 30L).associate { d(it) to 3 }
        assertEquals(10, RoutineHistory.currentStreak(today, WeekdayMask.EVERY_DAY, all, 3, null, lookbackDays = 10))
    }

    @Test fun bestStreakFindsTheLongestRun() {
        val doneDays = done(1, 2, 4, 5, 6, 7, 9)
        assertEquals(4, RoutineHistory.bestStreak(d(10), today, WeekdayMask.EVERY_DAY, doneDays, 3, null))
        assertEquals(2, RoutineHistory.bestStreak(d(10), today, WeekdayMask.EVERY_DAY, doneDays, 3, startDate = d(2)))
        assertEquals(0, RoutineHistory.bestStreak(d(10), today, WeekdayMask.EVERY_DAY, emptyMap(), 3, null))
    }

    @Test fun completionRateIgnoresOpenToday() {
        // Last 4 days (d3..today) every day: d3, d2 done; d1 missed; today open → 2 of 3.
        val rate = RoutineHistory.completionRate(d(3), today, WeekdayMask.EVERY_DAY, done(2, 3), 3, null)!!
        assertEquals(2f / 3f, rate, 0.0001f)
        // Once today is done it counts: 3 of 4.
        val done = RoutineHistory.completionRate(d(3), today, WeekdayMask.EVERY_DAY, done(0, 2, 3), 3, null)!!
        assertEquals(0.75f, done, 0.0001f)
    }

    @Test fun completionRateIsNullWithoutScheduledDays() {
        assertNull(RoutineHistory.completionRate(today, today, WeekdayMask.EVERY_DAY, emptyMap(), 3, null))
        assertNull(RoutineHistory.completionRate(d(3), today, WeekdayMask.EVERY_DAY, emptyMap(), 0, null))
    }

    @Test fun weekRowsEndWithTheCurrentWeek() {
        val rows = RoutineHistory.weekRows(today, 5, weekStartsMonday = true)
        assertEquals(5, rows.size)
        assertTrue(rows.all { it.size == 7 })
        assertEquals(LocalDate.of(2026, 3, 2), rows.last().first())
        assertEquals(LocalDate.of(2026, 3, 8), rows.last().last())
        assertEquals(LocalDate.of(2026, 2, 2), rows.first().first())
        val sundayRows = RoutineHistory.weekRows(today, 1, weekStartsMonday = false)
        assertEquals(LocalDate.of(2026, 3, 1), sundayRows.single().first())
    }

    @Test fun historyMapsEveryDate() {
        val days = RoutineHistory.history(listOf(d(1), today, today.plusDays(1)), today, WeekdayMask.EVERY_DAY, done(1), 3, null)
        assertEquals(listOf(DayState.DONE, DayState.TODAY_OPEN, DayState.FUTURE), days.map { it.state })
        assertEquals(1f, days.first().ratio, 0f)
    }
}

class FirstGraphemeTest {
    @Test fun takesTheFirstCharacter() {
        assertEquals("🌿", firstGrapheme("🌿🌱"))
        assertEquals("A", firstGrapheme("  Abc"))
        assertEquals("", firstGrapheme("   "))
    }
}
