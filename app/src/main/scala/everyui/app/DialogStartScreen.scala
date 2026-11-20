package everyui.app

import everyui.Color
import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.component.{Button, Component, ImageView, TextView}
import everyui.component.types.FontWeight
import everyui.net.*
import reactify.*

/** The dialog demo's first screen (`DialogCloseDemo`): a title's picture across it and a button that asks how to play
  * it. */
class DialogStartScreen extends UIScreen {
  override def manager: ScreenManager = DialogCloseDemo
  override def title: String = "Title"
  override def path: URLPath = path"/dialogstart"

  private val play = new Button("Play", () => DialogCloseDemo.ask())

  override def defaultFocus(): Component = play

  override def createUI(): Task[Unit] = Task {
    container.backgroundColor @= Color.fromHex("#101418")
    val backdrop = new ImageView {
      sizedByPicture @= false
      size.width := container.size.width()
      size.height := container.size.height()
      opacity @= 0.4
    }
    everyui.image.Image("/images/poster.jpg?backdrop").map(loaded => backdrop.image @= loaded).startUnit()
    val name = new TextView {
      content @= "A Title of Some Length"
      font.size @= 28.0
      font.weight @= FontWeight.Bold
      color @= Color.White
      position.left @= 48.0
      position.top @= 48.0
    }
    play.position.left @= 48.0
    play.position.top @= 110.0
    container.children ++= List(backdrop, name, play)
  }
}
