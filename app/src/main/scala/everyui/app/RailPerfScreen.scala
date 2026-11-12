package everyui.app

import everyui.Color
import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.component.{Component, Container, ProgressBar, ScrollContainer, TextView, VideoView}
import everyui.component.types.{FontWeight, WhiteSpace}
import everyui.layout.HorizontalLayout
import everyui.net.*
import everyui.render.Platform
import reactify.*

/** The rail demo's one screen (`RailPerfDemo`). */
class RailPerfScreen extends UIScreen {
  override def manager: ScreenManager = RailPerfDemo
  override def title: String = "Rail"
  override def path: URLPath = path"/railperf"

  private val count: Int = Platform.launchArg("n").toIntOption.getOrElse(20)
  private val railCount: Int = math.max(1, Platform.launchArg("rails").toIntOption.getOrElse(1))
  private val videoUrl: String = Platform.launchArg("video")
  /** The card the focus is on, which the hero tells of. */
  private val chosen: Var[Int] = Var(0)

  private val name = new TextView {
    content := "Title number " + (chosen() + 1).toString
    font.size @= 28.0
    font.weight @= FontWeight.Bold
    color @= Color.White
    position.left @= 48.0
    position.top @= 40.0
  }
  private val about = new TextView {
    content := (2001 + chosen() % 20).toString + "  ·  " + (90 + chosen() * 7 % 60).toString + " min  ·  Drama"
    font.size @= 14.0
    color @= Color.White.withAlpha(0.7)
    position.left @= 48.0
    position.top @= 84.0
  }
  private val synopsis = new TextView {
    content := RailPerfScreen.synopses(chosen() % RailPerfScreen.synopses.length)
    font.size @= 14.0
    font.lineHeight @= 1.3
    color @= Color.White.withAlpha(0.85)
    whiteSpace @= WhiteSpace.Normal
    size.width @= 520.0
    maxLines @= 3
    position.left @= 48.0
    position.top @= 110.0
  }
  private val progress = new ProgressBar(160.0, 5.0) {
    fraction := (chosen() * 37 % 100) / 100.0
    position.left @= 48.0
    position.top @= 190.0
  }

  // the rails, one above the next, scrolling up and down under the hero as a Home's do
  private val rows = new ScrollContainer {
    position.left @= 32.0
    position.top @= 230.0
    size.width @= 896.0
    size.height @= 300.0
    revealAlign @= Some(0.0)
    revealGlide @= 0.22
    content.layout @= Some(everyui.layout.VerticalLayout(8.0))
  }
  private var rail: ScrollContainer = makeRail(0)
  private var video: Option[VideoView] = None

  private def makeRail(row: Int): ScrollContainer = {
    val r = new ScrollContainer(horizontal = true) {
      size.width @= 896.0
      size.height @= 210.0
      revealAlign @= Some(0.5)
      revealGlide @= 0.22
      content.layout @= Some(HorizontalLayout(16.0))
      content.padding @= 12.0
    }
    r.content.children @= Vector.tabulate(count)(i => card(row * count + i))
    r
  }

  private def card(i: Int): Container = new Container {
    size.width @= 120.0
    size.height @= 180.0
    radius @= 10.0
    backgroundColor @= Color.fromHex(if (i % 2 == 0) "#2a3d5c" else "#4a2a4c")
    focusable @= true
    children += new TextView {
      content @= (i + 1).toString
      font.size @= 18.0
      color @= Color.White
      position.left @= 12.0
      position.top @= 10.0
    }
    // the ring over the card, made the first time the focus lands on it, as a television's poster card has one
    private var ringed = false
    focused.attach(on => if (on) {
      if (!ringed) {
        ringed = true
        children += new Container {
          size.width @= 120.0
          size.height @= 180.0
          radius @= 10.0
          borderWidth := (if (focused()) 2.5 else 0.0)
          borderColor @= Color.fromHex("#ffb000")
        }
      }
      Platform.log("railperf focus " + i.toString)
      // the hero follows on a turn of its own, as a television's Home does
      Platform.later(() => chosen @= i)
    })
    event.click.attach { e =>
      e.consume()
      ok()
    }
  }

  /** OK: the video started or stopped, with `video=`; without, the rail taken apart and built again. */
  private def ok(): Unit =
    if (videoUrl.nonEmpty) video match {
      case Some(v) =>
        Platform.log("railperf video stop")
        container.children -= v
        v.dispose()
        video = None
      case None =>
        val start = Platform.ticks()
        val v = new VideoView
        v.size.width @= container.size.width()
        v.size.height @= container.size.height()
        v.muted @= true
        v.loop @= true
        v.source @= videoUrl
        container.children @= v +: container.children()
        video = Some(v)
        Platform.log("railperf video started in " + math.round(Platform.ticks() - start).toString + " ms")
    }
    else {
      val old = rail
      val start = Platform.ticks()
      rail = makeRail(0)
      rows.content.children @= rows.content.children().map(c => if (c eq old) rail else c)
      Component.disposeGradually(List(old), () => Platform.collect(), "rail")
      rail.content.children().headOption.foreach(_.focus())
      Platform.log("railperf rail rebuilt in " + math.round(Platform.ticks() - start).toString + " ms")
    }

  override def defaultFocus(): Component = rail.content.children().head

  override def createUI(): Task[Unit] = Task {
    container.backgroundColor @= Color.fromHex("#101418")
    rows.content.children @= rail +: Vector.tabulate(railCount - 1)(k => makeRail(k + 1))
    container.children ++= List(name, about, synopsis, progress, rows)
  }
}

object RailPerfScreen {
  val synopses: Vector[String] = Vector(
    "A retired lighthouse keeper finds a message in a bottle that leads him across three countries and back into a " +
      "life he thought he had left behind for good.",
    "Two rival bakers are forced to share a kitchen for one summer, and discover that the recipe they have both been " +
      "chasing was written by the same woman.",
    "When the last train out of the valley is cancelled, the passengers left on the platform have one night to " +
      "decide who they are willing to become.",
    "A cartographer hired to map a city that keeps changing begins to suspect that the city is drawing her instead.")
}
