package tech.gdragon.commands.misc

import mu.KotlinLogging
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.discord.Command
import java.awt.Color

class Help : CommandHandler {
  private val logger = KotlinLogging.logger {}

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    transaction {
      val guild = Guild.findById(event.guild.idLong)
      val prefix = guild?.settings?.prefix ?: "!"

      require(args.isEmpty()) {
        throw InvalidCommand(::usage, "Empty arguments")
      }

      val disclaimer = """|**Depending on where you live, it _may_ be illegal to record without everyone's consent. Please
                          |check your local laws.**
                          |
                          |https://en.wikipedia.org/wiki/Telephone_recording_laws
                          |_This is not legal advice._
                          |""".trimMargin()

      // TODO must be configurable
      val embed = EmbedBuilder().apply {
        setAuthor("pawa", "https://www.pawabot.site", event.jda.selfUser.avatarUrl)
        setColor(Color.decode("#596800"))
        setTitle("Currently in beta, being actively developed and tested. Expect bugs. All settings get cleared on every beta release")
        setDescription("**pawa** is an implementation of _throw-voice_, check out [GitHub](https://github.com/guacamoledragon/throw-voice) for updates!\n\n")
        appendDescription(disclaimer)
        setThumbnail(event.jda.selfUser.avatarUrl)
        setFooter("Replace brackets [] with item specified. Vertical bar | means 'or', either side of bar is valid choice.", null)
        addBlankField(false)
      }

      val commands = Command.values()
      val aliases = guild?.settings?.aliases?.toList()

      commands
        .sorted()
        .forEach { command ->
          val commandHandler = command.handler

          val aliasDescription =
            aliases
              ?.filter { alias -> alias.name == command.name.toLowerCase() }
              ?.let {
                if (it.isNotEmpty()) {
                  "\nAliases: " + it.joinToString(",") { alias -> "`${alias.alias}`" }
                } else ""
              } ?: ""

          val description = commandHandler.description() + aliasDescription
          embed.addField(commandHandler.usage(prefix), description, false)
        }

      if (event.author.isBot) {
        BotUtils.sendMessage(event.channel, "Can't DM a bot!")
        this@Help.logger.warn {
          val channel = event.channel
          val user = event.author
          "${channel.guild.name}#${channel.name}: Could not alert bot ${user.name}"
        }
      } else {
        BotUtils.sendMessage(event.channel, "<@${event.author.id}> check your DMs!")
        event.author.openPrivateChannel().queue { channel ->
          channel.sendMessage(embed.build()).queue()
        }
      }
    }
  }

  override fun usage(prefix: String): String = "${prefix}help"

  override fun description(): String = "Shows all commands and their usages."
}
