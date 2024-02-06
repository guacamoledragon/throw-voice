package tech.gdragon.api.pawa

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.api.tape.queueFileIntoMp3
import tech.gdragon.data.Datastore
import tech.gdragon.db.Database
import tech.gdragon.db.dao.*
import tech.gdragon.db.table.Tables
import tech.gdragon.discord.Command
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import java.io.File
import java.math.BigDecimal
import org.koin.dsl.module
import tech.gdragon.koin.getBooleanProperty

class Pawa(val db: Database, _config: PawaConfig? = null) {
  companion object {
    fun module() = module(createdAtStart = true) {
      single<Pawa> {
        val config = PawaConfig {
          isStandalone = getBooleanProperty("BOT_STANDALONE")
        }
        Pawa(get(), config)
      }
    }
  }

  val config = _config ?: PawaConfig.invoke()
  val isStandalone = config.isStandalone
  val logger = KotlinLogging.logger { }

  private var _ignoredUsers: MutableMap<String, List<Long>> = mutableMapOf()
  private var _recordings: MutableMap<String, Long> = mutableMapOf()

  inline fun <reified T> translator(guildId: Long): T {
    val lang = transaction { Guild[guildId].settings.language }
    return Babel.commandTranslator(lang)
  }

  fun createAlias(guildId: Long, command: Command, alias: String): Alias? {
    // Command cannot be ALIAS and the alias cannot be the name of an existing command
    val isValidAlias = (Command.ALIAS != command) &&
      Command
        .entries
        .map { it.name.lowercase() }
        .none { it == alias.lowercase() }

    return when (isValidAlias) {
      true -> transaction(db.database) {
        Settings
          .find { Tables.Settings.guild eq guildId }
          .firstOrNull()
          ?.let { settings ->
            Alias.findOrCreate(command.name, alias, settings)
          }
      }

      // Otherwise, don't create a new alias
      false -> {
        logger.warn {
          "Could not create alias for ${command.name}"
        }
        null
      }
    }
  }

  /**
   * Returns whether automatic saving is enabled for the given [guildId].
   *
   * If the bot is running in standalone mode, this will always return `true`.
   */
  fun autoSave(guildId: Long): Boolean {
    return isStandalone || transaction(db.database) {
      Settings
        .find { Tables.Settings.guild eq guildId }
        .firstOrNull()?.autoSave
        ?: false
    }
  }

  fun autoStopChannel(channelId: Long, channelName: String, guildId: Long, threshold: Int) {
    transaction(db.database) {
      Channel
        .findOrCreate(channelId, channelName, guildId)
        .autoStop = threshold
    }
  }

  fun autoRecordChannel(channelId: Long, channelName: String, guildId: Long, threshold: Int) {
    transaction(db.database) {
      Channel
        .findOrCreate(channelId, channelName, guildId)
        .autoRecord = threshold
    }
  }

  fun toggleAutoSave(guildId: Long): Boolean? {
    return transaction(db.database) {
      Settings
        .find { Tables.Settings.guild eq guildId }
        .firstOrNull()
        ?.apply {
          autoSave = !autoSave
        }
        ?.autoSave
    }
  }

  fun setLocale(guildId: Long, locale: String): Pair<Lang, Lang> {
    return transaction {
      val guild = Guild[guildId]
      val prev = guild.settings.language
      val newLang = Lang.valueOf(locale)

      guild.settings.language = newLang

      Pair(prev, newLang)
    }
  }

  fun ignoreUsers(session: String, ignoredUserIds: List<Long>) {
    _ignoredUsers[session] = ignoredUserIds
  }

  fun startRecording(session: String, guildId: Long) {
    _recordings[session] = guildId
  }

  fun stopRecording(session: String) {
    _recordings -= session
  }

  /**
   * Set the recording volume for a given [guildId], and return the set value 0.0 otherwise.
   * [volumePercent] will be clamped between 0.0 and 1.0.
   */
  fun volume(guildId: Long, volumePercent: Double): Double {
    return transaction {
      val actualVolume = volumePercent.coerceIn(0.0..1.0)
      Settings
        .find { Tables.Settings.guild eq guildId }
        .firstOrNull()
        ?.let {
          it.volume = BigDecimal.valueOf(actualVolume)
          actualVolume
        } ?: 0.0
    }
  }

  /**
   * Given the Session ID, return the database record, or re-upload recording if it exists.
   * If recording cannot be recovered, return null.
   */
  fun recoverRecording(dataDirectory: String, datastore: Datastore, sessionId: String): Recording? {
    val recording = transaction {
      Recording.findById(sessionId)
    }

    recording?.let {
      if (it.url == null) {
        val mp3File = File("$dataDirectory/recordings", "$sessionId.mp3")
        val queueFile = File("$dataDirectory/recordings", "$sessionId.queue")

        when {
          queueFile.exists() && mp3File.exists().not() -> {
            logger.info { "Restoring $sessionId mp3 from queue file." }
            queueFileIntoMp3(queueFile, mp3File)
          }

          queueFile.exists().not() && mp3File.exists().not() -> {
            logger.warn {
              "Recording $sessionId was not found."
            }
            return null
          }
        }

        transaction {
          val result = datastore.upload("${it.guild.id.value}/${mp3File.name}", mp3File)
          it.apply {
            size = result.size
            modifiedOn = this.modifiedOn ?: result.timestamp
            url = result.url
          }
        }
      }
    } ?: logger.warn {
      "Recording $sessionId was not found."
    }

    return recording
  }
}
