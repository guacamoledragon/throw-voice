package tech.gdragon.api.pawa

class PawaConfig private constructor(
  val appUrl: String,
  val isStandalone: Boolean
) {

  class Builder(
    /**
     * The URL of the pawa recordings app. Defaults to [https://app.pawa.im](https://app.pawa.im)
     */
    var appUrl: String? = null,

    /**
     * Flag that determines if this instance is standalone. Defaults to `false`.
     */
    var isStandalone: Boolean? = null
  )

  companion object {
    operator fun invoke(body: Builder.() -> Unit = {}): PawaConfig {
      val builder = Builder().apply(body)
      return PawaConfig(
        appUrl = builder.appUrl ?: "https://app.pawa.im",
        isStandalone = builder.isStandalone?: false,
      )
    }
  }
}
