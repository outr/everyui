package everyui.app

import everyui.Color
import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.component.{Button, Component, Container, ImageView, ProgressBar, Row, TextView}
import everyui.component.types.FontWeight
import everyui.net.*
import reactify.*

/** The player the dialog demo opens as its menu closes (`DialogCloseDemo`): the title's picture behind, its name and
  * where it is, a bar of where it has got to and a row of controls, as a player waiting for its stream shows them. Made
  * for one showing. */
class DialogPlayerScreen extends UIScreen {
  override def manager: ScreenManager = DialogCloseDemo
  override def title: String = "Player"
  override def path: URLPath = path"/dialogplayer"
  override def oneShowing: Boolean = true

  private val pause = new Button("Pause")

  override def defaultFocus(): Component = pause

  override def createUI(): Task[Unit] = Task {
    container.backgroundColor @= Color.Black
    val backdrop = new ImageView {
      sizedByPicture @= false
      size.width := container.size.width()
      size.height := container.size.height()
      opacity @= 0.5
    }
    everyui.image.Image("/images/poster.jpg?backdrop").map(loaded => backdrop.image @= loaded).startUnit()
    val controls = new Container {
      position.left @= 48.0
      position.bottom := container.size.height() - 32.0
      size.width := container.size.width() - 96.0
      size.height @= 150.0
      children ++= List(
        new TextView {
          content @= "A Title of Some Length"
          font.size @= 24.0
          font.weight @= FontWeight.Bold
          color @= Color.White
        },
        new TextView {
          content @= "S1 E3 · The Episode's Name"
          font.size @= 15.0
          color @= Color.White.withAlpha(0.7)
          position.top @= 36.0
        },
        {
          val bar = ProgressBar(0.4, 600.0, 5.0)
          bar.position.top @= 70.0
          bar
        },
        new TextView {
          content @= "1:04:05 / 1:56:10"
          font.size @= 13.0
          color @= Color.White
          position.left @= 620.0
          position.top @= 64.0
        },
        {
          val row = Row(12.0)(new Button("Back 10"), pause, new Button("Ahead 30"), new Button("Subtitles"),
            new Button("Audio"), new Button("Next episode"))
          row.position.top @= 92.0
          row
        })
    }
    container.children ++= List(backdrop, controls)
  }
}
