package tech.gdragon.metrics

import io.honeycomb.libhoney.HoneyClient
import io.honeycomb.libhoney.LibHoney.create
import io.honeycomb.libhoney.LibHoney.options

interface EventTracer {
  fun sendEvent(fields: Map<String, Any>)

  fun shutdown()
}

class Honey(apiKey: String) : EventTracer {
  private val honeyClient: HoneyClient = create(
    options()
      .setWriteKey(apiKey)
      .setDataset("pawa")
      .setSampleRate(1)
      .build()
  )

  override fun sendEvent(fields: Map<String, Any>) {
    honeyClient
      .createEvent()
      .addFields(fields)
      .setTimestamp(System.currentTimeMillis())
      .send()
  }

  override fun shutdown() {
    honeyClient.closeOnShutdown()
  }
}

class NoHoney() : EventTracer {
  override fun sendEvent(fields: Map<String, Any>) {
    // No Op
  }

  override fun shutdown() {
    // No Op
  }
}
