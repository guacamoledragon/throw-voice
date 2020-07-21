package tech.gdragon.repl

import net.matlux.NreplServer
import org.koin.core.KoinComponent

class REPL : KoinComponent {
  val nRepl = NreplServer(NreplServer.DEFAULT_PORT)
}
