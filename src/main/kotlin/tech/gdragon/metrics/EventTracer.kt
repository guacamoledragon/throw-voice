package tech.gdragon.metrics

interface EventTracer {
  fun sendEvent(fields: Map<String, Any>)

  fun shutdown()
}

class NoOpTracer() : EventTracer {
  override fun sendEvent(fields: Map<String, Any>) {
    // No Op
  }

  override fun shutdown() {
    // No Op
  }
}
