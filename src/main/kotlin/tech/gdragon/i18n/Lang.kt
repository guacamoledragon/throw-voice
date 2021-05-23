/**
 * Inspired by http://ayedo.github.io/internationalization/2020/02/15/typesafe-i18n-made-easy.html
 */
package tech.gdragon.i18n

import java.util.*

enum class Lang {
  EN {
    override val locale = Locale.ENGLISH!!
    override val flagEmoji: String = ":flag_us:"
  },
  PT_BR {
    override val locale: Locale = Locale("pt", "BR")
    override val flagEmoji: String = ":flag_br:"
  },
  ZH {
    override val locale = Locale.CHINESE!!
    override val flagEmoji: String = ":flag_cn:"
  };

  abstract val locale: Locale
  abstract val flagEmoji: String
}

object Babel {
  val languages = Lang.values().joinToString("|") { it.name.lowercase() }

  fun resource(lang: Lang): ResourceBundle = ResourceBundle.getBundle("translations", lang.locale)

  private val help: MutableMap<Lang, Help> = mutableMapOf()
  fun help(lang: Lang) = help.getOrPut(lang) { Help(lang) }

  private val language: MutableMap<Lang, Language> = mutableMapOf()
  fun language(lang: Lang) = language.getOrPut(lang) { Language(lang) }

  private val record: MutableMap<Lang, Record> = mutableMapOf()
  fun record(lang: Lang) = record.getOrPut(lang) { Record(lang) }

  private val save: MutableMap<Lang, Save> = mutableMapOf()
  fun save(lang: Lang) = save.getOrPut(lang) { Save(lang) }

  private val stop: MutableMap<Lang, Stop> = mutableMapOf()
  fun stop(lang: Lang) = stop.getOrPut(lang) { Stop(lang) }

  fun valid(lang: String) = try {
    Lang.valueOf(lang)
    true
  } catch (ex: IllegalArgumentException) {
    false
  }
}

class Help(lang: Lang) {
  private val resource = Babel.resource(lang)

  val checkDm: (String) -> String = { userId -> resource.getString("help.check_dm").format("**<@$userId>**") }
  val embedTitle: (String) -> String = { website -> resource.getString("help.embed_title").format(website) }
  val usage: (String) -> String = { prefix -> resource.getString("help.usage").format(prefix) }
}

class Language(lang: Lang) {
  private val resource = Babel.resource(lang)

  val usage: (String) -> String = { prefix ->
    resource
      .getString("language.usage")
      .format(prefix, Babel.languages)
  }
}

class Record(lang: Lang) {
  private val resource = Babel.resource(lang)

  val alreadyInChannel: (String) -> String = { channelId -> resource.getString("record.already_in_channel").format("**<#$channelId>**") }
  val cannotRecord: (String) -> String  = { channelId -> resource.getString("record.cannot_record").format("**<#$channelId>**") }
  val joinChannel: String = resource.getString("record.join_channel")
  val usage: (String) -> String  = { prefix -> resource.getString("record.usage").format(prefix) }
}

class Save(lang: Lang) {
  private val resource = Babel.resource(lang)

  val notRecording: String = resource.getString("save.not_recording")
  val channelNotFound: (String) -> String = { channel -> resource.getString("save.channel_not_found").format(channel) }
  val usage: (String) -> String = { prefix -> resource.getString("save.usage").format(prefix, prefix) }
  val description: String = resource.getString("save.description")
}

class Stop(lang: Lang) {
  private val resource = Babel.resource(lang)

  val leaveChannel: (String) -> String = { channelId -> resource.getString("stop.leave_channel").format("**<#$channelId>**") }
  val noChannel = resource.getString("stop.no_channel")
  val usage: (String) -> String = { prefix -> resource.getString("stop.usage").format(prefix) }
}
