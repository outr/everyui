package everyui.app

import everyui.{Color, Shadow}
import everyui.app.screen.{ScreenManager, ScrollScreen}
import everyui.async.Task
import everyui.component.{Button, Component, Container, ScrollContainer, TextView}
import everyui.focus.Focus
import everyui.layout.{FlowLayout, HorizontalLayout}
import everyui.motion.{Keyframe, Track}
import everyui.net.*
import everyui.perf.{Perf, PerfSession, Quality, QualityLevel, QualityPolicy}
import everyui.render.Platform
import reactify.*

/** The quality demo's one screen: the level and the live measures, a button for each level, and what each level draws
  * differently. */
class QualityScreen extends ScrollScreen {
  override def manager: ScreenManager = QualityDemo
  override def title: String = "Quality"
  override def path: URLPath = path"/quality"

  private val stats: Var[String] = Var("")
  private val levelLine = TextView.bound("Quality: " + Quality.level().toString, 22.0)
  private val statsLine = TextView.bound(stats(), 13.0)
  private val deviceLine = TextView("", 13.0)

  private def force(level: QualityLevel): Button = new Button(level.toString, () => {
    // a new session's level, as a launch would give it: the session's own step down is put aside
    val policy = Quality.policy
    Quality.forget()
    Quality.policy = policy
    Quality.start(Some(level))
  })
  private val buttons = Vector(force(QualityLevel.Full), force(QualityLevel.Reduced), force(QualityLevel.Minimal))
  private val row = new Container {
    layout @= Some(HorizontalLayout(12.0))
    children := buttons
  }

  private val rail = new ScrollContainer(horizontal = true) {
    size.width @= 900.0
    size.height @= 170.0
    revealGlide @= 0.4
    content.layout @= Some(HorizontalLayout(24.0))
  }
  private val cards = Vector.tabulate(24) { i =>
    new Container {
      size.width @= 180.0
      size.height @= 120.0
      position.top @= 20.0
      radius @= 12.0
      backgroundColor @= (if (i % 2 == 0) Color.fromHex("#3a5a9a") else Color.fromHex("#8a3a6a"))
      shadow @= Some(Shadow(0.0, 8.0, 20.0, Color.Black.withAlpha(0.6)))
      focusable @= true
      children += new TextView {
        content @= "Card " + (i + 1).toString
        font.size @= 20.0
        color @= Color.White
        position.left @= 16.0
        position.top @= 16.0
        textShadow @= Some(Shadow(0.0, 2.0, 4.0, Color.Black))
      }
    }
  }

  private val spinner = new Container {
    size.width @= 60.0
    size.height @= 60.0
    backgroundColor @= Color.fromHex("#e0a030")
    motion @= List(Track(Track.Rotation, 2.0, List(Keyframe(0.0, 0.0), Keyframe(1.0, 2.0 * math.Pi))))
  }
  private val blurred = new Container {
    size.width @= 120.0
    size.height @= 60.0
    backgroundColor @= Color.fromHex("#30a0a0")
    blur @= 6.0
  }
  private val effects = new Container {
    layout @= Some(HorizontalLayout(40.0))
    children ++= List(spinner, blurred)
  }

  // `tiles=<n>` small boxes recoloured on each move of the focus: work for a slow device, or a throttled one, to feel
  private val tiles = new Container {
    size.width @= 900.0
    size.height := contentHeight
    layout @= Some(FlowLayout(4.0, 4.0))
  }

  override def defaultFocus(): Component = cards.head

  override def createUI(): Task[Unit] = Task {
    val window = Platform.launchArg("window")
    val slow = Platform.launchArg("slow")
    Quality.policy = QualityPolicy(
      windowSeconds = if (window.isEmpty) 60.0 else window.toDouble,
      slowWindows = if (slow.isEmpty) 3 else slow.toInt,
      // a short window has fewer frames to judge by
      minFrames = if (window.isEmpty) 30 else 10,
      summarySeconds = if (window.isEmpty) 300.0 else window.toDouble * 2.0)
    val start = Platform.launchArg("level")
    Quality.start(QualityLevel.values.find(_.toString == start))
    deviceLine.content @= "Device: " + Perf.device.line
    rail.content.children := cards
    val count = Platform.launchArg("tiles")
    if (count.nonEmpty) {
      tiles.children := Vector.tabulate(count.toInt)(_ => new Container {
        size.width @= 26.0
        size.height @= 26.0
      })
      Focus.focused.attach { f =>
        val at = cards.indexWhere(c => f.exists(_ eq c))
        tiles.children().zipWithIndex.foreach { case (t, i) =>
          t.backgroundColor @= Color.fromHSB(((i + at * 7) % 30).toDouble / 30.0, 0.6, 0.8)
          t.shadow @= Some(Shadow(0.0, 1.0, 3.0, Color.Black.withAlpha(0.5)))
        }
      }
    }
    body.children ++= List(levelLine, statsLine, deviceLine, row, rail, effects, tiles)
    Perf.windows.attach(w => Platform.log("quality window " + w.line + " wants " + Quality.policy.wants(w).toString))
    Perf.summaries.attach(s => Platform.log("quality summary " + QualityScreen.line(s)))
    Quality.level.attach(l => Platform.log("quality level " + l.toString))
    refresh()
  }

  // the window under way, once a second
  private def refresh(): Unit = {
    stats @= Perf.current().line
    Platform.after(1.0, () => refresh())
  }
}

object QualityScreen {
  /** A summary's fields in a line. */
  def line(s: PerfSession): String =
    s.levelRun.getOrElse("-") + " keys p50/p95 " + s.keyLatencyMsP50.map(_.toString).getOrElse("-") + "/" +
      s.keyLatencyMsP95.map(_.toString).getOrElse("-") + " drop " + s.frameDropPct.map(_.toString + "%").getOrElse("-") +
      " update p95 " + s.updateMsP95.map(_.toString).getOrElse("-") + " frame p95 " +
      s.frameMsP95.map(_.toString).getOrElse("-") + " mem " + s.memoryLevel.getOrElse("-") + " stepped " +
      s.steppedDown.map(_.toString).getOrElse("-") + " | " + s.evidence.getOrElse("")
}
