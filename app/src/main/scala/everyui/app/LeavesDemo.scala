package everyui.app

import everyui.component.{Container, ImageView, TextView}
import everyui.image.Image
import everyui.motion.{Keyframe, Track}
import everyui.Color
import everyui.render.Platform
import everyui.ui
import reactify.*

/** Leaves falling over the screen as NaboTV's seasonal effects make them: 28 pictures, each with an endless position
  * track that crosses the screen and wraps out past the side (keys a twentieth of a percent of the cycle apart) and
  * back in at the top, and a turn, over 17 to 31 s from a random point of its cycle. Nothing but each motion's repeat
  * brings a leaf back, so a platform that stops a motion as its own movement takes its node out of sight loses the leaf
  * for good. `short=1` makes the cycles 4 to 6 s. The words at the top tell the seconds, laid out again each second as
  * an app's clock is (`still=1` for none, `ticks=<seconds>` for only the first so many, as a screen settles once it
  * has loaded).
  *
  * Logs each second how many leaves the cycles put on the screen, and how long ago each last wrapped; what the platform
  * draws is read from screenshots, and on Android from the idle audit's running motions (`--es idleAudit 1`). */
object LeavesDemo {
  private val Gap = 0.0005

  private case class Leaf(seconds: Double, start: Double, size: Double)

  def run(): Unit = {
    val short = Platform.launchArg("short") == "1"
    val ticking = Platform.launchArg("still") != "1"
    val tickFor = Platform.launchArg("ticks").toDoubleOption.getOrElse(Double.MaxValue)
    val w = Platform.screenWidth()
    val h = Platform.screenHeight()
    val back = new Container {
      size.width @= w
      size.height @= h
      backgroundColor @= Color.fromHex("000000")
    }
    ui.children += back
    val leaves = (0 until 28).toList.map { _ =>
      Leaf(if (short) 4.0 + Platform.random() * 2.0 else 17.0 + Platform.random() * 14.0, Platform.random(),
        10.0 + Platform.random() * 12.0)
    }
    leaves.foreach { leaf =>
      val image = new ImageView
      image.image @= new Image(Platform.resolvePath("/images/icon.png"), leaf.size, leaf.size)
      image.size.width @= leaf.size
      image.size.height @= leaf.size
      image.position.left @= -leaf.size / 2.0
      image.position.top @= -leaf.size / 2.0
      val vx = 0.01 + Platform.random() * 0.02
      image.motion @= List(crossing(w, h, leaf, Platform.random() - vx * leaf.seconds / 2.0, vx),
        spinning(Platform.random() * 6.0, (Platform.random() - 0.5) * 3.0, leaf))
      back.children += image
    }
    val title = "Leaves: 28, " + (if (short) "4 to 6 s" else "17 to 31 s") + " cycles"
    val words = new TextView
    words.content @= title
    words.position.left @= 24.0
    words.position.top @= 24.0
    ui.children += words
    val began = Platform.ticks()
    def tell(): Unit = {
      val t = (Platform.ticks() - began) / 1000.0
      val along = leaves.map(l => l.start + t / l.seconds)
      val seen = along.count { a =>
        val u = a - math.floor(a)
        u > 0.05 / 1.1 && u < 1.05 / 1.1
      }
      // how long ago each crossed the wrap (a - floor(a) of a cycle ago), or "-" for one not wrapped yet
      val wraps = leaves.zip(along).map { (l, a) =>
        if (a < 1.0) "-" else (math.round((a - math.floor(a)) * l.seconds * 10.0) / 10.0).toString
      }
      Platform.log("[leaves] " + math.round(t).toString + "s: " + seen.toString +
        " of 28 on screen by their cycles; last wrapped (s ago): " + wraps.mkString(" "))
      // a clock, as an app's screen has, laid out again each second
      if (ticking && t <= tickFor) words.content @= title + ", " + math.round(t).toString + " s"
      Platform.after(1.0, () => tell())
    }
    Platform.after(1.0, () => tell())
  }

  /** From the top (5% above it) to the bottom (5% below), drifting `vx` screens a second, from `start` of the way
    * through; at the wrap out past the left side along the bottom and in along the top, as NaboTV's do. */
  private def crossing(w: Double, h: Double, leaf: Leaf, x0: Double, vx: Double): Track = {
    val steps = 8
    val start = math.max(Gap * 4.0, math.min(1.0 - Gap * 4.0, leaf.start))
    val wrap = 1.0 - start
    def at(f: Double, u: Double): Keyframe =
      Keyframe(f, (x0 + vx * u * leaf.seconds + 0.015 * math.sin(u * 2.0 * math.Pi)) * w, (-0.05 + 1.1 * u) * h)
    val before = (0 until steps).toList.map(_.toDouble / steps).filter(_ < wrap - Gap).map(f => at(f, start + f))
    val after = (1 to steps).toList.map(_.toDouble / steps).filter(_ > wrap + Gap).map(f => at(f, start + f - 1.0))
    Track(Track.Position, leaf.seconds, before ++ List(at(wrap - Gap / 2.0, 1.0),
      Keyframe(wrap - Gap / 6.0, -w, 1.05 * h), Keyframe(wrap + Gap / 6.0, -w, -0.05 * h),
      at(wrap + Gap / 2.0, 0.0)) ++ after)
  }

  /** A steady turn of `spin` radians a second, turning back at the wrap, out of sight. */
  private def spinning(a0: Double, spin: Double, leaf: Leaf): Track = {
    val start = math.max(Gap * 4.0, math.min(1.0 - Gap * 4.0, leaf.start))
    val wrap = 1.0 - start
    Track(Track.Rotation, leaf.seconds, List(Keyframe(0.0, a0 + spin * start * leaf.seconds),
      Keyframe(wrap - Gap / 2.0, a0 + spin * leaf.seconds), Keyframe(wrap + Gap / 2.0, a0),
      Keyframe(1.0, a0 + spin * start * leaf.seconds)))
  }
}
