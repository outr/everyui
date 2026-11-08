package everyui.render

import everyui.widget.WidgetRequest

/** Home-screen widgets (`everyui.widget.Widgets`). A page has no home screen whose widgets it draws,
  * so none are placed and the rest does nothing. */
object WidgetAccess {
  def refresh(kind: String): Unit = ()

  def refreshAll(): Unit = ()

  def placed(kind: String): Vector[WidgetRequest] = Vector.empty

  def configure(id: Int): Unit = ()
}
