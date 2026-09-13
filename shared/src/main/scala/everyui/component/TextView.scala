package everyui.component

import everyui.component.feature.FontFeature
import everyui.component.types.Prop
import everyui.render.{Nodes, RenderNode}

class TextView(node: RenderNode = Nodes.text()) extends Component(node):
  lazy val content: Prop[String] = new Prop[String](node.getText(), node.setText, () => measure.trigger())
  lazy val font: FontFeature = new FontFeature(this)

  override def kind: String = "TextView(" + content() + ")"
