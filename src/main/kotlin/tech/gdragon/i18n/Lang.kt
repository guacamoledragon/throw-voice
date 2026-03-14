/**
 * Inspired by http://ayedo.github.io/internationalization/2020/02/15/typesafe-i18n-made-easy.html
 */
package tech.gdragon.i18n

import java.util.*

enum class Lang {
  DE {
    override val locale = Locale.GERMAN!!
    override val flagEmoji: String = ":flag_de:"
  },
  EN {
    override val locale = Locale.ENGLISH!!
    override val flagEmoji: String = ":flag_us:"
  },
  FIL {
    override val locale = Locale("fil")
    override val flagEmoji: String = ":flag_ph:"
  },
  FR {
    override val locale = Locale.FRENCH!!
    override val flagEmoji: String = ":flag_fr:"
  },
  HU {
    override val flagEmoji: String = ":flag_hu:"
    override val locale: Locale = Locale("hu")
  },
  ID {
    override val locale = Locale("id", "ID", "indonesia")
    override val flagEmoji: String = ":flag_id:"
  },
  ITA {
    override val locale = Locale("ita")
    override val flagEmoji: String = ":flag_it:"
  },
  PL {
    override val locale: Locale = Locale("pl")
    override val flagEmoji: String = ":flag_pl:"
  },
  PT_BR {
    override val locale: Locale = Locale("pt", "BR")
    override val flagEmoji: String = ":flag_br:"
  },
  ES {
    override val locale = Locale("es")
    override val flagEmoji: String = ":flag_mx:"
  },
  THA {
    override val locale: Locale = Locale("tha")
    override val flagEmoji: String = ":flag_th:"
  },
  VI {
    override val locale: Locale = Locale("vi")
    override val flagEmoji: String = ":flag_vn:"
  };

  abstract val locale: Locale
  abstract val flagEmoji: String
}

object Babel {
  val languages = Lang.entries.joinToString(" | ") { it.name.lowercase() }

  fun resource(lang: Lang): ResourceBundle = ResourceBundle.getBundle("translations", lang.locale)

  private val autosave: MutableMap<Lang, AutoSave> = mutableMapOf()
  fun autosave(lang: Lang) = autosave.getOrPut(lang) { AutoSave(lang) }

  private val autorecord: MutableMap<Lang, AutoRecord> = mutableMapOf()
  fun autorecord(lang: Lang) = autorecord.getOrPut(lang) { AutoRecord(lang) }

  private val autostop: MutableMap<Lang, AutoStop> = mutableMapOf()
  fun autostop(lang: Lang) = autostop.getOrPut(lang) { AutoStop(lang) }

  private val ignore: MutableMap<Lang, Ignore> = mutableMapOf()
  fun ignore(lang: Lang) = ignore.getOrPut(lang) { Ignore(lang) }

  private val record: MutableMap<Lang, Record> = mutableMapOf()
  fun record(lang: Lang) = record.getOrPut(lang) { Record(lang) }

  private val save: MutableMap<Lang, Save> = mutableMapOf()
  fun save(lang: Lang) = save.getOrPut(lang) { Save(lang) }

  private val slash: MutableMap<Lang, Slash> = mutableMapOf()
  fun slash(lang: Lang) = slash.getOrPut(lang) { Slash(lang) }

  private val stop: MutableMap<Lang, Stop> = mutableMapOf()
  fun stop(lang: Lang) = stop.getOrPut(lang) { Stop(lang) }

  private val volume: MutableMap<Lang, Volume> = mutableMapOf()
  fun volume(lang: Lang) = volume.getOrPut(lang) { Volume(lang) }

  fun valid(lang: String) = try {
    Lang.valueOf(lang)
    true
  } catch (ex: IllegalArgumentException) {
    false
  }

  inline fun <reified T> commandTranslator(lang: Lang): T {
    return when (T::class) {
      AutoRecord::class -> autorecord(lang) as T
      AutoStop::class -> autostop(lang) as T
      AutoSave::class -> autosave(lang) as T
      Ignore::class -> ignore(lang) as T
      Record::class -> record(lang) as T
      Stop::class -> stop(lang) as T
      Save::class -> save(lang) as T
      Volume::class -> volume(lang) as T
      else -> throw IllegalArgumentException("Language: $lang was not found!")
    }
  }
}

