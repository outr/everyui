package everyui.app.durable

import fabric.rw.*

// The durable demo's wire types (app/protocol: the example app and server/ both compile them). Fabric case
// classes with derived codecs — on the JVM and Scala.js fabric's own `RW.gen`, on the emitted backends the
// compiler's derivation of it (RokuEmitter, "fabric RW derivation").

/** The ticker's event: the server's counter and its clock. */
case class Tick(n: Long, at: Long)
object Tick {
  given rw: RW[Tick] = RW.gen
}

/** What a client says about itself when it connects; the server finds a session by it. */
case class ClientInfo(tag: String)
object ClientInfo {
  given rw: RW[ClientInfo] = RW.gen
}

/** The demo's RPC: `echo` answers `say` back; `drop` makes the server close the caller's socket. */
case class DemoRequest(op: String, say: Option[String] = None)
object DemoRequest {
  given rw: RW[DemoRequest] = RW.gen
}

case class DemoResponse(echo: Option[String] = None, dropping: Boolean = false)
object DemoResponse {
  given rw: RW[DemoResponse] = RW.gen
}
