package everyui.component

import everyui.component.feature.FontFeature
import everyui.component.types.Prop
import everyui.render.Nodes
import reactify.*

/** The platform's own text field as a component — an `<input>`, a JavaFX `TextField`, an Android `EditText` — so
  * typing, the caret, selection, the clipboard, autofill and an input method are the platform's. `text` is what it
  * holds, both ways. A `secure` one hides what is typed. [[TextInput]] is the field a form uses. */
class InputView(secure: Boolean = false) extends Component(Nodes.input(secure)) {
  val text: Var[String] = Var("")
  val font: FontFeature = new FontFeature(this)
  /** What it says while empty. */
  lazy val placeholder: Prop[String] = new Prop[String]("", p => node.setPlaceholder(p))

  text.attach(v => if (node.getValue() != v) node.setValue(v))
  node.onValue(v => if (text() != v) text @= v)

  override def kind: String = "InputView"
}
