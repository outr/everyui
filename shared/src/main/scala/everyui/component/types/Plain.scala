package everyui.component.types

import reactify.Var

import scala.util.Success

/** A `Var` that starts at a value already in hand: what `Var(value)` comes to when `value` reads nothing, without the
  * work of getting there. `Var(value)` takes its value as a function, and works it out as one, watching what it reads
  * and firing what is there to hear it (nothing yet), the same as for a value that follows others; a component makes a
  * dozen such values that start plain (its parent, its measured size, its layout), and on a television that work was a
  * tenth of building a screen. Set and read, it is a `Var` like any other.
  *
  * Only for a value that reads nothing as it is made: `Var(Theme.accent())` follows the theme until it is set, and this
  * would not. */
class Plain[T](value: T) extends Var[T]() {
  function = () => value
  state = Success(value)
}

object Plain {
  def apply[T](value: T): Var[T] = new Plain[T](value)
}
