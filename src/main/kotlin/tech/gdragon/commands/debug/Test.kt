package tech.gdragon.commands.debug

import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.BotUtils.TRIGOMAN
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.audio.Record
import tech.gdragon.commands.audio.Save
import tech.gdragon.db.asyncTransaction
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.Recording
import tech.gdragon.db.now
import tech.gdragon.discord.message.RecordingReply
import tech.gdragon.i18n.Lang
import tech.gdragon.listener.CombinedAudioRecorderHandler

class Test : CommandHandler() {
  override fun action(args: Array<String>, event: MessageReceivedEvent, pawa: Pawa) {
    if ((event.member?.idLong ?: 1L) == TRIGOMAN) {
      recordSave(event, pawa)
    }
  }

  private fun recordSave(event: MessageReceivedEvent, pawa: Pawa) {
    val voiceChannel: VoiceChannel = event.guild.getVoiceChannelsByName("bot-testing", true).first()!!

    val message = Record.handler(pawa, event.guild, voiceChannel, event.channel)
    BotUtils.sendMessage(event.channel, message)
    Thread.sleep(1000L)
    Save.handler(pawa, event.guild, event.channel)
    val recorder = event.guild.audioManager.receivingHandler as? CombinedAudioRecorderHandler
    val recording = recorder?.recording!!
    //    val recording = transaction { Recording.all().first() }
    val recordingEmbed = RecordingReply(recording, pawa.config.appUrl)
    event.channel.sendMessage(recordingEmbed.message).queue()
  }

  fun recordingReply(event: MessageReceivedEvent, pawa: Pawa) {
    val recording = transaction { Recording.all().first() }
    val recordingEmbed = RecordingReply(recording, pawa.config.appUrl)
    event.channel.sendMessage(recordingEmbed.message).queue()
  }

  fun async(event: MessageReceivedEvent, pawa: Pawa) {
    for (i in 1..1000) {
      asyncTransaction {
        val guild = Guild[event.guild.idLong]
        guild.lastActiveOn = now()
        print("$i,")
      }
    }
  }

  override fun usage(prefix: String, lang: Lang): String {
    TODO("Not yet implemented")
  }

  override fun description(lang: Lang): String {
    TODO("Not yet implemented")
  }
}
