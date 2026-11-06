package br.com.otavioesteves.finances.domain.model

import java.time.ZonedDateTime

/** Both dates use the device's local calendar, including leap years and DST. */
fun nextInvoiceReminder(now: ZonedDateTime): ZonedDateTime = (0L..1L).flatMap { offset ->
    val month = now.toLocalDate().withDayOfMonth(1).plusMonths(offset)
    listOf(month.withDayOfMonth(5), month.withDayOfMonth(month.lengthOfMonth()))
}.map { it.atTime(9, 0).atZone(now.zone) }.filter { it.isAfter(now) }.minOrNull()!!
