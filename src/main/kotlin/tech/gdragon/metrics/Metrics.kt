package tech.gdragon.metrics

import com.codahale.metrics.CsvReporter
import com.codahale.metrics.MetricRegistry
import org.koin.core.KoinComponent
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

class Metrics : KoinComponent {
  val registry = MetricRegistry()

  private val reporter = CsvReporter.forRegistry(registry)
    .formatFor(Locale.US)
    .convertRatesTo(TimeUnit.SECONDS)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .build(File("./data/metrics/"))

  init {
    reporter.start(30, TimeUnit.SECONDS)
  }
}
