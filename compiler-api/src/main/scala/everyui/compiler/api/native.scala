package everyui.compiler.api

import scala.annotation.StaticAnnotation

/** Marks a facade: a declaration whose implementation is a platform global.
  *
  * The Roku compiler emits no code for a `@native` declaration and emits calls
  * to it verbatim — `uiSetText(n, s)` in Scala is `uiSetText(n, s)` in
  * BrightScript (see `roku/src/main/scala/everyui/roku/facades.scala`).
  */
final class native extends StaticAnnotation

object native:
  /** The body of a facade member: `def apply(): SGNode = native.value`.
    * `Nothing`-typed so it fits any signature; it only runs if a facade is
    * called on a platform it doesn't describe, which is a bug. */
  def value: Nothing =
    throw new Error("everyui.compiler.api.native.value was evaluated: a @native facade was called outside its platform")
