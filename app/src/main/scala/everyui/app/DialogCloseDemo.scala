package everyui.app

import everyui.app.screen.ScreenManager
import everyui.component.{Component, Container, MaterialIconView, Modal, ScrollContainer, TextView}
import everyui.component.types.{FontWeight, WhiteSpace}
import everyui.font.MaterialIcon
import everyui.layout.VerticalLayout
import everyui.render.Platform
import everyui.theme.Theme
import everyui.Color
import reactify.*

/** How long a dialog takes to close as a screen opens in its place, as a TV app's Resume question does as its player
  * opens (`DialogStartScreen`, then `DialogPlayerScreen`): a menu of about 25 components (a title, a picture and two
  * rows, each an icon and two lines of words in a ring that lights with the focus) over a screen of a picture. OK on
  * the screen's button asks; OK on a row closes the menu and opens the player, as `open=0` closes it alone. Each close
  * is logged, with every frame of 50 ms or more:
  *
  *   [ui] dialogclose closed in 4ms
  *   [dispose] dialog Container taken apart: 24 components in 6 ms
  *   [ui] dialogclose frame 80ms (update 12ms)
  *
  * `auto=<closes>` asks, closes and goes back that many times by itself, a few seconds apart. */
object DialogCloseDemo extends ScreenManager {
  lazy val start: DialogStartScreen = new DialogStartScreen

  def run(): Unit = {
    Theme.family @= "Open Sans"
    run(start)
    val closes = Platform.launchArg("auto").toIntOption.getOrElse(0)
    if (closes > 0) Platform.after(3.0, () => auto(closes))
  }

  private def auto(left: Int): Unit = {
    ask()
    Platform.after(1.5, () => {
      choose()
      Platform.after(3.0, () => {
        if (Platform.launchArg("open") != "0") back()
        if (left > 1) Platform.after(2.0, () => auto(left - 1))
      })
    })
  }

  /** The menu asked, as a title with a place to resume from asks it. */
  def ask(): Unit = {
    val chosen: () => Unit = () => choose()
    val rows = List(row(MaterialIcon.PlayArrow, "Resume from 1:04:05", "Episode 3 · 52 min left", chosen),
      row(MaterialIcon.Replay, "Start over", "From the beginning", chosen))
    val stack = new Container {
      layout @= Some(VerticalLayout(0.0))
      size.width @= 360.0
      children ++= rows.map(_._1)
    }
    val text = new TextView {
      content @= "Resume S1 E3 · A Title of Some Length?"
      font.size @= 13.0
      font.weight @= FontWeight.SemiBold
      color := Theme.muted()
      position.left @= 20.0
      position.top @= 18.0
    }
    val heading = new Container {
      size.width @= 360.0
      size.height := text.size.height() + 26.0
      children += text
    }
    val picture = new everyui.component.ImageView {
      sizedByPicture @= false
      size.width @= 96.0
      size.height @= 144.0
      position.left @= 132.0
      position.top := heading.size.height()
      radius @= 8.0
    }
    everyui.image.Image("/images/poster.jpg").map(loaded => picture.image @= loaded).startUnit()
    val scroll = new ScrollContainer {
      size.width @= 360.0
      position.top := picture.position.top() + 150.0
      content.size.height := stack.size.height()
      content.children += stack
      size.height := stack.size.height()
    }
    val panel = new Container {
      size.width @= 360.0
      backgroundColor := Theme.surface()
      radius @= 16.0
      size.height := scroll.position.top() + scroll.size.height() + 6.0
      children ++= List(heading, picture, scroll)
    }
    Modal.show(panel, rows.head._2)
  }

  /** A row of the menu that does `pressed` when chosen, and what has the focus in it. */
  private def row(icon: MaterialIcon, label: String, under: String, pressed: () => Unit): (Container, Component) = {
    val words = everyui.component.Column(2.0)(
      new TextView {
        content @= label
        font.size @= 15.0
        color := Theme.text()
      },
      new TextView {
        content @= under
        whiteSpace @= WhiteSpace.Normal
        size.width @= 260.0
        font.size @= 12.5
        color := Theme.muted()
      })
    val line = everyui.component.Row(12.0)(new MaterialIconView(icon, 18.0) {
      color := Theme.text()
    }, words)
    val tile = new Container {
      focusable @= true
      size.width @= 344.0
      size.height := line.size.height() + 24.0
      position.left @= 8.0
      radius @= 10.0
      backgroundColor := (if (focusShown()) Theme.controlLit() else Color.Clear)
      line.position.left @= 12.0
      line.position.top @= 12.0
      event.click.attach { e =>
        e.consume()
        pressed()
      }
    }
    val ring = new Container {
      size.width := tile.size.width()
      size.height := tile.size.height()
      radius @= 10.0
      borderWidth @= 2.5
      borderColor := (if (tile.focusShown()) Theme.accent() else Color.Clear)
    }
    tile.children ++= List(line, ring)
    val boxed = new Container {
      size.width @= 360.0
      size.height := tile.size.height()
      children += tile
    }
    (boxed, tile)
  }

  /** A row chosen: the menu closed and, as the choice is played, the player opened in the same turn. */
  def choose(): Unit = {
    val started = Platform.ticks()
    Modal.close()
    if (Platform.launchArg("open") != "0") show(new DialogPlayerScreen).startUnit()
    Platform.log("dialogclose closed in " + math.round(Platform.ticks() - started).toString + "ms")
  }

  override protected def frame(ms: Double, updateMs: Double): Unit =
    if (ms >= 50.0 && ms < 5000.0) Platform.log("dialogclose frame " + math.round(ms).toString + "ms (update " +
      math.round(updateMs).toString + "ms)")
}
