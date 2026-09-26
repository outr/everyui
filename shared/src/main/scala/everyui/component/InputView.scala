package everyui.component

import everyui.component.feature.FontFeature
import everyui.component.types.Prop
import everyui.render.Nodes
import reactify.*

/** The platform's own text field as a component — an `<input>`, a JavaFX `TextField`, an Android `EditText` — so
  * typing, the caret, selection, the clipboard, autofill and an input method are the platform's. `text` is what it
  * holds, both ways. A `secure` one hides what is typed; a `multiline` one is a box of lines (a `<textarea>`, where the
  * platform has one). [[TextInput]] is the field a form uses. */
class InputView(secure: Boolean = false, multiline: Boolean = false)
  extends Component(if (multiline) Nodes.textArea() else Nodes.input(secure)) {
  val text: Var[String] = own(Var(""))
  val font: FontFeature = new FontFeature(this)
  /** What it says while empty. */
  lazy val placeholder: Prop[String] = own(new Prop[String]("", p => node.setPlaceholder(p)))
  /** What the keyboard's enter key says it does, where the keyboard says: "done", "search", "next", "go" or "send". */
  lazy val action: Prop[String] = own(new Prop[String]("done", a => node.setInputAction(a)))

  /** Choose all of what it holds, so what is typed next replaces it (a value being changed rather than added to). */
  def selectAll(): Unit = node.selectAllInput()

  text.attach(v => if (node.getValue() != v) node.setValue(v))
  node.onValue(v => if (text() != v) text @= v)

  override def kind: String = "InputView"
}
