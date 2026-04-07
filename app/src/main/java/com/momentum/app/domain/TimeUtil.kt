package com.momentum.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.TimeZone

fun zoneId(zone: TimeZone = TimeZone.getDefault()): ZoneId = zone.toZoneId()

/** Start of calendar day for [epochMillis] in [zone]. */
fun startOfDayMillis(epochMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val z = zone.toZoneId()
    val d = Instant.ofEpochMilli(epochMillis).atZone(z).toLocalDate()
    return d.atStartOfDay(z).toInstant().toEpochMilli()
}

fun endOfDayMillis(dayStartMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val z = zone.toZoneId()
    val d = Instant.ofEpochMilli(dayStartMillis).atZone(z).toLocalDate()
    return d.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
}

fun millisToLocalDate(epochMillis: Long, zone: TimeZone = TimeZone.getDefault()): LocalDate {
    return Instant.ofEpochMilli(epochMillis).atZone(zone.toZoneId()).toLocalDate()
}

fun localDateToStartMillis(date: LocalDate, zone: TimeZone = TimeZone.getDefault()): Long {
    return date.atStartOfDay(zone.toZoneId()).toInstant().toEpochMilli()
}

fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)
