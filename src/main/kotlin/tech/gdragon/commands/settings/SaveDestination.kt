package tech.gdragon.commands.settings

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.messages.MessageCreate
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.i18n.SaveLocation

object SaveDestination {
  val command = Command(
    "save-destination",
    "Send recording to a specific channel, and, optionally, target a specific voice channel."
  ) {
    restrict(guild = true)
    option<TextChannel>("destination", "Recordings will be sent to this channel.", required = true) {
      setChannelTypes(ChannelType.TEXT)
    }
    option<Boolean>("disable", "Revert to default behaviour for all if no `channel` specified.")
  }

  fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
    event.deferReply().await()

    val destinationChannel = event.getOption<TextChannel>("destination")!!
    val disable = event.getOption<Boolean>("disable") ?: false

    val message =
      event.guild?.let { guild ->
        val translator: SaveLocation = pawa.translator(guild.idLong)

        when {
          disable -> {
            pawa.saveDestination(guild.idLong, null)
            ":file_folder: _${translator.current}_"
          }

          destinationChannel.canTalk() -> {
            pawa.saveDestination(guild.idLong, destinationChannel.idLong)
            ":file_folder: _${translator.channel(destinationChannel.asMention)}_"
          }

          else -> {
            ":no_entry_sign: _${translator.permissions(destinationChannel.asMention)}_"
          }
        }
      } ?: ""

    BotUtils.reply(event, MessageCreate(message))
  }
}
