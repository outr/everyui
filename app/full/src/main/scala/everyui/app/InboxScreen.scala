package everyui.app

import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.component.{Button, ButtonStyle, Column, Component, TextView}
import everyui.net.*
import everyui.render.Platform
import everyui.theme.Theme
import everyui.widget.Widgets

/** The inbox demo's first screen (InboxDemo), at `/inbox`: the widgets placed, and a button to draw them again
  * (`Widgets.refresh`). */
class InboxScreen extends UIScreen {
  override def manager: ScreenManager = InboxDemo
  override def title: String = "Inbox"
  override def path: URLPath = path"/inbox"

  private val placed = TextView("", 14.0, Theme.muted())
  private val redraw = new Button("Redraw the widgets", () => {
    Widgets.refresh("inbox")
    count()
  }, style = ButtonStyle.Filled)

  override def defaultFocus(): Component = redraw

  override def createUI(): Task[Unit] = Task {
    container.children += Column(12.0, padding = 16.0)(TextView("Inbox", 22.0), placed, redraw)
  }

  override protected def activate(): Task[Unit] = super.activate().map { _ =>
    Platform.log("inbox: " + Platform.path())
    count()
  }

  private def count(): Unit = {
    val widgets = Widgets.placed("inbox")
    placed.content @= widgets.size.toString + " placed" + widgets.map(w => " #" + w.id.toString + " " +
      w.size.toString + " " + w.choices.toList.sorted.mkString(",") + (if (w.countsOnly) " counts only" else ""))
      .mkString
  }
}
