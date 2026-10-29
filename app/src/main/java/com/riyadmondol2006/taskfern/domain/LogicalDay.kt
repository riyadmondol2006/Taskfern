package com.riyadmondol2006.taskfern.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The user's "today". By default a new day starts at midnight; with a later day start (Settings → Tasks → New day
 * starts at), the hours after midnight still belong to the previous day — useful for night owls, so Today, routine
 * ticks, widgets and the nightly refresh do not roll over while they are still up.
 *
 * [startMinutes] is kept up to date from the settings by the app container; it is 0 (midnight) until then and in tests.
 */
object LogicalDay {
    /** Minutes after midnight when a new day begins, 0..[MAX_START_MINUTES]. */
    @Volatile
    var startMinutes: Int = 0
        set(value) {
            field = value.coerceIn(0, MAX_START_MINUTES)
        }

    /** Latest allowed day start: noon. Later would make "today" mostly yesterday. */
    const val MAX_START_MINUTES = 12 * 60 - 1

    fun today(clock: Clock = Clock.systemDefaultZone()): LocalDate = dateAt(LocalDateTime.now(clock), startMinutes)

    fun dateAt(time: LocalDateTime, startMinutes: Int = this.startMinutes): LocalDate =
        time.minusMinutes(startMinutes.toLong()).toLocalDate()

    /** The moment [day] begins, e.g. 03:00 on that date when the day starts at 3 AM. */
    fun startOf(day: LocalDate, zone: ZoneId = ZoneId.systemDefault(), startMinutes: Int = this.startMinutes): Instant =
        day.atStartOfDay(zone).plusMinutes(startMinutes.toLong()).toInstant()

    fun startMillis(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long = startOf(day, zone).toEpochMilli()

    /** When the next day begins after [now]. */
    fun nextStart(now: LocalDateTime = LocalDateTime.now(), startMinutes: Int = this.startMinutes): LocalDateTime =
        dateAt(now, startMinutes).plusDays(1).atStartOfDay().plusMinutes(startMinutes.toLong())
}
