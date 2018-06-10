package tech.gdragon.commands.settings

import mu.KotlinLogging
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.User

fun configureAlerts(userId: String, guildId: Long, enable: Boolean) {
  transaction {
    val settings = Guild.findById(guildId)?.settings!!
    val user = User.findOrCreate(userId, settings)

    if (enable) {
      user.delete()
    }
  }
}

class Alerts : Command {
  private val logger = KotlinLogging.logger {}

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    val guildId = event.guild.idLong
    val channel = event.channel

    if (args.size == 1) {
      val author = event.author

      val alert = { enable: Boolean -> configureAlerts(author.id, guildId, enable) }
      val message =
        when (args.first()) {
          "off" -> {
            alert(false)
            logger.info {
              "${event.guild.name}#${event.channel.name}: Disable alerts for ${author.id}"
            }
            ":bangbang: _Alerts now **off**_"
          }
          "on" -> {
            alert(true)
            logger.info {
              "${event.guild.name}#${event.channel.name}: Enable alerts for ${author.id}"
            }
            ":bangbang: _Alerts now **on**_"
          }
          else -> {
            throw InvalidCommand(::usage, "Invalid argument: ${args.first()}")
          }
        }
      BotUtils.sendMessage(channel, message)
    } else {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }
  }

  override fun usage(prefix: String): String = "${prefix}alerts [on | off]"

  override fun description(): String =
    "Turns on/off direct message alerts when you are being recorded in a voice channel (on by default)"
}
