package tech.gdragon.listener

import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.messages.send
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.RestAction
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import tech.gdragon.BotUtils
import tech.gdragon.BotUtils.TRIGOMAN
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.commands.handleCommand
import tech.gdragon.data.Datastore
import tech.gdragon.db.asyncTransaction
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.Recording
import tech.gdragon.db.now
import tech.gdragon.discord.message.ErrorEmbed
import tech.gdragon.discord.message.RecordingReply
import tech.gdragon.discord.message.RequestAccessReply
import tech.gdragon.message.commands.RecoverRecordingCommand

class EventListener(val pawa: Pawa) : ListenerAdapter(), KoinComponent {

  private val logger = KotlinLogging.logger {}
  private val website: String = getKoin().getProperty("BOT_WEBSITE", "http://localhost:8080/")

  override fun onCommandAutoCompleteInteraction(event: CommandAutoCompleteInteractionEvent) {
    if (event.name == "recover" && event.focusedOption.name == "session-id") {
      val partialSessionId = event.focusedOption.value
      val choices = transaction {
        val limit = 25
        if (TRIGOMAN == event.user.idLong) {
          Recording.findIdLike("$partialSessionId%", null, limit)
        } else {
          Recording.findIdLike("$partialSessionId%", event.guild!!.idLong, limit)
        }.map { r -> r.id.value }
      }

      event
        .replyChoiceStrings(choices)
        .queue(null) {
           logger.warn {
             "Replying to `/recover` autocomplete request took longer than expected."
           }
        }
    }
  }

