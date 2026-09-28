package everyui.menu

import everyui.component.*
import everyui.component.types.Semantics
import everyui.event.Key
import everyui.font.MaterialIcon
import everyui.layout.HorizontalLayout
import everyui.theme.Theme
import reactify.*

/** A button with its alternatives beside it: `main` does the usual thing, and the arrow next to it drops a [[Menu]] of
  * `alternatives` ("Commit", then "Commit and Push…" under the arrow). The two take the focus in turn; the menu also
  * opens on the menu key or OK held on the main button (a remote's long press), and on Down from either with a
  * keyboard.
  *
  * {{{
  *   val commit = new SplitButton("Commit", () => commit(), List(
  *     MenuItem("Commit and Push…", () => commitAndPush()),
  *     MenuItem("Amend", () => amend())), moreLabel = "More commit actions")
  * }}} */
class SplitButton(label: String, action: () => Unit, alternatives: => List[MenuItem], style: ButtonStyle = ButtonStyle.Tonal,
                  moreLabel: String = "", points: Double = 13.0) extends Container {
  val main: Button = new Button(label, action, style, points = points) {
    override def keepsKey(key: Key): Boolean = SplitButton.opens(key)
  }
  /** The arrow that drops the alternatives. */
  val more: Container = new Container {
    override def keepsKey(key: Key): Boolean = SplitButton.opens(key)
    private val icon = new MaterialIconView(MaterialIcon.ArrowDropDown, points * 1.6)
    focusable @= true
    tooltip @= moreLabel
    semantics @= Semantics.button(moreLabel)
    size.height := main.size.height()
    size.width := main.size.height() * 0.9
    radius := size.height() / 2.0
    backgroundColor := (if (focused() || event.pointer.over()) Theme.controlLit() else Theme.control())
    borderWidth := (if (focused()) 2.5 else 0.0)
    borderColor := Theme.accent()
    icon.color := Theme.text()
    icon.position.left := (size.width() - icon.size.width()) / 2.0
    icon.position.top := (size.height() - icon.size.height()) / 2.0
    children += icon
  }
  /** Whether it can be pressed, the arrow with it. */
  val enabled: Var[Boolean] = main.enabled

  layout @= Some(HorizontalLayout(2.0))
  children ++= List(main, more)
  more.event.click.attach { e =>
    e.consume()
    open()
  }
  main.event.hold.attach(_ => open())
  List(main, more).foreach(_.event.key.down.attach(k => if (SplitButton.opens(k.key)) open()))

  /** The alternatives, dropped from the button. */
  def open(): Unit = if (enabled()) Menu.below(this, alternatives)

  override def kind: String = "SplitButton(" + label + ")"
}

object SplitButton {
  /** The keys that drop the alternatives, which the buttons keep from the focus layer: the menu key, and Down with a
    * keyboard (with a remote Down moves on as ever, and OK held opens it). */
  def opens(key: Key): Boolean =
    key == Key.ContextMenu || (key == Key.ArrowDown && !everyui.render.Platform.remoteNavigation())
}
