package com.riyadmondol2006.taskfern.domain

import com.riyadmondol2006.taskfern.data.db.DueScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanPeriodsTest {
    private val thu = LocalDate.of(2026, 10, 29)

    @Test fun `period ends`() {
        assertEquals(LocalDate.of(2026, 11, 1), PlanPeriods.endOf(DueScope.WEEK, thu, weekStartsMonday = true))
        assertEquals(LocalDate.of(2026, 10, 31), PlanPeriods.endOf(DueScope.WEEK, thu, weekStartsMonday = false))
        assertEquals(LocalDate.of(2026, 10, 31), PlanPeriods.endOf(DueScope.MONTH, thu))
        assertEquals(LocalDate.of(2026, 12, 31), PlanPeriods.endOf(DueScope.YEAR, thu))
        assertEquals(LocalDate.of(2028, 2, 29), PlanPeriods.endOf(DueScope.MONTH, LocalDate.of(2028, 2, 3)))
    }

    @Test fun `this and next period`() {
        assertEquals(LocalDate.of(2026, 11, 8), PlanPeriods.end(DueScope.WEEK, thu, 1))
        assertEquals(LocalDate.of(2026, 11, 30), PlanPeriods.end(DueScope.MONTH, thu, 1))
        assertEquals(LocalDate.of(2027, 12, 31), PlanPeriods.end(DueScope.YEAR, thu, 1))
        // Next month from 31 January lands in February, not March.
        assertEquals(LocalDate.of(2027, 2, 28), PlanPeriods.end(DueScope.MONTH, LocalDate.of(2027, 1, 31), 1))
    }

    @Test fun `labels relative to today`() {
        assertEquals(PlanPeriods.Relative.THIS, PlanPeriods.label(DueScope.WEEK, LocalDate.of(2026, 11, 1), thu).relative)
        assertEquals(PlanPeriods.Relative.NEXT, PlanPeriods.label(DueScope.WEEK, LocalDate.of(2026, 11, 8), thu).relative)
        assertEquals(PlanPeriods.Relative.OTHER, PlanPeriods.label(DueScope.WEEK, LocalDate.of(2026, 11, 15), thu).relative)
        assertEquals(PlanPeriods.Relative.THIS, PlanPeriods.label(DueScope.MONTH, LocalDate.of(2026, 10, 31), thu).relative)
        assertEquals(PlanPeriods.Relative.NEXT, PlanPeriods.label(DueScope.YEAR, LocalDate.of(2027, 12, 31), thu).relative)
    }

    @Test fun `current period check`() {
        assertTrue(PlanPeriods.isCurrent(DueScope.MONTH, LocalDate.of(2026, 10, 31), thu))
        assertFalse(PlanPeriods.isCurrent(DueScope.MONTH, LocalDate.of(2026, 9, 30), thu))
        assertFalse(PlanPeriods.isCurrent(DueScope.MONTH, LocalDate.of(2026, 11, 30), thu))
    }
}
