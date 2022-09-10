package tech.gdragon.commands.settings

import dev.minn.jda.ktx.CoroutineEventListener
import dev.minn.jda.ktx.interactions.Command
import dev.minn.jda.ktx.interactions.option
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import tech.gdragon.listener.CombinedAudioRecorderHandler
import net.dv8tion.jda.api.entities.Guild as DiscordGuild
import tech.gdragon.i18n.Ignore as IgnoreTranslator

class Ignore : CommandHandler() {
  companion object {
    val command = Command("ignore", "Ignore audio from specified during User for current recording.") {
      option<User>("user", "The user to ignore", true)
    }

    fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(SlashCommandEvent) -> Unit = { event ->
      event.guild?.let {
        val ignoredUserId = event.getOption("user")?.asUser?.idLong
        val message = handler(pawa, it, event.user.idLong, listOfNotNull(ignoredUserId))
        event.reply(message).queue()
      } ?: event.reply(":no_entry: _${Babel.slash(Lang.EN).inGuild}").queue()
    }

    private fun handler(pawa: Pawa, guild: DiscordGuild, authorId: Long, ignoredUserIds: List<Long>): String {

      val translator: IgnoreTranslator = pawa.translator(guild.idLong)

      val isRecording = guild.audioManager.connectedChannel
        ?.members
        ?.map { it.idLong }
        ?.containsAll(listOf(authorId, guild.jda.selfUser.idLong)) ?: false

      return if (isRecording) {
        (guild.audioManager.receivingHandler as? CombinedAudioRecorderHandler)!!.let { audioRecorderHandler ->
          // These two are redundant, eventually need to migrate the source of truth to the Pawa API class
          ignoredUserIds.forEach(audioRecorderHandler::silenceUser)
          pawa.ignoreUsers(audioRecorderHandler.session, ignoredUserIds)

          val ignoredUsersMentions = ignoredUserIds.joinToString { "<@$it>" }
          ":hear_no_evil: _${translator.ignore(ignoredUsersMentions)}_"
        }
      } else ":no_entry_sign: _${translator.notRecording}_"
    }
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent, pawa: Pawa) {
    require(args.isNotEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val ignoredUserIds = event.message.mentionedUsers.map(User::getIdLong)
    val message = handler(pawa, event.guild, event.author.idLong, ignoredUserIds)
    BotUtils.sendMessage(event.channel, message)
  }

  override fun usage(prefix: String, lang: Lang): String = Babel.ignore(lang).usage(prefix)

  override fun description(lang: Lang): String = "Ignores audio from a Bot during a recording session."
}
