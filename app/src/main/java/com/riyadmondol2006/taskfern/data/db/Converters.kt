package com.riyadmondol2006.taskfern.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalTime

/** Dates are stored as epoch days and times as minutes after midnight: compact, sortable and zone-free. */
class Converters {
    @TypeConverter
    fun dateToLong(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun longToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun timeToInt(time: LocalTime?): Int? = time?.let { it.hour * 60 + it.minute }

    @TypeConverter
    fun intToTime(value: Int?): LocalTime? = value?.let { LocalTime.of((it / 60) % 24, it % 60) }
}
