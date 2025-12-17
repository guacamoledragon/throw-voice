package tech.gdragon.api.pawa

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module
import tech.gdragon.api.commands.RecoverResult
import tech.gdragon.api.commands.safeFile
import tech.gdragon.api.tape.extractDuration
import tech.gdragon.api.tape.queueFileIntoMp3
import tech.gdragon.data.Datastore
import tech.gdragon.db.Database
import tech.gdragon.db.dao.*
import tech.gdragon.db.table.Tables
import tech.gdragon.discord.Command
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import tech.gdragon.koin.getBooleanProperty
import java.io.File
import java.math.BigDecimal
import java.util.*

open class Pawa(val db: Database, val config: PawaConfig = PawaConfig.invoke()) {
  companion object {
    fun module() = module(createdAtStart = true) {
      single<Pawa> {
        val config = PawaConfig {
          appUrl = getProperty("APP_URL", "")
          dataDirectory = getProperty("BOT_DATA_DIR", "")
          isStandalone = getBooleanProperty("BOT_STANDALONE")
        }
        Pawa(get(), config)
      }
    }
  }

  val isStandalone by lazy { config.isStandalone }
  val logger = KotlinLogging.logger { }

  private var _ignoredUsers: MutableMap<String, List<Long>> = mutableMapOf()
  private var _recordings: MutableMap<String, Long> = mutableMapOf()

  val recordings: Map<String, Long>
    get() = Collections.unmodifiableMap(_recordings)

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

  fun saveDestination(guildId: Long, channelId: Long?) {
    transaction {
      val guild = Guild[guildId]
      guild.settings.defaultTextChannel = channelId
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
   * Attempt recovery of a SessionID by:
   *   * Re-uploading MP3
   *   * Converting Queue file to MP3 _then_ uploading
   *   * If file was not found, re-send the URL (could be a Discord upload)
   * If recording cannot be recovered, return null.
   */
  open fun recoverRecording(datastore: Datastore, sessionId: String): RecoverResult {

    // Attempt to recover regardless of whether there's a database recording
    val mp3File = safeFile("${config.dataDirectory}/recordings", "$sessionId.mp3")
    val queueFile = safeFile("${config.dataDirectory}/recordings", "$sessionId.queue")

    val mp3Exists: Boolean = when {
      mp3File.exists() -> {
        logger.info { "Recovering from mp3 file." }
        true
      }

      // This is a side effect
      queueFile.exists() -> {
        logger.info { "Recovering $sessionId from queue file." }
        queueFileIntoMp3(queueFile, mp3File).exists()
      }

      queueFile.exists().not() && mp3File.exists().not() -> {
        logger.warn {
          "Recovering failed, could not find mp3 or queue file."
        }
        false
      }

      else -> false
    }

    val result =
      if (mp3Exists) {
        val recording = uploadRecording(sessionId, mp3File, datastore)
        RecoverResult(sessionId, recording)
      } else {

        val recording = transaction {
          Recording.findById(sessionId)
        }

        when {
          true == recording?.url?.contains("discord://") -> RecoverResult(sessionId, recording)
          true == recording?.expired() -> RecoverResult(sessionId, recording, ":warning: Recording expired.")
          else -> RecoverResult(sessionId, recording, "No recording with that Session ID")
        }
      }

    return result
  }

  fun uploadRecording(sessionId: String, mp3File: File, datastore: Datastore): Recording? {
    val recording = transaction {
      Recording.findById(sessionId)
    }

    return recording?.let {
      transaction {
        logger.info { "Re-uploading recording" }
        val result = datastore.upload("${it.guild.id.value}/${mp3File.name}", mp3File)

        it.apply {
          size = result.size
          modifiedOn = this.modifiedOn ?: result.timestamp
          url = result.url
          duration = extractDuration(mp3File)
        }
      }
    } ?: transaction {
      Guild.findById(408795211901173762L)?.let {
        logger.info { "Uploading recording and creating dummy Recording record." }
        val result = datastore.upload("${it.id.value}/${mp3File.name}", mp3File)

        Recording.new(sessionId) {
          channel = Channel.findOrCreate(776694242840019016L, "prolonged-testing", 408795211901173762L)
          guild = it
          size = result.size
          modifiedOn = result.timestamp
          url = result.url
          duration = extractDuration(mp3File)
        }
      }
    }
  }
}
