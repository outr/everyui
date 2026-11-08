package everyui.app

import everyui.component.{Button, ButtonStyle, Column, TextView}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import everyui.widget.Widgets

/** Where the "inbox" home-screen widget's taps open the app (InboxWidgets): `/inbox`, or `/inbox/<id>` for one
  * message, written on the screen and logged ("[ui] inbox: /inbox/m2"), at the launch and at each tap after; with
  * the widgets placed, and a button to draw them again (`Widgets.refresh`). `demo=inbox` opens it too. */
object InboxDemo {
  private val opened = TextView("", 18.0)
  private val placed = TextView("", 14.0, Theme.muted())

  def run(): Unit = {
    InboxWidgets.kinds.foreach(Widgets.register)
    ui.backgroundColor := Theme.background()
    val page = Column(12.0, padding = 16.0)(TextView("Inbox", 22.0), opened, placed,
      new Button("Redraw the widgets", () => {
        Widgets.refresh("inbox")
        count()
      }, style = ButtonStyle.Filled))
    ui.children += page
    show(Platform.path())
    count()
    Platform.onLaunch(params => params.get("path").foreach(show))
  }

  private def show(path: String): Unit = {
    opened.content @= (if (path.isEmpty) "Opened with no path" else "Opened at " + path)
    Platform.log("inbox: " + path)
  }

  private def count(): Unit = {
    val widgets = Widgets.placed("inbox")
    placed.content @= widgets.size.toString + " placed" + widgets.map(w => " #" + w.id.toString + " " +
      w.size.toString + " " + w.choices.toList.sorted.mkString(",") + (if (w.countsOnly) " counts only" else ""))
      .mkString
  }
}
