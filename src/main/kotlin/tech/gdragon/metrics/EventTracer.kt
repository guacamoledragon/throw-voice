package tech.gdragon.metrics

import io.honeycomb.libhoney.HoneyClient
import io.honeycomb.libhoney.LibHoney.create
import io.honeycomb.libhoney.LibHoney.options

interface EventTracer {
  fun sendEvent(fields: Map<String, Any>)
}

class Honey(apiKey: String) : EventTracer {
  private val honeyClient: HoneyClient = create(
    options()
      .setWriteKey(apiKey)
      .setDataset("pawa")
      .setSampleRate(2)
      .build()
  )

  override fun sendEvent(fields: Map<String, Any>) {
    honeyClient
      .createEvent()
      .addFields(fields)
      .setTimestamp(System.currentTimeMillis())
      .send()
  }
}
