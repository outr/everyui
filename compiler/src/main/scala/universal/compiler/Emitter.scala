package universal.compiler

import scala.tasty.inspector.Inspector

/** A backend of universal-ui's compiler: walks the program's TASTy, then writes its output. */
trait Emitter extends Inspector:
  def writeOutput(): Unit
  /** Constructs that could not be translated, one line each. */
  def unsupported: List[String]
