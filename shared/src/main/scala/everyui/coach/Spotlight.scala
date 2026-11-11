package everyui.coach

import everyui.component.{Component, Container}
import everyui.{Color, PlatformKind, ui}
import everyui.draw.{Drawing, Figure, Path}
import everyui.easing.Easing
import everyui.event.PointerKind
import everyui.focus.Focus
import everyui.render.Platform
import everyui.task.*
import everyui.theme.Theme
import reactify.*

import scala.concurrent.duration.*

/** A coach mark's light: the whole screen dimmed but for a hole over `targets` (one hole, around them all, `margin`
  * wider), which follows them as they move, scroll and glide, its corners rounded `radius` and a `ring` that wide
  * round it if wanted. Under the dim the pointer reaches nothing — a click, a tap, a hover, the wheel — and in the hole
  * it reaches what is there as ever; what is put over the screen after it (the note beside the hole, a dialog) is
  * above the dim and pointed at as ever.
  *
  * The hole is four bands of dim around it, so it costs a handful of nodes on a Roku, and moving it is moving them;
  * its corners are rounded by four small drawings where the platform draws curves (not a Roku or an Apple TV, whose
  * hole is square, the ring rounded). The dim is the theme's (`Theme.backdrop`) and the ring its accent, unless set.
  *
  * {{{
  *   val light = Spotlight.on(List(card), radius = 14.0, ring = 3.0)
  *   …
  *   light.release()
  * }}} */
class Spotlight(initial: List[Component]) extends Container {
  override def kind: String = "Spotlight"

  /** What the hole is over: it is around them all. */
  val targets: Var[List[Component]] = own(Var(initial))
  /** Room left round the targets, in pixels. */
  val margin: Var[Double] = own(Var(8.0))
  /** The hole's corners' radius. */
  val corner: Var[Double] = own(Var(0.0))
  /** How wide the ring round the hole is: none at 0. */
  val ring: Var[Double] = own(Var(0.0))
  val ringColor: Var[Color] = own(Var(Theme.accent()))
  /** The dim. */
  val shade: Var[Color] = own(Var(Theme.backdrop()))

  ringColor := Theme.accent()
  shade := Theme.backdrop()
  size.width := ui.size.width()
  size.height := ui.size.height()

  // the hole round the targets on the screen: empty (all dim) with none showing
  val holeLeft: Val[Double] = own(Val(edge(_.absoluteLeft, least = true)))
  val holeTop: Val[Double] = own(Val(edge(_.absoluteTop, least = true)))
  val holeRight: Val[Double] = own(Val(edge(t => t.absoluteLeft + t.size.width(), least = false)))
  val holeBottom: Val[Double] = own(Val(edge(t => t.absoluteTop + t.size.height(), least = false)))

  // the furthest of `f` over the targets on the screen, the least or the greatest, `margin` beyond it
  private def edge(f: Component => Double, least: Boolean): Double = {
    val all = targets().filter(Focus.showing).map(f)
    if (all.isEmpty) 0.0
    else if (least) all.foldLeft(all.head)((a, b) => math.min(a, b)) - margin()
    else all.foldLeft(all.head)((a, b) => math.max(a, b)) + margin()
  }
  // how far the corners round: no more than half the hole's shorter side
  private val round: Val[Double] =
    own(Val(math.max(0.0, math.min(corner(), math.min(holeRight() - holeLeft(), holeBottom() - holeTop()) / 2.0))))

  private def band(): Container = new Container {
    backgroundColor := shade()
    Spotlight.blocks(this)
  }
  private val above = band()
  above.size.width := size.width()
  above.size.height := math.max(0.0, holeTop())
  private val below = band()
  below.position.top := holeBottom()
  below.size.width := size.width()
  below.size.height := math.max(0.0, size.height() - holeBottom())
  private val before = band()
  before.position.top := holeTop()
  before.size.width := math.max(0.0, holeLeft())
  before.size.height := math.max(0.0, holeBottom() - holeTop())
  private val after = band()
  after.position.left := holeRight()
  after.position.top := holeTop()
  after.size.width := math.max(0.0, size.width() - holeRight())
  after.size.height := math.max(0.0, holeBottom() - holeTop())
  children ++= List(above, below, before, after)

  // each corner of the hole dimmed outside its rounding: (where the corner's box sits across and down, as -1 for the
  // hole's left or top and 1 for its right or bottom)
  if (Spotlight.curves) List((-1, -1), (1, -1), (1, 1), (-1, 1)).foreach { case (across, down) =>
    val piece = new Drawing {
      size.width := round()
      size.height := round()
      position.left := (if (across < 0) holeLeft() else holeRight() - round())
      position.top := (if (down < 0) holeTop() else holeBottom() - round())
      figures := (if (round() <= 0.0) Nil else List(Figure(Spotlight.cornerPath(round(), across, down), Some(shade()))))
    }
    Spotlight.blocks(piece)
    children += piece
  }

