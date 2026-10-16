package everyui.render

import javafx.scene.control.TextInputControl
import javafx.scene.input.{MouseButton, MouseEvent}
import javafx.scene.{Node, Scene}

/** Whether the click JavaFX is about to send was a click at all. A press belongs to what it went down on, as a phone's
  * controls have it: the innermost thing there that answers a click (`mark`ed) or takes typing (a field). JavaFX, as a
  * browser does, instead sends its click to the innermost node holding both where the button went down and where it
  * came up, which made a click of a drag: one that selected words in a field in a dialog and ended over the dim around
  * it went to the dialog's sheet, as a click outside the dialog, and closed it. A mouse's click here counts only when
  * its press and its release landed on the same such thing; a finger's tap (made into the mouse) is left as it is. */
private[render] object PressOwner {
  private val key = "everyui.clicks"
  private val watched = java.util.Collections.newSetFromMap(new java.util.WeakHashMap[Scene, java.lang.Boolean]())
  private var pressed: Option[Node] = None
  private var split = false

  /** `fx` answers clicks: a press on it, or on something in it that does not, is its. */
  def mark(fx: Node): Unit = {
    fx.getProperties.put(key, java.lang.Boolean.TRUE)
    if (Host.scene != null) watch(Host.scene)
    else fx.sceneProperty.addListener((_, _, scene) => if (scene != null) watch(scene))
  }

  /** Whether `e`, a click, came of a press and a release on different things: not to be heard. */
  def splits(e: MouseEvent): Boolean = split && !e.isSynthesized

  private def watch(scene: Scene): Unit = if (watched.add(scene)) {
    scene.addEventFilter(MouseEvent.MOUSE_PRESSED, (e: MouseEvent) => if (e.getButton == MouseButton.PRIMARY) {
      split = false
      pressed = if (e.isSynthesized) None else owner(under(e))
    })
    scene.addEventFilter(MouseEvent.MOUSE_RELEASED, (e: MouseEvent) => if (e.getButton == MouseButton.PRIMARY) {
      val released = owner(under(e))
      split = pressed.exists(p => p.getScene != null && released.exists(r => !(r eq p)))
      pressed = None
    })
    // heard by the scene last, once everything under the pointer has had the click
    scene.addEventHandler(MouseEvent.MOUSE_CLICKED, (_: MouseEvent) => split = false)
  }

  // the node under the pointer: a release is sent to what the press went down on, but picks where it is now
  private def under(e: MouseEvent): Node = {
    val picked = if (e.getPickResult == null) null else e.getPickResult.getIntersectedNode
    if (picked != null) picked else e.getTarget match {
      case n: Node => n
      case _ => null
    }
  }

  // the innermost thing from `node` up that answers a click or takes typing
  private def owner(node: Node): Option[Node] = {
    var at = node
    while (at != null && !answers(at)) at = at.getParent
    Option(at)
  }

  private def answers(node: Node): Boolean =
    (node.hasProperties && node.getProperties.containsKey(key)) || node.isInstanceOf[TextInputControl]
}
