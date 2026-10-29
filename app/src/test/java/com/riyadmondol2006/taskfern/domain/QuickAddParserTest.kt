package com.riyadmondol2006.taskfern.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class QuickAddParserTest {
    private val thursday = LocalDate.of(2026, 10, 29)

    private fun parse(s: String) = QuickAddParser.parse(s, thursday)

    @Test fun `plain text is left alone`() {
        val r = parse("Buy milk")
        assertEquals("Buy milk", r.title)
        assertNull(r.date)
        assertFalse(r.hasMatches)
    }

    @Test fun `tomorrow with a 12-hour time and priority`() {
        val r = parse("Call mum tomorrow at 6pm !!")
        assertEquals("Call mum", r.title)
        assertEquals(thursday.plusDays(1), r.date)
        assertEquals(LocalTime.of(18, 0), r.time)
        assertEquals(2, r.priority)
    }

    @Test fun `24-hour time without a date means today`() {
        val r = parse("Standup 09:30")
        assertEquals("Standup", r.title)
        assertEquals(thursday, r.date)
        assertEquals(LocalTime.of(9, 30), r.time)
    }

    @Test fun `weekday names mean the coming one`() {
        assertEquals(LocalDate.of(2026, 11, 2), parse("Gym monday").date)
        assertEquals(thursday, parse("Report thursday").date) // today counts for a bare weekday
        assertEquals(LocalDate.of(2026, 11, 5), parse("Report next thu").date)
    }

    @Test fun `relative offsets`() {
        assertEquals(thursday.plusDays(3), parse("Renew pass in 3 days").date)
        assertEquals(thursday.plusWeeks(2), parse("Dentist in 2 weeks").date)
        assertEquals(LocalDate.of(2026, 11, 2), parse("Plan next week").date)
    }

    @Test fun `tonight sets an evening time`() {
        val r = parse("Take out trash tonight")
        assertEquals(thursday, r.date)
        assertEquals(LocalTime.of(20, 0), r.time)
        assertEquals("Take out trash", r.title)
    }

    @Test fun `tags are collected`() {
        val r = parse("Book flights #travel #Family")
        assertEquals("Book flights", r.title)
        assertEquals(listOf("travel", "family"), r.tags)
    }

    @Test fun `ambiguous numeric dates are not interpreted`() {
        val r = parse("Pay rent 03/04")
        assertNull(r.date)
        assertEquals("Pay rent 03/04", r.title)
    }

    @Test fun `words inside other words are not dates`() {
        val r = parse("Sunday roast recipe")
        // "Sunday" alone is a weekday; but "Mondays" or "today's" must not match.
        assertEquals(LocalDate.of(2026, 11, 1), r.date)
        assertNull(parse("Update todays notes").date)
        assertNull(parse("Mondays are busy").date)
    }

    @Test fun `invalid times are ignored`() {
        val r = parse("Meeting 25:99")
        assertNull(r.time)
    }

    @Test fun `title never becomes empty`() {
        val r = parse("tomorrow")
        assertEquals("tomorrow", r.title)
    }

    @Test fun `12am and 12pm`() {
        assertEquals(LocalTime.MIDNIGHT, parse("Thing 12am").time)
        assertEquals(LocalTime.NOON, parse("Lunch 12pm").time)
    }
}
