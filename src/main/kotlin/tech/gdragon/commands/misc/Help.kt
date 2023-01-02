package tech.gdragon.commands.misc

import mu.withLoggingContext
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent.getKoin
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.discord.Command
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import java.awt.Color

class Help : CommandHandler() {
  override fun action(args: Array<String>, event: MessageReceivedEvent, pawa: Pawa) {
    // Avoid getting DoS'd by bots, bots are helpless anyway :D
    if (event.author.isBot)
      return

    val guild = transaction { Guild[event.guild.idLong] }
    val prefix = transaction { guild.settings.prefix }
    val aliases = transaction {
      Guild.findById(event.guild.idLong)
        ?.settings
        ?.aliases
        ?.toList()
        ?.map {
          object {
            val name = it.name
            val alias = it.alias
          }
        }
    }
    val language = transaction { guild.settings.language }

    val translator = Babel.help(language)

    require(args.isEmpty()) {
      throw InvalidCommand(::usage, "Empty arguments")
    }

    val website: String = getKoin().getProperty("BOT_WEBSITE", "http://localhost:8080/")

    val embed = EmbedBuilder().apply {
      setAuthor("pawa", website, event.jda.selfUser.avatarUrl)
      setColor(Color.decode("#596800"))
      setTitle(translator.embedTitle(website))
      setThumbnail(event.jda.selfUser.avatarUrl)
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    val commands = Command.values()
    commands
      .sorted()
      .forEach { command ->
        val commandHandler = command.handler

        val aliasDescription =
          aliases
            ?.filter { alias -> alias.name == command.name }
            ?.let {
              if (it.isNotEmpty()) {
                "\nAliases: " + it.joinToString(",") { alias -> "`${alias.alias}`" }
              } else ""
            } ?: ""

        embed.addField(commandHandler.usage(prefix, language), aliasDescription, false)
      }

    event.author.openPrivateChannel()
      .flatMap { it.sendMessageEmbeds(embed.build()) }
      .queue(
        { BotUtils.sendMessage(defaultChannel, ":information_source: _${translator.checkDm(event.author.id)}_") },
        { ex ->
          withLoggingContext("guild" to event.guild.name) {
            logger.warn(ex) { "Couldn't send DM to ${event.author.name}" }
          }
        })
  }

  override fun usage(prefix: String, lang: Lang): String {
    val translator = Babel.help(lang)
    return translator.usage(prefix)
  }

  override fun description(lang: Lang): String = "Shows all commands and their usages."
}
