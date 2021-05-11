/**
 * Inspired by http://ayedo.github.io/internationalization/2020/02/15/typesafe-i18n-made-easy.html
 */
package tech.gdragon.i18n

import tech.gdragon.commands.audio.Save
import java.util.*

enum class Lang {
  EN {
    override val locale = Locale.ENGLISH
  },
  ES {
    override val locale = Locale("es")
  },
  ZH {
    override val locale = Locale.CHINESE
  };

  abstract val locale: Locale
}

class Save(lang: Lang) {
  private val resource = ResourceBundle.getBundle(Save::class.simpleName!!, lang.locale)

  val notRecording: String = resource.getString("not_recording")
  val channelNotFound: (String) -> String = { channel -> resource.getString("channel_not_found").format(channel) }
  val usage: (String) -> String = { prefix -> resource.getString("usage").format(prefix) }
  val description: String = resource.getString("description")
}
