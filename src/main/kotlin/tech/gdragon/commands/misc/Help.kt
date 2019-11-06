package tech.gdragon.commands.misc

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.transaction
import tech.gdragon.discord.Command
import java.awt.Color

class Help : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    val prefix = transaction { Guild.findById(event.guild.idLong)?.settings?.prefix } ?: "!"
    val aliases = transaction {
      Guild.findById(event.guild.idLong)
        ?.settings
        ?.aliases
        ?.toList()
    }

    require(args.isEmpty()) {
      throw InvalidCommand(::usage, "Empty arguments")
    }

    usageCounter.add(1)

    val disclaimer = """|**Depending on where you live, it _may_ be illegal to record without everyone's consent. Please
                          |check your local laws.**
                          |
                          |https://en.wikipedia.org/wiki/Telephone_recording_laws
                          |_This is not legal advice._
                          |""".trimMargin()

    val website: String = getKoin().getProperty("WEBSITE", "http://localhost:8080/")

    // TODO must be configurable
    val embed = EmbedBuilder().apply {
      setAuthor("pawa", website, event.jda.selfUser.avatarUrl)
      setColor(Color.decode("#596800"))
      setTitle("Currently in beta, being actively developed and tested. Expect bugs. All settings get cleared on every beta release")
      setDescription("**pawa** is an implementation of _throw-voice_, check out [GitHub](https://github.com/guacamoledragon/throw-voice) for updates!\n\n")
      appendDescription(disclaimer)
      setThumbnail(event.jda.selfUser.avatarUrl)
      setFooter("Replace brackets [] with item specified. Vertical bar | means 'or', either side of bar is valid choice.", null)
      addBlankField(false)
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    val commands = Command.values()

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

    event.author.openPrivateChannel().queue { channel ->
      try {
        channel
          .sendMessage(embed.build())
          .queue {
            BotUtils.sendMessage(defaultChannel, ":information_source: _**<@${event.author.id}>** check your DMs!_")
          }
      } catch (ex: UnsupportedOperationException) {
        logger.warn(ex) {
          "Couldn't send DM to ${event.author.name}"
        }
      }
    }
  }

  override fun usage(prefix: String): String = "${prefix}help"

  override fun description(): String = "Shows all commands and their usages."
}
