package everyui.kit

import everyui.{Color, PlatformKind}
import everyui.component.*
import everyui.motion.{Keyframe, ReduceMotion, Track}
import everyui.render.Platform
import everyui.theme.Theme
import reactify.*

/** A ring in the theme's accent around something round `around` across (an avatar), a little out from its edge, while
  * `on`: somebody at work. Half of it is lit and turns once a second over the rest, faint; the platform runs the turn
  * (`Component.motion`), so it asks the app for no frames, and stops it the moment `on` goes false or the ring is out of
  * sight. Where people ask for less motion (`ReduceMotion`), and on a Roku or an Apple TV, which cannot turn what is
  * cut to half of itself, the whole ring is lit and stands still.
  *
  * It places itself in the box of what it goes around (its parent), centred on it, so it sits outside that box by
  * `outset` on each side. */
class BusyRing(around: Double, on: => Boolean) extends Container {
  /** How thick the ring is, and the gap between it and the edge it goes around. */
  val stroke: Double = BusyRing.stroke(around)
  val outset: Double = BusyRing.outset(around)
  val diameter: Double = around + outset * 2.0

  private val shown: Val[Boolean] = own(Val(on))
  /** Whether the lit half turns now: shown, where it can turn, and not asked to stand still. */
  val turning: Val[Boolean] = own(Val(shown() && BusyRing.turns && !ReduceMotion()))

  private def circle(colour: => Color): Container = new Container {
    size.width @= diameter
    size.height @= diameter
    radius @= diameter / 2.0
    borderWidth @= stroke
    borderColor := colour
  }

  private val track = circle(if (turning()) Theme.accent().withAlpha(0.3) else Theme.accent())

  size.width @= diameter
  size.height @= diameter
  position.left @= -outset
  position.top @= -outset
  visible := shown()
  children += track
  // the lit half: the whole ring seen through the top half of the box, turned with the box
  if (BusyRing.turns) {
    val half = new Container {
      size.width @= diameter
      size.height @= diameter / 2.0
      children += circle(Theme.accent())
    }
    half.clipTo(diameter, diameter / 2.0)
    val spin = new Container {
      size.width @= diameter
      size.height @= diameter
      children += half
      visible := turning()
      motion := (if (turning()) BusyRing.turn else Nil)
    }
    children += spin
  }

  override def kind: String = "BusyRing"
}

object BusyRing {
  /** How thick the ring around something `around` across is, and how far out of its box the ring reaches: the ring and
    * the gap inside it, each as thick. */
  def stroke(around: Double): Double = math.max(2.0, math.round(around / 16.0).toDouble)
  def outset(around: Double): Double = stroke(around) * 2.0

  /** Whether the platform can turn the lit half: a Roku clips only upright, and an Apple TV runs no motion yet. */
  def turns: Boolean =
    Platform.kind != PlatformKind.Roku && Platform.kind != PlatformKind.TvOS && Platform.kind != PlatformKind.IOS

  /** Once round a second. */
  val turn: List[Track] = List(Track(Track.Rotation, 1.0, List(Keyframe(0.0, 0.0), Keyframe(1.0, 2.0 * math.Pi))))
}
