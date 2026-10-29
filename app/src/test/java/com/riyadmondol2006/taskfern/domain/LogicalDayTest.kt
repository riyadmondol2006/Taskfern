package com.riyadmondol2006.taskfern.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class LogicalDayTest {
    private val oct29 = LocalDate.of(2026, 10, 29)

    @After fun reset() { LogicalDay.startMinutes = 0 }

    @Test fun `midnight start is the calendar day`() {
        assertEquals(oct29, LogicalDay.dateAt(LocalDateTime.of(2026, 10, 29, 0, 0), 0))
        assertEquals(oct29, LogicalDay.dateAt(LocalDateTime.of(2026, 10, 29, 23, 59), 0))
    }

    @Test fun `with a 3 AM start the small hours belong to yesterday`() {
        assertEquals(oct29.minusDays(1), LogicalDay.dateAt(LocalDateTime.of(2026, 10, 29, 1, 30), 180))
        assertEquals(oct29.minusDays(1), LogicalDay.dateAt(LocalDateTime.of(2026, 10, 29, 2, 59), 180))
        assertEquals(oct29, LogicalDay.dateAt(LocalDateTime.of(2026, 10, 29, 3, 0), 180))
    }

    @Test fun `day start instant and next start`() {
        val zone = ZoneId.of("UTC")
        assertEquals(LocalDateTime.of(2026, 10, 29, 3, 0), LogicalDay.startOf(oct29, zone, 180).atZone(zone).toLocalDateTime())
        assertEquals(LocalDateTime.of(2026, 10, 30, 3, 0), LogicalDay.nextStart(LocalDateTime.of(2026, 10, 29, 23, 0), 180))
        assertEquals(LocalDateTime.of(2026, 10, 29, 3, 0), LogicalDay.nextStart(LocalDateTime.of(2026, 10, 29, 1, 0), 180))
        assertEquals(LocalDateTime.of(2026, 10, 30, 0, 0), LogicalDay.nextStart(LocalDateTime.of(2026, 10, 29, 12, 0), 0))
    }

    @Test fun `start is clamped to noon`() {
        LogicalDay.startMinutes = 5_000
        assertEquals(LogicalDay.MAX_START_MINUTES, LogicalDay.startMinutes)
        LogicalDay.startMinutes = -10
        assertEquals(0, LogicalDay.startMinutes)
    }
}
