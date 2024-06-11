package tech.gdragon.discord.message

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun formatInstant(instant: Instant): String {
  val localDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
  val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

  return "${localDateTime.format(formatter)} UTC"
}
