package everyui.app

import everyui.Color
import everyui.component.{Container, ScrollContainer, TextView}
import everyui.layout.VerticalLayout
import everyui.render.Platform
import everyui.theme.{Palette, Theme}
import everyui.ui

/** A rail of tiles down the window's left edge, 44 wide, as an app's far-left rail is: its scroll draws no bar and
  * keeps no room for one (`ScrollContainer.bar`), and its ends fade where there is more beyond them (`edgeFade`, 24);
  * beside it a list with its bar, fading too, over a striped ground so the fade is seen to show what is behind it.
  * `theme=light`, and `at=<pixels>` scrolls both that far as they are made. Logs the rail's offset and fades as they
  * change ("[ui] rail 120 fades 24,24"). */
object RailDemo {
  private val hues = Vector(210.0, 160.0, 35.0, 280.0, 340.0, 190.0, 120.0, 20.0)

  private def tile(i: Int, width: Double, height: Double): Container = new Container {
    size.width @= width
    size.height @= height
    backgroundColor @= Color.fromHSB(hues(i % hues.size) / 360.0, 0.55, 0.85)
    radius @= 8.0
    children += new TextView {
      content @= (i + 1).toString
      font.size @= 14.0
      color @= Color.White
      position.left @= 10.0
      position.top @= 10.0
    }
  }

  def run(): Unit = {
    if (Platform.launchArg("theme") == "light") Theme.palette @= Palette.light
    ui.backgroundColor := Theme.background()
    // stripes behind both, which the fade lets show through
    val ground = new Container {
      size.width := ui.size.width()
      size.height := ui.size.height()
      children ++= (0 until 40).map(i => new Container {
        size.width := ui.size.width()
        size.height @= 12.0
        position.top @= i * 24.0
        backgroundColor := Theme.accent().withAlpha(0.25)
      })
    }
    val rail = new ScrollContainer {
      size.width @= 44.0
      size.height := ui.size.height()
      content.layout @= Some(VerticalLayout(4.0))
    }
    rail.bar @= false
    rail.edgeFade @= 24.0
    rail.content.children ++= (0 until 30).map(i => tile(i, 44.0, 44.0))
    val list = new ScrollContainer {
      size.width @= 260.0
      size.height := math.min(420.0, ui.size.height() - 40.0)
      position.left @= 80.0
      position.top @= 20.0
      content.layout @= Some(VerticalLayout(6.0))
    }
    list.edgeFade @= 32.0
    list.content.children ++= (0 until 20).map(i => tile(i, 240.0, 56.0))
    ui.children ++= List(ground, rail, list)
    rail.fades.attach(f => Platform.log("rail " + rail.offset().round.toString + " fades " + f._1.round.toString + "," +
      f._2.round.toString))
    Option(Platform.launchArg("at")).filter(_.nonEmpty).foreach { at =>
      Platform.after(0.5, () => {
        rail.scrollTo(at.toDouble)
        list.scrollTo(at.toDouble)
      })
    }
    Platform.log("rail ok")
  }
}
