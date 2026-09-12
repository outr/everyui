package universal.ui

import reactify.Var

/** **The atomic seam** — youi's `Prop[T](getter, setter)`, a real reactify
  * `Var` on every backend.
  *
  * Writing it (`:=`, `@=`, or a dependency it tracks changing) fires the
  * attached reaction, which pushes the value into the backend node through
  * `setter`; `refresh()` pulls the node's current value back in through
  * `getter`. Nothing here knows about the DOM or SceneGraph — only which
  * getter and setter it is handed.
  *
  * `valueChanging` is youi's re-entrancy guard verbatim: a refresh writes the
  * node's own value back into the cell, and must not echo it out to the node.
  *
  * Differs from youi only in taking `getter` as `() => T` rather than by-name,
  * and in constructing through Var's no-arg constructor followed by `refresh()`
  * rather than Var's by-name secondary constructor.
  */
class Prop[T](getter: () => T, setter: T => Unit) extends Var[T]:
  private var valueChanging: Boolean = false

  refresh()
  attach { v =>
    if !valueChanging then setter(v)
  }

  /** Pull the backend node's current value back into the cell without
    * writing it out again. */
  def refresh(): Unit =
    valueChanging = true
    try this @= getter()
    finally valueChanging = false
