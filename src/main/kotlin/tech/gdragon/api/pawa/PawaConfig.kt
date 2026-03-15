package tech.gdragon.api.pawa

/**
 * Selects which audio recorder implementation to use at runtime.
 *
 * Set via the `BOT_RECORDER_TYPE` environment variable / Koin property.
 */
enum class RecorderType {
  /** Legacy RxJava-based [tech.gdragon.listener.CombinedAudioRecorderHandler]. */
  LEGACY,
  /** Newer BlockingQueue-based [tech.gdragon.listener.BaseAudioRecorder] hierarchy. */
  QUEUE;

  companion object {
    fun fromString(value: String): RecorderType =
      when (value.uppercase()) {
        "QUEUE" -> QUEUE
        "LEGACY" -> LEGACY
        else -> throw IllegalArgumentException(
          "Invalid BOT_RECORDER_TYPE: '$value'. Must be LEGACY or QUEUE."
        )
      }
  }
}

class PawaConfig private constructor(
  val appUrl: String,
  val dataDirectory: String,
  val isStandalone: Boolean,
  val recorderType: RecorderType
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
    var isStandalone: Boolean? = null,

    /**
     * Which recorder implementation to use. Must be set via `BOT_RECORDER_TYPE`.
     */
    var recorderType: RecorderType? = null
  )

  companion object {
    operator fun invoke(body: Builder.() -> Unit = {}): PawaConfig {
      val builder = Builder().apply(body)
      return PawaConfig(
        appUrl = builder.appUrl.ifBlank { "discord://" },
        dataDirectory = builder.dataDirectory.ifBlank { "./" },
        isStandalone = builder.isStandalone ?: false,
        recorderType = requireNotNull(builder.recorderType) {
          "BOT_RECORDER_TYPE must be set. Valid values: LEGACY, QUEUE"
        }
      )
    }
  }
}
