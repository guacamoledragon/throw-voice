/**
 * Heavily insipired by https://github.com/nrepl/nrepl-java-example
 */
package tech.gdragon.repl

import clojure.java.api.Clojure
import mu.KotlinLogging

class REPL {
  val logger = KotlinLogging.logger { }
  val port = "7888"
  private var _server: Any? = null

  init {
    val require = Clojure.`var`("clojure.core", "require")
    require.invoke(Clojure.read("nrepl.server"))

    val start = Clojure.`var`("nrepl.server", "start-server")
    _server = start.invoke(
      Clojure.read(":port"), Clojure.read(port),
      Clojure.read(":handler"), Clojure.`var`("nrepl.server", "default-handler").invoke()
    )

    logger.info {
      "Starting nREPL Server on port $port"
    }
  }

  fun shutdown(): Unit {
    _server?.let(Clojure.`var`("nrepl.server", "stop-server")::invoke)
  }
}
