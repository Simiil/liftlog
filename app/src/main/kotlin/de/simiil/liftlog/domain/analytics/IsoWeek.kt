package de.simiil.liftlog.domain.analytics

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/** ISO-8601 week key: weekBasedYear*100 + weekNumber (replaces java.time IsoFields). */
fun isoWeekKey(date: LocalDate): Long {
    val thursday = date.plus(4 - date.dayOfWeek.isoDayNumber, DateTimeUnit.DAY)
    val week = (thursday.dayOfYear - 1) / 7 + 1
    return thursday.year * 100L + week
}
