package com.focustask.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Local date-times are stored as "seconds since 1970 on the wall clock", so they never shift. */
fun LocalDateTime.toStored(): Long = toEpochSecond(ZoneOffset.UTC)

fun Long.fromStored(): LocalDateTime = LocalDateTime.ofEpochSecond(this, 0, ZoneOffset.UTC)

fun LocalDateTime.toEpochMillisLocal(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun minutesToTime(min: Int): LocalTime = LocalTime.of((min / 60) % 24, min % 60)

fun LocalTime.toMinutes(): Int = hour * 60 + minute

fun LocalDate.stored(): Long = toEpochDay()
