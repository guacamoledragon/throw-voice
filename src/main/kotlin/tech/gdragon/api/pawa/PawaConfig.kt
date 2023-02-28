package tech.gdragon.api.pawa

class PawaConfig private constructor() {
  class Builder()

  companion object {
    operator fun invoke(body: Builder.() -> Unit = {}): PawaConfig {
      val builder = Builder().apply(body)
      return PawaConfig()
    }
  }
}
