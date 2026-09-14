package everyui.component

import reactify.*
import everyui.Color
import everyui.component.feature.{MeasuredFeature, PositionFeature, SizeFeature}
import everyui.component.types.{Prop, WhiteSpace}
import everyui.event.Events
import everyui.focus.Focus
import everyui.render.RenderNode

/** youi's `Component`, with the DOM element replaced by a [[RenderNode]] — which each backend supplies — so the
  * same Props and features run over a DOM element or a SceneGraph node. */
class Component(val node: RenderNode) {
  /** For diagnostics (a tree dump): what this is, and what it contains. */
  def kind: String = "Component"
  def childComponents: Vector[Component] = Vector.empty

  lazy val measure: Trigger = Trigger()
  lazy val measured: MeasuredFeature = new MeasuredFeature(this)
  val size: SizeFeature = new SizeFeature(this)
  lazy val position: PositionFeature = new PositionFeature(this)

  lazy val color: Prop[Color] = new Prop[Color](Color.Black, c => node.setColor(c.toHex))
  lazy val backgroundColor: Prop[Color] =
    new Prop[Color](Color.Clear, c => node.setBackgroundColor(if (c.alphaInt == 0) "" else if (c.alphaInt < 255) c.toHexAlpha else c.toHex))
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
  lazy val focused: Val[Boolean] = Val(Focus.focused().exists(_ eq this))
  def focus(): Unit = Focus.focused @= Some(this)
}