  override fun onMessageContextInteraction(event: MessageContextInteractionEvent) {
    if (pawa.isStandalone || event.user.idLong == TRIGOMAN) {
      when {
        RecoverRecordingCommand.EVENT_NAME == event.name -> {
          event.deferReply().queue()

          val datastore = getKoin().get<Datastore>()
          val recoverRecordingCommand = RecoverRecordingCommand(pawa, datastore, event.target.contentRaw)

          event.target.addReaction(Emoji.fromUnicode("👀")).queue()
          if (recoverRecordingCommand.results.isEmpty()) {
            event.hook.send("No Session IDs in message found.").queue()
            event.target.addReaction(Emoji.fromUnicode("❌")).queue()
          } else {
            val privateMessageAction = event.target.author
              .openPrivateChannel()
              .flatMap { privateChannel ->
                val successfulReplies = recoverRecordingCommand
                  .successfulRecordings()
                  .map { RecordingReply(it.recording!!, pawa.config.appUrl).message }
                  .map(privateChannel::sendMessage)
                val failedReply = privateChannel
                  .sendMessage(ErrorEmbed(
                    "Recording Not Found",
                    "Could not find these Session ID:\n ```${
                      recoverRecordingCommand.failedRecordings().joinToString("\n") { it.id }
                    }```\n If this is a mistake, contact support server."
                  ).message)

                RestAction.allOf(successfulReplies + failedReply)
              }

            privateMessageAction.queue({
              val message = """
                |${event.target.author.asMention} Please check your DMs :bow:
                |$recoverRecordingCommand
              """.trimMargin()

              event.hook.send(message).queue()
              event.target.addReaction(Emoji.fromUnicode("✅")).queue()
            }, {
              event.hook.send("Failed to DM ${event.target.author.asMention} about recovered recordings.").queue()
            })
          }
        }
      }
    } else {
      val errorEmbed = ErrorEmbed(
        "You cannot use \"${event.name}\" command.",
        "Join the support server and post your Session ID."
      )
      event
        .reply(errorEmbed.message)
        .setEphemeral(true)
        .queue()
    }
  }

  override fun onGuildJoin(event: GuildJoinEvent) {
    withLoggingContext("guild" to event.guild.name) {
      val guild = event.guild
      asyncTransaction {
        Guild
          .findOrCreate(guild.idLong, guild.name)
          .also {
            it.active = true
          }
        BotUtils.updateActivity(event.guild)
      }

      logger.info { "Joined new server '${guild.name}', connected to ${event.jda.guilds.size} guilds." }
    }
  }

  override fun onGuildLeave(event: GuildLeaveEvent) {
    withLoggingContext("guild" to event.guild.name) {
      asyncTransaction {
        Guild
          .findById(event.guild.idLong)
          ?.let {
            it.active = false
            it.unjoinedOn = now()
          }
      }

      logger.info { "Server '${event.guild.name}' unjoined, connected to ${event.jda.guilds.size} guilds." }
    }
  }

  fun onGuildVoiceJoin(event: GuildVoiceUpdateEvent) {
    withLoggingContext("guild" to event.guild.name) {
      val user = event.member.user
      event.channelJoined?.let { voiceChannel ->
        logger.debug { "${event.guild.name}#${voiceChannel.name} - ${user.name} joined voice channel" }

        if (BotUtils.isSelfBot(user)) {
          logger.debug { "${event.guild.name}#${voiceChannel.name} - ${user.name} is self-bot" }
          return
        }

        // Update activity
        BotUtils.updateActivity(event.guild)

        if (pawa.isStandalone) {
          BotUtils.autoRecord(pawa, event.guild, voiceChannel)
        }
      }
    }
  }

  fun onGuildVoiceLeave(event: GuildVoiceUpdateEvent) {
    withLoggingContext("guild" to event.guild.name) {
      event.channelLeft?.let { voiceChannel ->
        logger.debug {
          "${event.guild.name}#${voiceChannel.name} - ${event.member.effectiveName} left voice channel"
        }
        if (BotUtils.isSelfBot(event.member.user).not()) {
          val save = pawa.autoSave(event.guild.idLong)
          BotUtils.autoStop(event.guild, voiceChannel, save)
        }
      }
    }
  }

  fun onGuildVoiceMove(event: GuildVoiceUpdateEvent) {
    withLoggingContext("guild" to event.guild.name) {
      val user = event.member.user
      val voiceChannelJoined = event.channelJoined
      val voiceChannelLeft = event.channelLeft
      if (voiceChannelJoined != null && voiceChannelLeft != null) {
        logger.debug { "${event.guild.name}#${voiceChannelLeft.name} - ${user.name} left voice channel" }
        logger.debug { "${event.guild.name}#${voiceChannelJoined.name} - ${user.name} joined voice channel" }

        // Update activity
        BotUtils.updateActivity(event.guild)

        if (BotUtils.isSelfBot(user).not()) {
          val save = pawa.autoSave(event.guild.idLong)
          BotUtils.autoStop(event.guild, voiceChannelLeft, save)
          if (pawa.isStandalone) {
            BotUtils.autoRecord(pawa, event.guild, voiceChannelJoined)
          }
        }
      }
    }
  }

  override fun onGuildVoiceUpdate(event: GuildVoiceUpdateEvent) {
    when {
      // Joining a channel without bot being connected
      event.channelLeft == null -> onGuildVoiceJoin(event)
      // Leaving a channel and connecting to no channel == leave
      event.channelLeft != null && event.voiceState.channel == null -> onGuildVoiceLeave(event)
      // Leaving a channel and connecting to another == move
      event.channelLeft != null && event.voiceState.channel != null -> onGuildVoiceMove(event)
      else -> super.onGuildVoiceUpdate(event)
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
          } catch (e: InvalidCommand) {
            BotUtils.sendMessage(defaultChannel, ":no_entry_sign: _Usage: `${e.usage(prefix)}`_")
            logger.warn { "[$rawContent] ${e.reason}" }
          }
        }
      }
    }

    span.end()
  }

  override fun onModalInteraction(event: ModalInteractionEvent) {
    if (event.modalId == "request-access") {
      val request = event.getValue("request-body")?.asString.orEmpty()
      val sessionId = event.getValue("session-id")?.asString.orEmpty()

      event.jda
        .openPrivateChannelById(TRIGOMAN)
        .flatMap { channel ->
          val requestReply = RequestAccessReply(event.user, request, sessionId)
          channel.sendMessageEmbeds(requestReply.embed)
        }
        .queue()

      event
        .reply("Your request has been submitted!\nJoin support server https://discord.gg/gkvsNw8")
        .setEphemeral(true)
        .queue()
    }
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

  override fun onMessageReceived(event: MessageReceivedEvent) {
    when {
      event.isFromType(ChannelType.PRIVATE) -> onPrivateMessageReceived(event)
      event.isFromGuild -> onGuildMessageReceived(event)
      else -> super.onMessageReceived(event)
    }
  }

  /**
   * Always add recording prefix when recording and if possible.
   */
  override fun onGuildMemberUpdateNickname(event: GuildMemberUpdateNicknameEvent) {
    if (BotUtils.isSelfBot(event.user)) {
      if (event.guild.audioManager.isConnected) {
        logger.debug {
          "${event.guild}#: Attempting to change nickname from ${event.oldNickname} -> ${event.newNickname}"
        }

        BotUtils.recordingStatus(event.member, true)
      }
    }
  }

  override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
    val commandName = event.interaction.name
    val sessionId = event.interaction.getOption<String>("session-id") ?:
      pawa.recordings.filterValues { it == event.guild?.idLong }.keys.firstOrNull()
    event.guild?.let(BotUtils::updateActivity)
    withLoggingContext("command" to commandName, "session-id" to sessionId) {
      tech.gdragon.commands.logger.info {
        "Executing command: $commandName"
      }
      super.onSlashCommandInteraction(event)
    }
  }
}
