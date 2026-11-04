package everyui.rich

import everyui.component.Component
import everyui.render.Nodes

/** The platform's editing surface for a rich document (`Nodes.richEditor`), in the focus layer as a field is: focused
  * by a click, typed into, and given the platform's focus with the caret where the editor last had it. */
class RichSurface(caret: () => RichRange) extends Component(Nodes.richEditor()) {
  focusable @= true
  clickFocus @= true
  override def takesTyping: Boolean = true

  focused.attach(f => if (f) node.focusRich(caret()))
  node.onInputFocus(() => focus())

  override private[everyui] def refocus(): Unit = node.focusRich(caret())

  override def kind: String = "RichSurface"
}
