package tech.gdragon.commands.misc

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.commands.CommandHandler
import tech.gdragon.db.dao.Guild
import java.awt.Color

class Help : Command {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    transaction {
      val guild = Guild.findById(event.guild.idLong)
      val prefix = guild?.settings?.prefix ?: "!"

      require(args.isEmpty()) {
        BotUtils.sendMessage(event.channel, usage(prefix))
      }

      // TODO must be configurable
      val embed = EmbedBuilder().apply {
        setAuthor("pawa", "http://pawabot.site", event.jda.selfUser.avatarUrl)
        setColor(Color.decode("#596800"))
        setTitle("Currently in beta, being actively developed and tested. Expect bugs. All settings get cleared on every beta release")
        setDescription("**pawa** is an implementation of _throw-voice_, check out [GitHub](https://github.com/guacamoledragon/throw-voice) for updates!")
        setThumbnail(event.jda.selfUser.avatarUrl)
        setFooter("Replace brackets [] with item specified. Vertical bar | means 'or', either side of bar is valid choice.", null)
        addBlankField(false)
      }

      val commands = CommandHandler.commands.keys
      val aliases = guild?.settings?.aliases?.toList()

      commands
        .sorted()
        .forEach {
          val command = CommandHandler.commands[it]

          val aliasDescription =
            aliases
              ?.filter { alias -> alias.name == it }
              ?.let {
                if (it.isNotEmpty()) {
                  "\nAliases: " + it.joinToString(",") { alias -> "`${alias.alias}`" }
                } else ""
              } ?: ""

          val description = command?.description() + aliasDescription
          embed.addField(command?.usage(prefix), description, false)
        }

      BotUtils.sendMessage(event.channel, "<@${event.author.id}> check your DMs!")
      event.author.openPrivateChannel().queue({ channel ->
        channel.sendMessage(embed.build()).queue()
      })
    }
  }

  override fun usage(prefix: String): String = "${prefix}help"

  override fun description(): String = "Shows all commands and their usages."
}
