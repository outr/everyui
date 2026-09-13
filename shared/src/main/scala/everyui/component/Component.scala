package everyui.component

import reactify.*
import everyui.Color
import everyui.component.feature.{MeasuredFeature, PositionFeature, SizeFeature}
import everyui.component.types.{Prop, WhiteSpace}
import everyui.render.RenderNode

/** youi's `Component`, with the DOM element replaced by a [[RenderNode]] — which each backend supplies — so the
  * same Props and features run over a DOM element or a SceneGraph node. */
class Component(val node: RenderNode):
  /** For diagnostics (a tree dump): what this is, and what it contains. */
  def kind: String = "Component"
  def childComponents: Vector[Component] = Vector.empty

  lazy val measure: Trigger = Trigger()
  lazy val measured: MeasuredFeature = new MeasuredFeature(this)
  val size: SizeFeature = new SizeFeature(this)
  lazy val position: PositionFeature = new PositionFeature(this)

  lazy val color: Prop[Color] = new Prop[Color](Color.Black, c => node.setColor(c.toHex))
  lazy val backgroundColor: Prop[Color] =
    new Prop[Color](Color.Clear, c => node.setBackgroundColor(if c.alpha == 0 then "" else c.toHex))
  /** In turns, as youi has it (1.0 is a full revolution). */
  lazy val rotation: Prop[Double] = new Prop[Double](0.0, turns => node.setRotation(turns * 2.0 * math.Pi))
  lazy val whiteSpace: Prop[WhiteSpace] =
    new Prop[WhiteSpace](WhiteSpace.Normal, ws => node.setWhiteSpace(ws.value), () => measure.trigger())

  lazy val effectiveWidth: Val[Double] = Val(size.width())
  lazy val effectiveHeight: Val[Double] = Val(size.height())