class AutoSave(lang: Lang) {
  private val resource = Babel.resource(lang)

  val noop: String = resource.getString("autosave.noop")
  val off: String = resource.getString("autosave.off")
  val on: String = resource.getString("autosave.on")
}

class AutoRecord(lang: Lang) {
  private val resource = Babel.resource(lang)

  val all: (String) -> String = { number -> resource.getString("autorecord.all").format("**$number**") }
  val none: String = resource.getString("autorecord.none")
  val one: (String, String) -> String = { channelId, number -> resource.getString("autorecord.one").format("**<#$channelId>**", "**$number**") }
  val some: (String) -> String = { channelId -> resource.getString("autorecord.some").format("**<#$channelId>**") }
  val notFound: String = resource.getString("autorecord.not_found")
  val description: String = resource.getString("autorecord.description")
}

class AutoStop(lang: Lang) {
  private val resource = Babel.resource(lang)

  val all: (String) -> String = { number -> resource.getString("autostop.all").format("**$number**") }
  val none: String = resource.getString("autostop.none")
  val one: (String, String) -> String =
    { channelId, number -> resource.getString("autostop.one").format("**<#${channelId}>**", "**$number**") }
  val some: (String) -> String = { channelId -> resource.getString("autostop.some").format("**<#$channelId>**") }
}

class Ignore(lang: Lang) {
  private val resource = Babel.resource(lang)

  val beta: String = resource.getString("ignore.beta")
  val ignore: (String) -> String = { users -> resource.getString("ignore.ignore").format(users) }
  val notRecording: String = resource.getString("ignore.not_recording")
}

class Record(lang: Lang) {
  private val resource = Babel.resource(lang)

  val afkChannel: (String) -> String = { channelId ->
    resource.getString("record.afk_channel").format("**<#$channelId>**")
  }
  val alreadyInChannel: (String) -> String =
    { channelId -> resource.getString("record.already_in_channel").format("**<#$channelId>**") }
  val cannotRecord: (String, String) -> String = { channelId, permission ->
    val permission = permission.replace('_', ' ')
    resource.getString("record.cannot_record").format("**<#$channelId>**", "`$permission`")
  }

  val joinChannel: String = resource.getString("record.join_channel")

  fun cannotUpload(channelId: String, permission: String): String {
    val permission = permission.replace('_', ' ')
    return """:no_entry_sign: _${
      resource.getString("record.cannot_upload").format("**<#$channelId>**", "`$permission`")
    }_"""
  }

  fun recording(channelId: String, session: String): String {
    val recording = resource.getString("record.recording").format("<#$channelId>")
    val warning = resource.getString("record.warning")
    return """:red_circle: **$recording**
           |_Session ID: `$session`_
           |.
           |.
           |.
           |:warning: _${warning}_
           """.trimMargin()
  }

}

class Save(lang: Lang) {
  private val resource = Babel.resource(lang)

  val notRecording: String = resource.getString("save.not_recording")
  val channelNotFound: (String) -> String = { channel -> resource.getString("save.channel_not_found").format(channel) }
  val description: String = resource.getString("save.description")
}

class SaveLocation(lang: Lang) {
  private val resource = Babel.resource(lang)

  val current: String = resource.getString("savelocation.current")
  val channel: (String) -> String = { channel -> resource.getString("savelocation.channel").format("**$channel**") }
  val permissions: (String) -> String =
    { channel -> resource.getString("savelocation.permissions").format("**$channel**") }
}

class Slash(lang: Lang) {
  private val resource = Babel.resource(lang)

  val inGuild: String = resource.getString("slash.in_guild")
}

class Stop(lang: Lang) {
  private val resource = Babel.resource(lang)

  val leaveChannel: (String) -> String =
    { channelId -> resource.getString("stop.leave_channel").format("**<#$channelId>**") }
  val noChannel: String = resource.getString("stop.no_channel")
}

class Volume(lang: Lang) {
  private val resource = Babel.resource(lang)

  val recording: (String) -> String = { volume -> resource.getString("volume.recording").format("**$volume%**") }
  val notRecording: String = resource.getString("volume.not_recording")
}
