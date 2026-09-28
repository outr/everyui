package everyui.monaco.gallery

import everyui.component.{Choice, Container}
import everyui.layout.HorizontalLayout
import reactify.*

/** A switch: `label` lit while on, `change` told of each flip. */
class Toggle(label: String, initial: Boolean)(change: Boolean => Unit) extends Container {
  val on: Var[Boolean] = own(Var(initial))
  layout @= Some(HorizontalLayout())

  private def build(): Unit = {
    children.clear()
    children += new Choice(label, on(), () => {
      on @= !on()
      change(on())
    })
  }

  on.attach(_ => build())
  build()
}
