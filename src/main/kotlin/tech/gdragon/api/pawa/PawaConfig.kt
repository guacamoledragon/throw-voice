package tech.gdragon.api.pawa

class PawaConfig private constructor(
  val appUrl: String,
  val dataDirectory: String,
  val isStandalone: Boolean,
  val recoverEnabled: Boolean,
  val maintenance: Boolean
) {

  class Builder(
    /**
     * The URL of the pawa recordings app. Falls back to the `discord://` sentinel when unset —
     * consumers check `startsWith("discord://")` to mean "no app URL configured".
     */
    var appUrl: String = "",

    /**
     * The directory where Pawa will store all of its data, used for recordings and temporary files.
     */
    var dataDirectory: String = "",

    /**
     * Flag that determines if this instance is standalone. Defaults to `false`.
     */
    var isStandalone: Boolean? = null,

    /**
     * Whether the Recover button/flow is enabled. Set via `BOT_RECOVER_ENABLED`. Defaults to `true`.
     */
    var recoverEnabled: Boolean? = null,

    /**
     * The start value of maintenance mode. Set via `BOT_MAINTENANCE`. Defaults to `false`.
     */
    var maintenance: Boolean = false
  )

  companion object {
    operator fun invoke(body: Builder.() -> Unit = {}): PawaConfig {
      val builder = Builder().apply(body)
      return PawaConfig(
        appUrl = builder.appUrl.ifBlank { "discord://" },
        dataDirectory = builder.dataDirectory.ifBlank { "./" },
        isStandalone = builder.isStandalone ?: false,
        recoverEnabled = builder.recoverEnabled ?: true,
        maintenance = builder.maintenance
      )
    }
  }
}
