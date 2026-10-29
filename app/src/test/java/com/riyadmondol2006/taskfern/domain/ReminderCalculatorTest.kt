package com.riyadmondol2006.taskfern.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderCalculatorTest {
    private val nine = LocalTime.of(9, 0)
    private val berlin = ZoneId.of("Europe/Berlin")
    private val newYork = ZoneId.of("America/New_York")

    @Test fun `reminder at due time and before it`() {
        val now = ZonedDateTime.of(2026, 10, 29, 8, 0, 0, 0, berlin).toInstant()
        val next = ReminderCalculator.nextReminder(LocalDate.of(2026, 10, 29), LocalTime.of(14, 0), listOf(0, 60), null, nine, now, berlin)
        assertEquals(ZonedDateTime.of(2026, 10, 29, 13, 0, 0, 0, berlin).toInstant(), next)
    }

    @Test fun `date without time uses the default reminder time`() {
        val now = ZonedDateTime.of(2026, 10, 29, 7, 0, 0, 0, berlin).toInstant()
        val next = ReminderCalculator.nextReminder(LocalDate.of(2026, 10, 29), null, listOf(0), null, nine, now, berlin)
        assertEquals(ZonedDateTime.of(2026, 10, 29, 9, 0, 0, 0, berlin).toInstant(), next)
    }

    @Test fun `past reminders are not returned`() {
        val now = ZonedDateTime.of(2026, 10, 29, 15, 0, 0, 0, berlin).toInstant()
        assertNull(ReminderCalculator.nextReminder(LocalDate.of(2026, 10, 29), LocalTime.of(14, 0), listOf(0, 60), null, nine, now, berlin))
    }

    @Test fun `snooze wins when it is sooner`() {
        val now = ZonedDateTime.of(2026, 10, 29, 8, 0, 0, 0, berlin).toInstant()
        val snooze = now.plusSeconds(15 * 60)
        val next = ReminderCalculator.nextReminder(LocalDate.of(2026, 10, 30), LocalTime.of(9, 0), listOf(0), snooze, nine, now, berlin)
        assertEquals(snooze, next)
    }

    @Test fun `reminder stays at wall clock time across daylight saving change`() {
        // Europe/Berlin leaves DST on 2026-10-25 at 03:00 -> 02:00.
        val before = ReminderCalculator.reminderInstants(LocalDate.of(2026, 10, 24), nine, listOf(0), nine, berlin).single()
        val after = ReminderCalculator.reminderInstants(LocalDate.of(2026, 10, 26), nine, listOf(0), nine, berlin).single()
        assertEquals(9, before.atZone(berlin).hour)
        assertEquals(9, after.atZone(berlin).hour)
        // 2 days apart on the wall clock = 49 hours of real time because of the extra hour.
        assertEquals(49 * 3600L, after.epochSecond - before.epochSecond)
    }

    @Test fun `a time inside the spring-forward gap moves forward`() {
        // 2026-03-29 02:30 does not exist in Berlin (clocks jump 02:00 -> 03:00).
        val instant = ReminderCalculator.reminderInstants(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), listOf(0), nine, berlin).single()
        assertEquals(LocalTime.of(3, 30), instant.atZone(berlin).toLocalTime())
    }

    @Test fun `a time inside the fall-back overlap uses the earlier instant`() {
        // 2026-11-01 01:30 happens twice in New York; the first one (EDT, -04:00) is used.
        val instant = ReminderCalculator.reminderInstants(LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), listOf(0), nine, newYork).single()
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), instant)
    }

    @Test fun `changing time zone keeps the local wall-clock time`() {
        val date = LocalDate.of(2026, 10, 29)
        val inBerlin = ReminderCalculator.reminderInstants(date, nine, listOf(0), nine, berlin).single()
        val inNewYork = ReminderCalculator.reminderInstants(date, nine, listOf(0), nine, newYork).single()
        assertEquals(9, inBerlin.atZone(berlin).hour)
        assertEquals(9, inNewYork.atZone(newYork).hour)
    }

    @Test fun `routine reminder finds the next scheduled weekday`() {
        // Thursday 2026-10-29 10:00; routine on Mondays at 07:30.
        val now = ZonedDateTime.of(2026, 10, 29, 10, 0, 0, 0, berlin).toInstant()
        val mondayMask = 1
        val next = ReminderCalculator.nextRoutineReminder(mondayMask, 7 * 60 + 30, now, berlin)
        assertEquals(ZonedDateTime.of(2026, 11, 2, 7, 30, 0, 0, berlin).toInstant(), next)
    }

    @Test fun `routine reminder later today`() {
        val now = ZonedDateTime.of(2026, 10, 29, 10, 0, 0, 0, berlin).toInstant()
        val next = ReminderCalculator.nextRoutineReminder(0x7F, 20 * 60, now, berlin)
        assertEquals(ZonedDateTime.of(2026, 10, 29, 20, 0, 0, 0, berlin).toInstant(), next)
    }

    @Test fun `routine without days has no reminder`() =
        assertNull(ReminderCalculator.nextRoutineReminder(0, 600, Instant.EPOCH, berlin))
}
