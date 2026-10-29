package com.riyadmondol2006.taskfern.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Works out when a task should next remind. Pure: the caller supplies the clock and zone.
 *
 * A task's reminders are offsets (minutes before) from its due moment. A task due on a date without a time is "due" at
 * the user's default reminder time on that date. Times are wall-clock times in the device's current zone, so a 9:00
 * reminder stays at 9:00 after travelling or a daylight-saving change. In a DST gap (e.g. 02:30 that does not exist)
 * java.time moves the moment forward by the gap length; in an overlap the earlier offset is used.
 */
object ReminderCalculator {

    fun dueMoment(date: LocalDate, time: LocalTime?, defaultTime: LocalTime, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(date, time ?: defaultTime, zone)

    /** Every reminder instant for the occurrence due on [date]. */
    fun reminderInstants(
        date: LocalDate,
        time: LocalTime?,
        offsetsMinutes: Collection<Int>,
        defaultTime: LocalTime,
        zone: ZoneId,
    ): List<Instant> {
        val due = dueMoment(date, time, defaultTime, zone)
        return offsetsMinutes.distinct().map { due.minusMinutes(it.toLong()).toInstant() }.sorted()
    }

    /**
     * The next reminder strictly after [now], considering a snooze. Returns null when nothing is left to remind about
     * (no due date, no reminders, or all reminder times have passed).
     */
    fun nextReminder(
        date: LocalDate?,
        time: LocalTime?,
        offsetsMinutes: Collection<Int>,
        snoozeUntil: Instant?,
        defaultTime: LocalTime,
        now: Instant,
        zone: ZoneId,
    ): Instant? {
        val candidates = buildList {
            if (snoozeUntil != null && snoozeUntil.isAfter(now)) add(snoozeUntil)
            if (date != null && offsetsMinutes.isNotEmpty()) {
                reminderInstants(date, time, offsetsMinutes, defaultTime, zone).filterTo(this) { it.isAfter(now) }
            }
        }
        return candidates.minOrNull()
    }

    /** Next moment a routine scheduled at [minuteOfDay] on the given weekdays should remind, strictly after [now]. */
    fun nextRoutineReminder(
        weekdaysMask: Int,
        minuteOfDay: Int,
        now: Instant,
        zone: ZoneId,
    ): Instant? {
        if (weekdaysMask == 0) return null
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        var day = now.atZone(zone).toLocalDate()
        repeat(8) {
            if (weekdaysMask and (1 shl (day.dayOfWeek.value - 1)) != 0) {
                val at = ZonedDateTime.of(day, time, zone).toInstant()
                if (at.isAfter(now)) return at
            }
            day = day.plusDays(1)
        }
        return null
    }
}
