package tech.gdragon.db

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.ISODateTimeFormat

val dtf: DateTimeFormatter = ISODateTimeFormat.basicDateTime()

fun nowUTC(): DateTime = DateTime.now()
