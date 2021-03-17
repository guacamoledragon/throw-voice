package tech.gdragon.commands.debug

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand

class Status : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.isEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }


    val shardManager = event.jda.shardManager

    val shardStatus = shardManager
      ?.shards
      ?.joinToString { shard ->
        "${shard.shardInfo.shardString} ${shard.status}\n"
      } ?: "Status Unavailable"

    val status = """
      ```
      $shardStatus
      ```
    """.trimIndent()

    BotUtils.sendMessage(event.channel, status)
  }

  override fun usage(prefix: String): String = "${prefix}status"

  override fun description(): String = "Display Shard Status"

}
