package tech.gdragon.commands

import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.managers.AudioManager
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.db.dao.Guild
import tech.gdragon.listener.CombinedAudioRecorderHandler

/**
 * I don't remember what this was for, I think perhaps I don't need the class hierarchy?
 */
object Commands {
  fun leave(guildId: Long, audioManager: AudioManager, channel: TextChannel?): String {
    return transaction {
      val guild = Guild.findById(guildId)

      if (audioManager.isConnected) {
        val voiceChannel = audioManager.connectedChannel

        guild?.settings?.let {
          if (it.autoSave) {
            val audioReceiveHandler = audioManager.receiveHandler as CombinedAudioRecorderHandler
            audioReceiveHandler.saveRecording(voiceChannel, channel)
          }
        }

        BotUtils.leaveVoiceChannel(voiceChannel)
        ":wave: _Leaving **<#${voiceChannel.id}>**_"
      } else {
        ":no_entry_sign: _I am not in a channel_"
      }
    }
  }
}
