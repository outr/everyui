package everyui.table

import everyui.component.MaterialIconView
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** The tick box that leads a row of a `VirtualTable` with `checks` on: ticked while its row is chosen. The table takes
  * a click on it (`VirtualTable.checks`). */
class CheckCell[T] extends GridCell[T] {
  val box: MaterialIconView = new MaterialIconView(MaterialIcon.CheckBoxOutlineBlank, 18.0) {
    icon := (if (selected()) MaterialIcon.CheckBox else MaterialIcon.CheckBoxOutlineBlank)
    color := (if (selected()) Theme.accent() else Theme.muted())
    position.left := (CheckCell.this.size.width() - 18.0) / 2.0
    position.top := (CheckCell.this.size.height() - 18.0) / 2.0
  }
  cursor @= "pointer"
  children += box

  override def kind: String = "CheckCell"
}
