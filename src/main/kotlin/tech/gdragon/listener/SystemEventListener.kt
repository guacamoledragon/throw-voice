package tech.gdragon.listener

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class SystemEventListener : ListenerAdapter() {
  private val logger = KotlinLogging.logger { }

  override fun onShutdown(event: ShutdownEvent) {
    logger.error {
      "Shutting down with code: ${event.closeCode?.meaning} at ${event.timeShutdown}"
    }
  }
}
