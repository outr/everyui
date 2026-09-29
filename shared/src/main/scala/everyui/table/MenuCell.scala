package everyui.table

import everyui.Color
import everyui.component.{Container, MaterialIconView, TextView}
import everyui.font.MaterialIcon
import everyui.list.ListRow
import everyui.menu.{Menu, MenuItem}
import everyui.theme.Theme
import reactify.*

/** A cell that edits by a menu: what `text` makes of the item, with an arrow, in a small box that a click (or F2 on its
  * row) drops `items`' menu from. The menu's entries do the editing: a status changed, say, after a confirmation and a
  * post to the server, with the changed row put back (`PagedSource.replace`, or the app's `items`).
  *
  * {{{
  *   GridColumn[Entity]("status", "Status", 130.0, () => new MenuCell[Entity](_.status.label, e =>
  *     Status.all.map(s => MenuItem(s.label, () => confirmStatus(e, s), checked = Some(s == e.status)))))
  * }}} */
class MenuCell[T](text: T => String, items: T => List[MenuItem], colour: T => Option[Color] = (_: T) => None)
  extends GridCell[T] {
  val label: TextView = new TextView {
    content := item().map(text).getOrElse("")
    font.size @= 12.5
    color := item().flatMap(colour).getOrElse(Theme.text())
  }
  private val arrow = new MaterialIconView(MaterialIcon.ArrowDropDown, 18.0) {
    color := Theme.muted()
  }
  /** The box the menu drops from. */
  val box: Container = new Container {
    size.height @= 22.0
    size.width := math.min(MenuCell.this.size.width() - ListRow.inset * 2.0, label.size.width() + 30.0)
    radius @= 4.0
    borderWidth @= 1.0
    // (an edge that shows on a chosen row's colour as well as on the page)
    borderColor := Theme.muted().withAlpha(0.45)
    backgroundColor := (if (event.pointer.over()) Theme.controlLit() else Color.Clear)
    cursor @= "pointer"
    label.position.left := ListRow.along(8.0, label.size.width(), size.width())
    label.position.top := (size.height() - label.size.height()) / 2.0
    arrow.position.left := ListRow.along(size.width() - 20.0, 18.0, size.width())
    arrow.position.top @= 2.0
    children ++= List(label, arrow)
  }
  box.position.left := ListRow.along(ListRow.inset, box.size.width(), size.width())
  box.position.top := (size.height() - box.size.height()) / 2.0
  box.event.click.attach { e =>
    e.consume()
    edit()
  }
  children += box

  override def edits: Boolean = true

  override def edit(): Unit = item().foreach(t => Menu.below(box, items(t)))

  override def kind: String = "MenuCell"
}
