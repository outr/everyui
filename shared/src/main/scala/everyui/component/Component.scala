package everyui.component

import reactify.*
import everyui.{Color, Gradient, Shadow}
import everyui.component.feature.{MeasuredFeature, PositionFeature, SizeFeature}
import everyui.component.types.{Prop, WhiteSpace}
import everyui.event.{Events, Key}
import everyui.focus.Focus
import everyui.motion.Track
import everyui.render.RenderNode

/** youi's `Component`, with the DOM element replaced by a [[RenderNode]] — which each backend supplies — so the
  * same Props and features run over a DOM element or a SceneGraph node. */
class Component(val node: RenderNode) {
  /** For diagnostics (a tree dump): what this is, and what it contains. */
  def kind: String = "Component"
  def childComponents: Vector[Component] = Vector.empty
  /** Ask for this to be laid out again: a container asks the layout pass (`LayoutPass`); anything else has nothing
    * to lay out. */
  def requestLayout(): Unit = ()

  lazy val measure: Trigger = Trigger()
  lazy val measured: MeasuredFeature = new MeasuredFeature(this)
  val size: SizeFeature = new SizeFeature(this)
  lazy val position: PositionFeature = new PositionFeature(this)

  /** Whether the platform draws this at all. A component that is not shown keeps its place and its size: it is out of
    * sight, not out of the layout. */
  lazy val visible: Prop[Boolean] = new Prop[Boolean](true, v => node.setVisible(v))

  /** Drawn larger or smaller about its own centre, its place and its size unchanged — what a card does under the
    * focus. 1.0 is its own size. */
  lazy val scale: Prop[Double] = new Prop[Double](1.0, s => node.setScale(s))

  /** Drawn once into a picture of its own and reused while it does not change: what sits over something that moves
    * (particles) is then not drawn again every frame. Where the platform keeps no such pictures, nothing changes. */
  lazy val cached: Prop[Boolean] = new Prop[Boolean](false, c => node.setCached(c))

  /** Properties moving on their own, run by the platform (see everyui.motion.Track): setting them replaces what was
    * moving, and Nil stops it. */
  lazy val motion: Prop[List[Track]] = new Prop[List[Track]](Nil, t => node.setMotion(t))

  lazy val color: Prop[Color] = new Prop[Color](Color.Black, c => node.setColor(c.toHex))
  lazy val backgroundColor: Prop[Color] = new Prop[Color](Color.Clear, c => node.setBackgroundColor(Component.css(c)))
  /** A gradient over the whole box, on top of the background colour; a backend that draws none fills with its first
    * stop. */
  lazy val gradient: Prop[Option[Gradient]] = new Prop[Option[Gradient]](None, g => g match {
    case Some(value) => node.setGradient(value.angle, value.encoded)
    case None => node.setGradient(0.0, "")
  })
  /** A soft shadow under the box (the web and the desktop). */
  lazy val shadow: Prop[Option[Shadow]] = new Prop[Option[Shadow]](None, s => s match {
    case Some(value) => node.setShadow(value.x, value.y, value.blur, Component.css(value.color))
    case None => node.setShadow(0.0, 0.0, 0.0, "")
  })
  /** An edge `borderWidth` pixels wide in `borderColor`, drawn inside the box and following its corners, so a border
    * never changes a size. (A Roku Rectangle has no edge to draw.) */
  lazy val borderColor: Prop[Color] = new Prop[Color](Color.Clear, c => node.setBorder(borderWidth(), Component.css(c)))
  lazy val borderWidth: Prop[Double] = new Prop[Double](0.0, w => node.setBorder(w, Component.css(borderColor())))
  /** Rounded corners, in pixels (the Roku's Rectangle has none). */
  lazy val radius: Prop[Double] = new Prop[Double](0.0, r => node.setRadius(r))
  /** In turns, as youi has it (1.0 is a full revolution). */
  lazy val rotation: Prop[Double] = new Prop[Double](0.0, turns => node.setRotation(turns * 2.0 * math.Pi))
  lazy val whiteSpace: Prop[WhiteSpace] =
    new Prop[WhiteSpace](WhiteSpace.NoWrap, ws => node.setWhiteSpace(ws.value), () => measure.trigger())

  lazy val effectiveWidth: Val[Double] = Val(size.width())
  lazy val effectiveHeight: Val[Double] = Val(size.height())

  /** The container this is in, kept by ContainerFeature. */
  val parent: Var[Option[Component]] = Var(None)
  /** The position in the render root: the parents' positions added up. */
  def absoluteLeft: Double = position.left() + parent().map(_.absoluteLeft).getOrElse(0.0)
  def absoluteTop: Double = position.top() + parent().map(_.absoluteTop).getOrElse(0.0)

  /** What a pointer resting here says; nothing where there is no pointer. */
  lazy val tooltip: Prop[String] = new Prop[String]("", t => node.setTooltip(t))

  lazy val event: Events = new Events(this)
  /** Whether the focus can land here: by a click, `focus()`, or the arrows from a neighbour (everyui.focus.Focus). */
  lazy val focusable: Var[Boolean] = Var(false)
  /** Whether the focus is here: set by the focus layer as it moves (`Focus.listen`). */
  lazy val focused: Val[Boolean] = focusedNow
  private[everyui] lazy val focusedNow: Var[Boolean] = {
    val here = Var(false)
    here @= Focus.focused.get.exists(_ eq this) // read once, untracked: it follows the focus layer, not the focus
    here
  }
  def focus(): Unit = Focus.focused @= Some(this)

  /** Whether this handles `key` itself while focused, so the focus layer leaves it be: a text field keeps Enter and
    * the arrows along its line. */
  def keepsKey(key: Key): Boolean = false
}

object Component {
  /** A colour as the seam takes it: `"#rrggbb"`, `"#rrggbbaa"` when translucent, or `""` for none. */
  def css(c: Color): String = if (c.alphaInt == 0) "" else if (c.alphaInt < 255) c.toHexAlpha else c.toHex
}
