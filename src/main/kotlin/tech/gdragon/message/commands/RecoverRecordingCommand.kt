package tech.gdragon.message.commands

import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.data.Datastore

class RecoverRecording(val pawa: Pawa, val dataDirectory: String, val datastore: Datastore, message: String) {
  companion object {
    const val EVENT_NAME = "Recover Recording"
  }

  private val sessionIds = BotUtils.findSessionID(message)

  fun recover(): String {
    sessionIds.map {
      
    }
    return ""
  }
}
