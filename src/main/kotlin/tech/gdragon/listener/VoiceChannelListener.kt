package tech.gdragon.listener

import mu.KotlinLogging
import net.dv8tion.jda.core.events.channel.voice.VoiceChannelCreateEvent
import net.dv8tion.jda.core.events.channel.voice.VoiceChannelDeleteEvent
import net.dv8tion.jda.core.events.channel.voice.update.VoiceChannelUpdateNameEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter

class VoiceChannelListener : ListenerAdapter() {
  private val logger = KotlinLogging.logger {}

  override fun onVoiceChannelCreate(event: VoiceChannelCreateEvent) {
    logger.debug { "Creating Channel: ${event.channel}" }
    super.onVoiceChannelCreate(event)
  }

  override fun onVoiceChannelDelete(event: VoiceChannelDeleteEvent) {
    logger.debug { "Deleting Channel: ${event.channel}" }
    super.onVoiceChannelDelete(event)
  }

  override fun onVoiceChannelUpdateName(event: VoiceChannelUpdateNameEvent) {
    logger.debug { "Updating Channel: ${event.channel}" }
    super.onVoiceChannelUpdateName(event)
  }
}
