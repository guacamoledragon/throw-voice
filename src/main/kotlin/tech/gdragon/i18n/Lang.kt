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
  val languages = Lang.values().joinToString("|") { it.name.lowercase() }

  fun resource(lang: Lang): ResourceBundle = ResourceBundle.getBundle("translations", lang.locale)

  private val autosave: MutableMap<Lang, AutoSave> = mutableMapOf()
  fun autosave(lang: Lang) = autosave.getOrPut(lang) { AutoSave(lang) }

  private val autostop: MutableMap<Lang, AutoStop> = mutableMapOf()
  fun autostop(lang: Lang) = autostop.getOrPut(lang) { AutoStop(lang) }

  private val alias: MutableMap<Lang, Alias> = mutableMapOf()
  fun alias(lang: Lang) = alias.getOrPut(lang) { Alias(lang) }

  private val help: MutableMap<Lang, Help> = mutableMapOf()
  fun help(lang: Lang) = help.getOrPut(lang) { Help(lang) }

  private val ignore: MutableMap<Lang, Ignore> = mutableMapOf()
  fun ignore(lang: Lang) = ignore.getOrPut(lang) { Ignore(lang) }

  private val language: MutableMap<Lang, Language> = mutableMapOf()
  fun language(lang: Lang) = language.getOrPut(lang) { Language(lang) }

  private val prefix: MutableMap<Lang, Prefix> = mutableMapOf()
  fun prefix(lang: Lang) = prefix.getOrPut(lang) { Prefix(lang) }

  private val record: MutableMap<Lang, Record> = mutableMapOf()
  fun record(lang: Lang) = record.getOrPut(lang) { Record(lang) }

  private val removealias: MutableMap<Lang, RemoveAlias> = mutableMapOf()
  fun removealias(lang: Lang) = removealias.getOrPut(lang) { RemoveAlias(lang) }

  private val save: MutableMap<Lang, Save> = mutableMapOf()
  fun save(lang: Lang) = save.getOrPut(lang) { Save(lang) }

  private val savelocation: MutableMap<Lang, SaveLocation> = mutableMapOf()
  fun savelocation(lang: Lang) = savelocation.getOrPut(lang) { SaveLocation(lang) }

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
      Alias::class -> alias(lang) as T
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

class Alias(lang: Lang) {
  private val resource = Babel.resource(lang)

  val command: (String) -> String = { alias -> resource.getString("alias.command").format("**`$alias`**") }
  val exists: (String) -> String = { alias -> resource.getString("alias.exists").format("**`$alias`**") }
  val invalid: (String) -> String = { command -> resource.getString("alias.invalid").format("**`$command`**") }
  val new: (String, String) -> String =
    { alias, command -> resource.getString("alias.new").format("**`$alias", "$command`**") }
  val usage: (String) -> String = { s -> resource.getString("alias.usage").format(s) }
}

class AutoSave(lang: Lang) {
  private val resource = Babel.resource(lang)

  val noop = resource.getString("autosave.noop")
  val off = resource.getString("autosave.off")
  val on = resource.getString("autosave.on")
  val usage: (String) -> String = { prefix -> resource.getString("autosave.usage").format(prefix) }
}

class AutoStop(lang: Lang) {
  private val resource = Babel.resource(lang)

  val all: (String) -> String = { number -> resource.getString("autostop.all").format("**$number**") }
  val none: String = resource.getString("autostop.none")
  val one: (String, String) -> String =
    { channelId, number -> resource.getString("autostop.one").format("**<#${channelId}>**", "**$number**") }
  val some: (String) -> String = { channelId -> resource.getString("autostop.some").format("**<#$channelId>**") }
  val usage: (String) -> String = { prefix -> resource.getString("autostop.usage").format(prefix) }
}

class Help(lang: Lang) {
  private val resource = Babel.resource(lang)

  val checkDm: (String) -> String = { userId -> resource.getString("help.check_dm").format("**<@$userId>**") }
  val embedTitle: (String) -> String = { website -> resource.getString("help.embed_title").format(website) }
  val usage: (String) -> String = { prefix -> resource.getString("help.usage").format(prefix) }
}

class Ignore(lang: Lang) {
  private val resource = Babel.resource(lang)

  val beta = resource.getString("ignore.beta")
  val ignore: (String) -> String = { users -> resource.getString("ignore.ignore").format(users) }
  val notRecording = resource.getString("ignore.not_recording")
  val usage: (String) -> String = { prefix -> resource.getString("ignore.usage").format(prefix) }
}

class Language(lang: Lang) {
  private val resource = Babel.resource(lang)

  val usage: (String) -> String = { prefix ->
    resource
      .getString("language.usage")
      .format(prefix, Babel.languages)
  }
}

class Prefix(lang: Lang) {
  private val resource = Babel.resource(lang)

  val changed: (String) -> String = { prefix -> resource.getString("prefix.changed").format("**`${prefix}`**") }
  val notChanged: (String) -> String = { prefix -> resource.getString("prefix.not_changed").format("**`$prefix`**") }
  val usage: (String) -> String = { prefix -> resource.getString("prefix.usage").format(prefix) }
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

  val usage: (String) -> String = { prefix -> resource.getString("record.usage").format(prefix) }
}

class RemoveAlias(lang: Lang) {
  private val resource = Babel.resource(lang)

  val doesNotExist: (String) -> String =
    { alias -> resource.getString("removealias.does_not_exist").format("**`$alias`**") }
  val remove: (String) -> String = { alias -> resource.getString("removealias.remove").format("**`$alias`**") }
  val usage: (String) -> String = { prefix -> resource.getString("removealias.usage").format(prefix) }
}

class Save(lang: Lang) {
  private val resource = Babel.resource(lang)

  val notRecording: String = resource.getString("save.not_recording")
  val channelNotFound: (String) -> String = { channel -> resource.getString("save.channel_not_found").format(channel) }
  val usage: (String) -> String = { prefix -> resource.getString("save.usage").format(prefix, prefix) }
  val description: String = resource.getString("save.description")
}

class SaveLocation(lang: Lang) {
  private val resource = Babel.resource(lang)

  val current: String = resource.getString("savelocation.current")
  val channel: (String) -> String = { channel -> resource.getString("savelocation.channel").format("**$channel**") }
  val permissions: (String) -> String =
    { channel -> resource.getString("savelocation.permissions").format("**$channel**") }
  val notFound: (String) -> String = { channel -> resource.getString("savelocation.not_found").format("**$channel**") }
  val fail: String = resource.getString("savelocation.fail")
  val usage: (String) -> String = { prefix -> resource.getString("savelocation.usage").format(prefix, prefix) }
}

class Slash(lang: Lang) {
  private val resource = Babel.resource(lang)

  val inGuild = resource.getString("slash.in_guild")
}

class Stop(lang: Lang) {
  private val resource = Babel.resource(lang)

  val leaveChannel: (String) -> String =
    { channelId -> resource.getString("stop.leave_channel").format("**<#$channelId>**") }
  val noChannel = resource.getString("stop.no_channel")
  val usage: (String) -> String = { prefix -> resource.getString("stop.usage").format(prefix) }
}

class Volume(lang: Lang) {
  private val resource = Babel.resource(lang)

  val recording: (String) -> String = { volume -> resource.getString("volume.recording").format("**$volume%**") }
  val notRecording: String = resource.getString("volume.not_recording")
  val usage: (String) -> String = { prefix -> resource.getString("volume.usage").format(prefix) }
}
