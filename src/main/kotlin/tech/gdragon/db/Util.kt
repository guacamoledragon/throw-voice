package tech.gdragon.db

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat
import java.time.Instant

val dtf: DateTimeFormatter = ISODateTimeFormat.basicDateTime()

fun nowUTC(): DateTime = DateTime.now()

fun now(): Instant = Instant.now()
