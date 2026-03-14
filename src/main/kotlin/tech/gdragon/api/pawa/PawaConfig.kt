package tech.gdragon.api.pawa

/**
 * Selects which audio recorder implementation to use at runtime.
 *
 * Set via the `BOT_RECORDER_IMPL` environment variable / Koin property.
 */
enum class RecorderImpl {
  /** Legacy RxJava-based [tech.gdragon.listener.CombinedAudioRecorderHandler]. */
  LEGACY,
  /** Newer BlockingQueue-based [tech.gdragon.listener.BaseAudioRecorder] hierarchy. */
  QUEUE;

  companion object {
    fun fromString(value: String?): RecorderImpl =
      when (value?.uppercase()) {
        "QUEUE" -> QUEUE
        "LEGACY" -> LEGACY
        else -> LEGACY // default to legacy for backward compatibility
      }
  }
}

class PawaConfig private constructor(
  val appUrl: String,
  val dataDirectory: String,
  val isStandalone: Boolean,
  val recorderImpl: RecorderImpl
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
     * Which recorder implementation to use. Defaults to [RecorderImpl.LEGACY].
     */
    var recorderImpl: RecorderImpl? = null
  )

  companion object {
    operator fun invoke(body: Builder.() -> Unit = {}): PawaConfig {
      val builder = Builder().apply(body)
      return PawaConfig(
        appUrl = builder.appUrl.ifBlank { "discord://" },
        dataDirectory = builder.dataDirectory.ifBlank { "./" },
        isStandalone = builder.isStandalone ?: false,
        recorderImpl = builder.recorderImpl ?: RecorderImpl.LEGACY
      )
    }
  }
}
