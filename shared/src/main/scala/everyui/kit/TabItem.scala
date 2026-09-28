package everyui.kit

import everyui.component.*
import everyui.Color
import everyui.component.types.FontWeight
import everyui.font.MaterialIcon
import everyui.layout.HorizontalLayout
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** One tab of a `TabBar`: its label, in the accent and underlined while it is the one shown, lit while focused. A
  * click shows it, and so does the focus landing on it where the bar says (`showOnFocus`: a remote moving along).
  *
  * Around the label are slots an app fills: `icon` before it (a file's type), `marker` after it (an unsaved file's
  * dot, a count); and a closable tab ends in a close button, which asks the bar to close it (`TabBar.closing`) rather
  * than showing it, as a click on it with the middle button does. */
class TabItem(val key: String, title: String, current: Var[String], closable: Boolean = false,
              close: String => Unit = _ => (), showOnFocus: Boolean = true) extends Container {
  val label: Var[String] = Var(title)
  val icon: Var[Option[Component]] = Var(None)
  val marker: Var[Option[Component]] = Var(None)

  private def selected: Boolean = current() == key
  private val text = new TextView {
    content := label()
    font.size @= 16.0
    font.weight := (if (selected) FontWeight.Bold else FontWeight.SemiBold)
    color := (if (selected) Theme.accent() else if (TabItem.this.focused()) Theme.text() else Theme.muted())
  }
  /** The close button, on a closable tab. */
  val closeButton: Option[MaterialIconView] = if (!closable) None else Some(new MaterialIconView(MaterialIcon.Close, 16.0) {
    color := Theme.muted()
    event.click.attach { e =>
      e.consume()
      close(key)
    }
  })
  private val row = new Container {
    layout @= Some(HorizontalLayout(6.0, middle = true))
    children := icon().toVector ++ Vector(text) ++ marker().toVector ++ closeButton.toVector
  }
  private val underline = new Container {
    size.height @= 3.0
    backgroundColor := (if (selected) Theme.accent() else Color.Clear)
  }

  focusable @= true
  size.width := row.size.width() + TabItem.padH * 2.0
  size.height @= TabBar.height
  backgroundColor := (if (focused()) Theme.controlLit() else Color.Clear)
  row.position.left @= TabItem.padH
  row.position.top := (TabBar.height - row.size.height()) / 2.0
  underline.size.width := size.width()
  underline.position.top @= TabBar.height - 3.0
  children ++= List(row, underline)
  // on a turn of its own: the focus is still settling, and what the tab shows may be a few dozen components
  if (showOnFocus) focused.attach(on => if (on && !selected) Platform.later(() => current @= key))
  event.click.attach { e =>
    e.consume()
    current @= key
  }
  // the middle button closes a closable tab, as a browser's does
  if (closable) event.pointer.middle.attach { e =>
    e.consume()
    close(key)
  }
}

object TabItem {
  val padH: Double = 18.0
}
