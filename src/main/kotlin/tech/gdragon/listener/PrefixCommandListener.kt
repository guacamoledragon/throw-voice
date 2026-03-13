package tech.gdragon.listener

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.koin.core.component.KoinComponent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.commands.handleCommand

class PrefixCommandListener(val pawa: Pawa) : ListenerAdapter(), KoinComponent {

  private val logger = KotlinLogging.logger {}
  private val website: String = getKoin().getProperty("BOT_WEBSITE", "http://localhost:8080/")

  override fun onMessageReceived(event: MessageReceivedEvent) {
    when {
      event.isFromType(ChannelType.PRIVATE) -> onPrivateMessageReceived(event)
      event.isFromGuild -> onGuildMessageReceived(event)
      else -> super.onMessageReceived(event)
    }
  }

  fun onGuildMessageReceived(event: MessageReceivedEvent) {
    val tracer = getKoin().get<Tracer>()
    val span = tracer.spanBuilder("Event Message Received").startSpan()
    span.run {
      setAttribute("discord-user", event.author.idLong)
      setAttribute("guild", event.guild.idLong)
      setAttribute("text-channel", event.channel.idLong)
    }

    span.makeCurrent().use {

      event.member?.let {
        if (BotUtils.isSelfBot(it.user)) {
          span.end()
          return
        }
      }

      val prefix = try {
        BotUtils.getPrefix(event.guild)
      } catch (e: Exception) {
        span.recordException(e)
        span.setStatus(StatusCode.ERROR)
        logger.error(e) {
          "Attempting to fetch prefix for ${event.guild.idLong}, failed!"
        }
        span.end()
        return
      }

      withLoggingContext("guild" to event.guild.name, "text-channel" to event.channel.name) {
        val rawContent = event.message.contentDisplay.lowercase()
        val inMaintenance = getKoin().getProperty("BOT_MAINTENANCE", "false").toBoolean()
        val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
        val hasPrefix = rawContent.startsWith(prefix)

        if (hasPrefix && inMaintenance) {
          BotUtils.sendMessage(
            defaultChannel,
            ":poop: _Currently running an update...\n\nIf you have any questions please visit the support server: ${website}_"
          )
          logger.warn { "Trying to use while running an update" }
        } else if (hasPrefix) {
          try {
            handleCommand(span, event, pawa, prefix, rawContent)
            // Update activity
            BotUtils.updateActivity(event.guild)

            if (!pawa.isStandalone) {
              BotUtils.sendMessage(
                defaultChannel,
                ":warning: **Prefix commands (and aliases) will be removed <t:1777593600:D> (<t:1777593600:R>).** " +
                  "Please switch to slash commands: `/record`, `/save`, `/stop`, etc. " +
                  "Type `/` in the message bar to see all available commands."
              )
            }
          } catch (e: InvalidCommand) {
            BotUtils.sendMessage(defaultChannel, ":no_entry_sign: _Usage: `${e.usage(prefix)}`_")
            logger.warn { "[$rawContent] ${e.reason}" }
          }
        }
      }
    }

    span.end()
  }

  private fun onPrivateMessageReceived(event: MessageReceivedEvent) {
    if (event.author.isBot.not()) {
      val message = """
        For more information on ${event.jda.selfUser.asMention}, please visit https://www.pawa.im.
      """.trimIndent()

      event
        .channel
        .sendMessage(message)
        .queue()
    }
  }
}
