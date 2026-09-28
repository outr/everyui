package everyui.monaco.gallery

import everyui.component.{Choice, Container}
import everyui.layout.{FlowLayout, HorizontalLayout}
import reactify.*

/** One of `options` chosen: a row of choices (wrapped to `width` when given), `choose` told of each new one. */
class Picker(options: List[String], initial: String, width: Double = 0.0)(choose: String => Unit) extends Container {
  val value: Var[String] = own(Var(initial))

  if (width > 0.0) {
    size.width @= width
    layout @= Some(FlowLayout(6.0, 6.0))
  }
  else layout @= Some(HorizontalLayout(6.0, middle = true))

  private def build(): Unit = {
    children.clear()
    children ++= options.map(option => new Choice(option, option == value(), () => if (value() != option) {
      value @= option
      choose(option)
    }))
  }

  value.attach(_ => build())
  build()
}
