package com.riyadmondol2006.taskfern.domain

import com.riyadmondol2006.taskfern.data.db.OccurrenceAction.COMPLETED
import com.riyadmondol2006.taskfern.data.db.OccurrenceAction.SKIPPED
import com.riyadmondol2006.taskfern.domain.recurrence.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TaskStreaksTest {
    private val today = LocalDate.of(2026, 10, 29)
    private fun d(offset: Long) = today.plusDays(offset)
    private fun done(vararg offsets: Long) = offsets.map { OccurrenceRecord(d(it), COMPLETED) }

    @Test fun `daily habit done five days in a row`() {
        val s = TaskStreaks.compute(RecurrenceRule.daily(), d(-10), done(-5, -4, -3, -2, -1), currentDue = today, today = today)
        assertEquals(Streak(5, 5), s)
    }

    @Test fun `today still open keeps the streak alive`() {
        val s = TaskStreaks.compute(RecurrenceRule.daily(), d(-3), done(-3, -2, -1), currentDue = today, today = today)
        assertEquals(3, s.current)
    }

    @Test fun `a missed day breaks the streak but keeps the best`() {
        // Done -6..-4, missed -3 (completed late on -2 means -3 was never logged), then -2, -1.
        val s = TaskStreaks.compute(RecurrenceRule.daily(), d(-10), done(-6, -5, -4, -2, -1), currentDue = today, today = today)
        assertEquals(Streak(2, 3), s)
    }

    @Test fun `overdue now means no current streak`() {
        val s = TaskStreaks.compute(RecurrenceRule.daily(), d(-10), done(-5, -4, -3), currentDue = d(-2), today = today)
        assertEquals(Streak(0, 3), s)
    }

    @Test fun `skipping breaks the streak`() {
        val log = done(-4, -3) + OccurrenceRecord(d(-2), SKIPPED) + done(-1)
        assertEquals(Streak(1, 2), TaskStreaks.compute(RecurrenceRule.daily(), d(-10), log, today, today))
    }

    @Test fun `weekly on chosen days counts scheduled days only`() {
        // Anchor Monday 2026-10-05, Mon + Thu. Done Mon 19, Thu 22, Mon 26; due Thu 29 (today).
        val rule = RecurrenceRule.weekly(1, DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        val log = listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 26)).map { OccurrenceRecord(it, COMPLETED) }
        assertEquals(Streak(3, 3), TaskStreaks.compute(rule, LocalDate.of(2026, 10, 5), log, today, today))
    }

    @Test fun `repeat after completion allows any gap`() {
        val rule = RecurrenceRule.daily(3).copy(fromCompletion = true)
        val s = TaskStreaks.compute(rule, d(-30), done(-20, -15, -9, -4), currentDue = d(1), today = today)
        assertEquals(4, s.current)
    }

    @Test fun `unlogged occurrence before the current due date breaks it`() {
        // Last done -5; the task was then moved on to tomorrow without completing -4..0 (e.g. skipped by catch-up).
        val s = TaskStreaks.compute(RecurrenceRule.daily(), d(-10), done(-7, -6, -5), currentDue = d(1), today = today)
        assertEquals(Streak(0, 3), s)
    }

    @Test fun `no history no streak`() = assertEquals(Streak.NONE, TaskStreaks.compute(RecurrenceRule.daily(), today, emptyList(), today, today))
}
