package universal.app

import reactify.*
import universal.component.TextView
import universal.render.Platform
import universal.ui

/** The vertical slice's app — ONE source, compiled against two backends.
  *
  * Nothing here names the DOM or SceneGraph. `label.content := …` is a reactify
  * expression: the label's content DEPENDS on `name` through reactify's
  * dependency tracking, and the content Prop's setter is whatever the linked
  * backend's `RenderNode` provides.
  */
object HelloWorld:
  def run(): Unit =
    val name = Var("World")

    val label = new TextView
    label.content := "Hello, " + name() + "!"
    label.position.left @= 100.0
    ui.children += label

    // Read back through the seam's OTHER half — `node.getText()` asks the
    // backend node itself, so this proves the Prop's setter actually reached the
    // SceneGraph / DOM, not just the reactive cell.
    Platform.log("mounted: " + label.content() + " | node: " + label.node.getText())

    // A reactive change AFTER mount — the surgical update the framework exists
    // for. Nothing re-assigns the content: reactify re-evaluates it because it
    // read `name`.
    name @= "Universal UI"
    Platform.log("updated: " + label.content() + " | node: " + label.node.getText())
    Platform.log("x: " + label.position.left().toString + " | node x: " + label.node.getX().toString)

    FrameProbe.run()
