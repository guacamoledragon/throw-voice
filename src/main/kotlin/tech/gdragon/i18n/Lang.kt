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
    override val flagEmoji: String = "flag_br"
  },
  ZH {
    override val locale = Locale.CHINESE!!
    override val flagEmoji: String = "flag_cn"
  };

  abstract val locale: Locale
  abstract val flagEmoji: String
}

object Babel {
  val languages = Lang.values().joinToString("|") { it.name.lowercase() }

  fun resource(lang: Lang): ResourceBundle = ResourceBundle.getBundle("translations", lang.locale)

  private val language: MutableMap<Lang, Language> = mutableMapOf()
  fun language(lang: Lang) = language.getOrPut(lang) { Language(lang) }

  private val save: MutableMap<Lang, Save> = mutableMapOf()
  fun save(lang: Lang) = save.getOrPut(lang) { Save(lang) }

  fun valid(lang: String) = try {
    Lang.valueOf(lang)
    true
  } catch (ex: IllegalArgumentException) {
    false
  }
}

class Language(lang: Lang) {
  private val resource = Babel.resource(lang)

  val usage: (String) -> String = { prefix ->
    resource
      .getString("language.usage")
      .format(prefix, Babel.languages)
  }
}

class Save(lang: Lang) {
  private val resource = Babel.resource(lang)

  val notRecording: String = resource.getString("save.not_recording")
  val channelNotFound: (String) -> String = { channel -> resource.getString("save.channel_not_found").format(channel) }
  val usage: (String) -> String = { prefix -> resource.getString("save.usage").format(prefix, prefix) }
  val description: String = resource.getString("save.description")
}
