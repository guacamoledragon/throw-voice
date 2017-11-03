package tech.gdragon.commands.settings

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.User
import tech.gdragon.db.table.Tables.Users
import java.util.function.Consumer

class AlertsCommand : Command {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    val guildId = event.guild.idLong
    val prefix = transaction { Guild.findById(guildId)!!.settings.prefix }
    val channel = event.channel

    var message = usage(prefix)

    if (args.size == 1) {
      val author = event.author

      transaction {
        val userList = User.find { Users.id eq author.idLong }
        when (args[0]) {
          "off" -> {
            val guild = Guild.findById(guildId)
            if (userList.empty()) {
              User.new(author.idLong) {
                name = author.name
                settings = guild!!.settings
              }
            }
            message = "Alerts now off, message `${prefix}alerts on` to re-enable at any time"
          }
          "on" -> {
            userList.forEach(Consumer<User> { it.delete() })
            message = "Alerts now on, message `${prefix}alerts off` to disable at any time"
          }
        }
      }
      BotUtils.sendMessage(channel, message)
    }
  }

  override fun usage(prefix: String): String = "${prefix}alerts [on | off]"

  override fun description(): String =
    "Turns on/off direct message alerts for when you are being recorded in a voice channel (on by default)"
}
