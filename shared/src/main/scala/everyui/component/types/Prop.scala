package everyui.component.types

import reactify.Var

/** youi's `Prop`, as youi has it: a reactify `Var` whose changes are written out through `setter`, plus any
  * `callbacks` (a component re-measuring itself, say). `refresh()` pulls the current value back in without
  * echoing it out again. */
class Prop[T](getter: => T, setter: T => Unit, callbacks: (() => Unit)*) extends Var[T](getter) {
  private var valueChanging = false

  refresh()
  attach { v =>
    if (!valueChanging) {
      setter(v)
      // drawn differently: the frame loop runs, if it was idle (Frames)
      everyui.task.Frames.wake()
    }
    if (callbacks.nonEmpty) callbacks.foreach(_())
  }

  def refresh(): Unit = {
    valueChanging = true
    try this @= get
    finally valueChanging = false
  }
}
