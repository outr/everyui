package everyui.store

import fabric.io.{JsonFormatter, JsonParser}
import fabric.rw.*

import scala.util.Try

/** A [[Codec]] of fabric's `RW`: the value kept as compact JSON, and read back through the same `RW`. For an app
  * that has fabric (one with the network module), so a case class it already sends to its server can be kept too:
  *
  * {{{
  * given Codec[Layout] = FabricCodec[Layout]
  * val layout: Var[Layout] = Stored("dashboard", "layout", Layout.default)
  * }}}
  *
  * Text that is not JSON, or JSON the `RW` cannot read (a field since renamed), reads as None, and the default
  * stands. A plain String or number keeps its own [[Codec]] rather than this, so its text stays plain. */
object FabricCodec {
  def apply[T](using rw: RW[T]): Codec[T] = Codec.from[T](
    value => JsonFormatter.Compact(value.json),
    text => Try(JsonParser(text).as[T]).toOption
  )
}
