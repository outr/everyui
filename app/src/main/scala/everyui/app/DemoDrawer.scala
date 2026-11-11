package everyui.app

import everyui.Color
import everyui.component.{Button, Column, Component, Container}
import everyui.easing.Easing
import everyui.focus.Focus
import everyui.drive.Drive
import everyui.render.Platform
import everyui.task.*
import reactify.*

import scala.concurrent.duration.*

/** DrawerDemo's drawer: a scrim the screen's size and a rail of rows down its left, which `open` makes again a turn
  * later each time. */
class DemoDrawer(across: Double, tall: Double) extends Container {
  private val scrim = new Container {
    size.width @= across
    size.height @= tall
    backgroundColor @= Color.Black.withAlpha(0.55)
  }
  private val rail = Column(8.0)()
  private var release: () => Unit = () => ()
  private var was: Option[Component] = None
  private var opens = 0

  size.width @= across
  size.height @= tall
  visible @= false
  rail.position.top @= 16.0
  children ++= List(scrim, rail)
  scrim.event.click.attach { e =>
    e.consume()
    close()
  }

  def open(): Unit = if (!visible()) {
    opens += 1
    Platform.log(s"drawer open $opens")
    was = Focus.focused()
    visible @= true
    // its rows made again a turn later, as an app sets its destinations from what it has loaded
    Platform.later(() => {
      rail.children @= Vector("Home", "Calendar", "Settings").map(n => new Button(n, () => chose(n)): Component)
      rail.children().headOption.foreach(_.focus())
      Platform.after(0.5, () => rail.children().foreach(r => Platform.log(s"drawer row ${Drive.nameOf(r)} at " +
        s"${r.absoluteLeft},${r.absoluteTop} ${r.size.width()}x${r.size.height()} shown=${r.notHidden}")))
    })
    rail.position.left @= -240.0
    rail.position.left.to(16.0).in(180.millis).easing(Easing.cubicOut).start()
    Focus.scope @= Some(this)
    release = Focus.onBack { _ =>
      close()
      true
    }
  }

  private def chose(row: String): Unit = {
    Platform.log(s"drawer chose $row")
    close()
  }

  def close(): Unit = if (visible()) {
    release()
    release = () => ()
    Focus.scope @= None
    visible @= false
    was.filter(Focus.showing).foreach(_.focus())
  }
}
