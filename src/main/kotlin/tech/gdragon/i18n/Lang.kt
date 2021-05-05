/**
 * Inspired by http://ayedo.github.io/internationalization/2020/02/15/typesafe-i18n-made-easy.html
 */
package tech.gdragon.i18n

import tech.gdragon.commands.audio.Save
import java.lang.IllegalArgumentException
import java.util.*

/**
 * Locale(EN).command.notRecording
 * Generate a map from String -> String
 */

enum class Lang {
  EN,
  ES,
  ZH
}

class Save(lang: Locale) {
  private val resource = ResourceBundle.getBundle(Save::class.simpleName!!, lang)

  val notRecording: String = resource.getString("not_recording")
  val channelNotFound: (String) -> String = { channel -> resource.getString("channel_not_found").format(channel) }
  val usage: (String) -> String = { prefix -> resource.getString("usage").format(prefix) }
}


fun main() {
  val resourceBundle = ResourceBundle.getBundle(Save::class.simpleName!!, Locale.SIMPLIFIED_CHINESE)
//  println(resourceBundle.getString("app.command.save.not_recording"))

  resourceBundle.keySet().forEach(::println)
}
