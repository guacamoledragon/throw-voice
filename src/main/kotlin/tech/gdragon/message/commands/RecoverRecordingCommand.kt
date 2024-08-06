package tech.gdragon.message.commands

import tech.gdragon.BotUtils
import tech.gdragon.api.commands.RecoverResult
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.data.Datastore

class RecoverRecordingCommand(val pawa: Pawa, val dataDirectory: String, val datastore: Datastore, message: String) {
  companion object {
    const val EVENT_NAME = "Recover Recording"
  }

  private var _results: List<RecoverResult> = emptyList()

  private val sessionIds = BotUtils.findSessionID(message)

  val results: List<RecoverResult>
    get() {
      if (_results.isEmpty()) {
        _results = sessionIds.map { id ->
          val recording = pawa.recoverRecording(dataDirectory, datastore, id)
          RecoverResult(id, recording)
        }
      }
      return _results
    }

  fun failedRecordings(): List<RecoverResult> = results.filter { it.recording == null }

  fun successfulRecordings(): List<RecoverResult> = results.filter { it.recording != null }

  override fun toString(): String {
    val listStatus = results.joinToString("\n") { result ->
      if(result.recording == null) {
        ":x: `${result.id}`"
      } else {
        ":white_check_mark: `${result.id}`"
      }
    }
    return listStatus
  }
}
