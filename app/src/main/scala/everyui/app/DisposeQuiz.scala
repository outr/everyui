package everyui.app

import everyui.app.screen.{ScreenManager, ScrollScreen}
import everyui.async.Task
import everyui.component.{Button, Component, Container, TextView}
import everyui.layout.FlowLayout
import everyui.net.*
import everyui.theme.Theme

/** A quiz whose board is drawn afresh for each question, as a game's is: the question, four answers and a grid of
  * tiles, the last board taken apart as the next goes in (`children.replaceMaking`), so only the one showing is held. */
class DisposeQuiz(val manager: ScreenManager) extends ScrollScreen {
  override def title: String = "Quiz"
  override def path: URLPath = path"/quiz"

  /** Where the board goes. */
  val stage: Container = new Container
  private var first: Component = stage

  override def defaultFocus(): Component = first

  /** The board for question `n`, in place of the last. */
  def ask(n: Int): Unit = {
    val answers = (1 to 4).toList.map(k => new Button("Answer " + k.toString))
    val tiles = new Container {
      size.width @= 600.0
      layout @= Some(FlowLayout(6.0, 6.0))
    }
    tiles.children ++= (1 to DisposeQuiz.Tiles).toList.map { k =>
      val tile = new Container {
        size.width @= 60.0
        size.height @= 40.0
        backgroundColor := Theme.surface()
      }
      tile.children += TextView(k.toString, 11.0, Theme.muted())
      tile
    }
    val board = everyui.component.Column(12.0)(TextView(DisposeQuiz.Question + " " + n.toString, 22.0),
      everyui.component.Row(8.0)(answers*), tiles)
    stage.children.replaceMaking(Vector(board))
    first = answers.head
    first.focus()
  }

  override def createUI(): Task[Unit] = Task {
    container.backgroundColor := Theme.background()
    body.children += stage
  }
}

object DisposeQuiz {
  /** What each board's question says, before its number: what is looked for once it is gone. */
  val Question: String = "Question"
  val Tiles: Int = 40
}