  private val circle = new Container {
    position.left := holeLeft() - ring()
    position.top := holeTop() - ring()
    size.width := holeRight() - holeLeft() + ring() * 2.0
    size.height := holeBottom() - holeTop() + ring() * 2.0
    radius := (if (corner() > 0.0) round() + ring() else 0.0)
    borderWidth := ring()
    borderColor := ringColor()
    visible := ring() > 0.0
  }
  everyui.render.Gestures.passThrough(circle.node)
  children += circle
  // the pointer through the hole, where no band is
  everyui.render.Gestures.passBetween(node)

  /** Whether a pointer's `kind` at `c`, at `x`, `y`, is heard: not under the dim, unless it is the pointer leaving or
    * lifting, or the wheel (whose `x` and `y` are how far it turned; the dim takes it where it is under the pointer). */
  private[everyui] def lets(c: Component, kind: PointerKind, x: Double, y: Double): Boolean =
    kind == PointerKind.Leave || kind == PointerKind.Up || kind == PointerKind.Wheel || !under(c) ||
      (x >= holeLeft() && x < holeRight() && y >= holeTop() && y < holeBottom())

  // whether `c` is on a part of the screen put up before this, and so under the dim
  private def under(c: Component): Boolean = {
    val all = ui.children()
    val mine = all.indexWhere(_ eq this)
    val at = all.indexWhere(_ eq Focus.layer(c))
    mine >= 0 && at >= 0 && at < mine
  }

  private var fading: Option[TaskInstance] = None

  /** Over the screen, fading in. */
  def show(): Spotlight = {
    opacity @= 0.0
    ui.children += this
    Spotlight.up = this :: Spotlight.up
    fading = Some(opacity.to(1.0).in(Spotlight.fade).easing(Easing.cubicInOut).start())
    this
  }

  /** Taken down: the pointer reaches everything again at once, and the dim fades out, and is taken apart. */
  def release(): Unit = if (Spotlight.up.exists(_ eq this)) {
    Spotlight.up = Spotlight.up.filterNot(_ eq this)
    fading.foreach(_.cancel())
    fading = Some(sequential(opacity.to(0.0).in(Spotlight.fade).easing(Easing.cubicInOut), synchronous {
      ui.children -= this
      dispose()
    }).start())
  }
}

object Spotlight {
  private[coach] var up: List[Spotlight] = Nil
  private[coach] val fade: FiniteDuration = 250.millis

  /** A spotlight on `targets`, put up over the screen as it is, fading in. */
  def on(targets: List[Component], radius: Double = 0.0, ring: Double = 0.0, margin: Double = 8.0): Spotlight = {
    val light = new Spotlight(targets)
    light.corner @= radius
    light.ring @= ring
    light.margin @= margin
    light.show()
  }

  /** Whether a pointer's `kind` at `c`, at `x`, `y`, is heard: not under a spotlight's dim, outside its hole. */
  def lets(c: Component, kind: PointerKind, x: Double, y: Double): Boolean = up.forall(_.lets(c, kind, x, y))

  /** Whether the platform rounds the hole: it draws curves. */
  def curves: Boolean =
    Platform.kind != PlatformKind.Roku && Platform.kind != PlatformKind.TvOS && Platform.kind != PlatformKind.IOS

  // the platform's own handling of the pointer stops at `c`: a press, a click, a long press, the wheel and a hover over
  // it are its own, and reach nothing under it (a finger on Android, which passes what a view does not take)
  private def blocks(c: Component): Unit = {
    // the plain arrow over it, which a platform would otherwise make a hand for something that hears clicks
    c.cursor @= "default"
    List(PointerKind.Down, PointerKind.Click, PointerKind.Menu, PointerKind.Wheel, PointerKind.Enter)
      .foreach(k => c.node.onPointer(k, (_, _) => ()))
    c.hearsPointer = true
  }

  /** A corner's box, `r` square, dimmed outside the hole's rounding: the box at the hole's left (`across` -1) or right
    * (1), its top (`down` -1) or bottom (1). */
  def cornerPath(r: Double, across: Int, down: Int): Path = {
    // the corner of the box that is the hole's own corner, and the centre of the rounding
    val x = if (across < 0) 0.0 else r
    val y = if (down < 0) 0.0 else r
    val cx = if (across < 0) r else 0.0
    val cy = if (down < 0) r else 0.0
    // the quarter circle facing the corner, from one edge of the box to the other
    val from = if (across < 0 && down < 0) math.Pi else if (down < 0) math.Pi * 1.5 else if (across > 0) 0.0
      else math.Pi / 2.0
    Path.empty.moveTo(x, y).arc(cx, cy, r, from, from + math.Pi / 2.0).close
  }
}
