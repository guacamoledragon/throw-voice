package tech.gdragon.commands.misc

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.db.dao.Guild
import tech.gdragon.listener.CombinedAudioRecorderHandler

class Join : Command {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    transaction {
      val guild = Guild.findById(event.guild.idLong)
      val prefix = guild?.settings?.prefix ?: "!"

      require(args.isEmpty()) {
        BotUtils.sendMessage(event.channel, usage(prefix))
      }

      val voiceChannel = event.member.voiceState.channel
      val message =
        if(voiceChannel == null) {
          "You need to be in a voice channel to use this command!"
        } else {
          val connectedChannel = event.guild.audioManager.connectedChannel
          if(connectedChannel != null && connectedChannel.members.contains(event.member)) {
            "I am already in your channel!"
          } else {

            if(event.guild.audioManager.isConnected) {
              guild?.settings?.let {
                if (it.autoSave) {
                  val audioReceiveHandler = event.guild.audioManager.receiveHandler as CombinedAudioRecorderHandler
                  audioReceiveHandler.saveRecording(connectedChannel, event.channel)
                  BotUtils.leaveVoiceChannel(connectedChannel)
                }
              }
            }

            BotUtils.joinVoiceChannel(voiceChannel, true)
            "Joining ${voiceChannel.name}."
          }
        }

      BotUtils.sendMessage(event.channel, message)
    }
  }

  override fun usage(prefix: String): String = "${prefix}join"

  override fun description(): String = "Force the bot to join and record your current channel."
}
