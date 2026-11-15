package everyui.component.types

import reactify.Var

import scala.util.Success

/** youi's `Prop`, as youi has it: a reactify `Var` whose changes are written out through `setter`, plus any
  * `callbacks` (a component re-measuring itself, say). `refresh()` pulls the current value back in without
  * echoing it out again.
  *
  * Its first value is `getter` as it is, followed by nothing, as youi's ends up once it has refreshed: taken as it is,
  * rather than worked out as a function of what it reads, followed, and then let go of again by a refresh, which for
  * the dozen properties of each component was a tenth of building a screen on a television. `getter` is read where the
  * property is made, so one made from a value (the theme's family) is given it as it is (`option`), not read as a value
  * being worked out reads one, which would follow it.
  *
  * A change is heard by a reaction of its own (`PropWrite`) rather than a function attached to it: a component makes a
  * dozen of these, and each function attached cost a closure, its scope and a reaction wrapping it. */
class Prop[T](getter: => T, setter: T => Unit, callbacks: (() => Unit)*) extends Var[T]() {
  private var valueChanging = false
  // (kept as a list, which is nothing where there are none, rather than the arguments as they were passed)
  private val after: List[() => Unit] = if (callbacks.isEmpty) Nil else callbacks.toList

  start(getter)
  reactions += new PropWrite[T](this)

  /** `v` set: written out, unless it is being pulled back in (`refresh`), and the callbacks told. */
  private[types] def written(v: T): Unit = {
    if (!valueChanging) {
      setter(v)
      // drawn differently: the frame loop runs, if it was idle (Frames)
      everyui.task.Frames.wake()
    }
    if (after.nonEmpty) after.foreach(_())
  }

  private def start(first: T): Unit = {
    state = Success(first)
  }

  def refresh(): Unit = {
    valueChanging = true
    try this @= get
    finally valueChanging = false
  }
}
