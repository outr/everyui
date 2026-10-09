package everyui.kit

import everyui.Color
import everyui.component.*
import everyui.component.types.Semantics
import everyui.event.Key
import everyui.font.MaterialIcon
import everyui.menu.{Menu, MenuItem}
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** The "+" after a `TabBar`'s last tab (`TabBar.addMenu`), as a browser's new-tab button: a muted glyph, as a tab's
  * close button is, that brightens on a plate of its own under the pointer, and lit as a tab is while focused. A click,
  * a tap, Enter, OK and Space drop its menu, made afresh by `items` each time; so does Down where a keyboard drives the
  * app (a remote's Down goes on into what the tabs show, as it does from a tab). `words` is its tooltip and what a
  * screen reader calls it. */
class TabAdd(words: Var[String], items: () => List[MenuItem]) extends Container {
  private def lit: Boolean = event.pointer.over()
  private val plate = new Container {
    size.width @= TabAdd.plate
    size.height @= TabAdd.plate
    radius @= TabAdd.plate / 2.0
    position.left @= (TabAdd.width - TabAdd.plate) / 2.0
    position.top @= (TabBar.height - TabAdd.plate) / 2.0
    backgroundColor := (if (lit) Theme.controlLit() else Color.Clear)
  }
  val icon: MaterialIconView = new MaterialIconView(MaterialIcon.Add, TabAdd.glyph) {
    position.left @= (TabAdd.width - TabAdd.glyph) / 2.0
    position.top @= (TabBar.height - TabAdd.glyph) / 2.0
    color := (if (lit || TabAdd.this.focused()) Theme.text() else Theme.muted())
  }

  focusable @= true
  size.width @= TabAdd.width
  size.height @= TabBar.height
  backgroundColor := (if (focused()) Theme.controlLit() else Color.Clear)
  tooltip := words()
  // a glyph says nothing to a screen reader: what it does does
  semantics := Semantics.button(words())
  // the room around it a finger may land in, on a touch screen
  tapSlop @= true
  children ++= List(plate, icon)
  event.click.attach { e =>
    e.consume()
    open()
  }
  event.key.down.attach(k => if (keepsKey(k.key) && !Platform.keyRepeat()) open())

  override def keepsKey(key: Key): Boolean =
    key == Key.Space || (key == Key.ArrowDown && !Platform.remoteNavigation())

  /** Its menu, dropped from it (a panel in the middle of the screen on a television), where it has entries. */
  def open(): Unit = {
    val list = items()
    if (list.nonEmpty) {
      if (Platform.remoteNavigation()) Menu.show(list, words()) else Menu.below(this, list)
    }
  }

  override def kind: String = "TabAdd"
}

object TabAdd {
  /** How wide it is, beside the tabs, which are as tall as it. */
  val width: Double = 36.0
  /** The plate the pointer lights under the glyph, and the glyph. */
  val plate: Double = 28.0
  val glyph: Double = 18.0
}
