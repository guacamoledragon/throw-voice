package tech.gdragon.repl

import net.matlux.NreplServer
import org.koin.core.component.KoinComponent

class REPL : KoinComponent {
  val nRepl = NreplServer(NreplServer.DEFAULT_PORT)
}
