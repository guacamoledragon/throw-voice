package tech.gdragon.commands.settings

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.User
import tech.gdragon.db.table.Tables.Users

class Alerts : Command {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    val guildId = event.guild.idLong
    val channel = event.channel

    if (args.size == 1) {
      val author = event.author

      transaction {
        val userList = User.find { Users.id eq author.idLong }
        val message =
          when (args.first()) {
            "off" -> {
              val guild = Guild.findById(guildId)
              if (userList.empty()) {
                User.new(author.idLong) {
                  name = author.name
                  settings = guild!!.settings
                }
              }
              ":bangbang: _Alerts now **off**_"
            }
            "on" -> {
              userList.forEach { it.delete() }
              ":bangbang: _Alerts now **on**_"
            }
            else -> {
              throw InvalidCommand(::usage, "Invalid argument: ${args.first()}")
            }
          }

        BotUtils.sendMessage(channel, message)
      }
    } else {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }
  }

  override fun usage(prefix: String): String = "${prefix}alerts [on | off]"

  override fun description(): String =
    "Turns on/off direct message alerts for when you are being recorded in a voice channel (on by default)"
}
