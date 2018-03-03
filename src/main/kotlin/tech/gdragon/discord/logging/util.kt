package tech.gdragon.discord.logging

fun parseGuildInfo(body: String): Map<String, Any> {
  val (guild, channel) = body.substringBefore(':', "#").split('#').let {
    if(it.count() != 2)
      listOf("","")
    else
      it
  }
  return mapOf("guild" to guild, "channel" to channel)
}

fun parseAudioFile(body: String): Map<String, Any> {
  val (f,s) = body.split(" - ")
  val filename = f.split(' ').last()
  val size = s.slice(0 until 10).toDouble()

  return mapOf("file" to mapOf("filename" to filename, "size" to size))
}

fun parseBody(log: String) = log.substringAfter(" - ")
