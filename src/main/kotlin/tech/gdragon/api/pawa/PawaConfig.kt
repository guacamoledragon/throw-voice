package tech.gdragon.api.pawa

class PawaConfig private constructor(
  val appUrl: String,
  val dataDirectory: String,
  val isStandalone: Boolean
) {

  class Builder(
    /**
     * The URL of the pawa recordings app. Defaults to [https://app.pawa.im](https://app.pawa.im)
     */
    var appUrl: String = "",

    /**
     * The directory where Pawa will store all of its data, used for recordings and temporary files.
     */
    var dataDirectory: String = "",

    /**
     * Flag that determines if this instance is standalone. Defaults to `false`.
     */
    var isStandalone: Boolean? = null
  )

  companion object {
    operator fun invoke(body: Builder.() -> Unit = {}): PawaConfig {
      val builder = Builder().apply(body)
      return PawaConfig(
        appUrl = builder.appUrl.ifBlank { "discord://" },
        dataDirectory = builder.dataDirectory.ifBlank { "./" },
        isStandalone = builder.isStandalone ?: false
      )
    }
  }
}
