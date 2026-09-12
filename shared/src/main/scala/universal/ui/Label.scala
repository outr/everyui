package universal.ui

import universal.render.{Nodes, RenderNode}

/** A text component — the smallest thing that proves the seam end to end:
  * `content` is a reactive [[Prop]] whose setter reaches the backend node.
  *
  * COMPOSES rather than extends `Component`. youi's real hierarchy is
  * `Label extends Component`; this was written before the Roku compiler had
  * class inheritance. It has it now (DESIGN.md, the object model), so this can
  * collapse back to `extends` when the widget layer is ported.
  */
class Label(val node: RenderNode):
  val component: Component = new Component(node)
  val content: Prop[String] = new Prop[String](() => node.getText(), v => node.setText(v))

  val x: Prop[Double] = component.x
  val y: Prop[Double] = component.y

object Label:
  def apply(): Label = new Label(Nodes.text())
