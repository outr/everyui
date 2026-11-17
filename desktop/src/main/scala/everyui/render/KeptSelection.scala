package everyui.render

import javafx.beans.InvalidationListener
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.scene.{Node, Scene}
import javafx.scene.control.TextField

/** What is chosen in a text field kept as the focus moves between other nodes. JavaFX's TextField behaviour lets its
  * field's choice go (`selectRange(0, 0)`) whenever the scene's focus owner becomes anything but the field, though
  * the field neither had the focus nor takes it: a pulse's focus cleanup, after a focused node left the scene, undid
  * a `selectAll` a turn old. The field's own focus still moves its choice as JavaFX has it: all of it chosen as the
  * keys or the app focus it, none as it loses the focus.
  *
  * Its scene's focus owner is watched from as the field's skin is made, after the behaviour that skin makes, and on
  * each scene it is put in after that: told of a move before the behaviour (invalidation listeners come before change
  * ones) and after it (a change listener added after the behaviour's), so a move that is not the field's (`held`) is
  * known for as long as the behaviour can answer it. */
final class KeptSelection(field: TextField) {
  // the scene's focus owner as last seen
  private var owner: Node = null
  private var holding = false

  /** Whether the focus is moving between nodes other than the field, now: its choice is not to be let go. */
  def held: Boolean = holding

  private val moving: InvalidationListener = o => {
    val now = o.asInstanceOf[ObservableValue[Node]].getValue
    holding = !(owner eq field) && !(now eq field)
    owner = now
  }
  private val moved: ChangeListener[Node] = (_, _, _) => holding = false

  /** The field's scene followed: called as its skin, and the behaviour with it, have been made. */
  def follow(): Unit = {
    watch(field.getScene)
    field.sceneProperty.addListener((_: ObservableValue[? <: Scene], was: Scene, now: Scene) => {
      if (was != null) {
        was.focusOwnerProperty.removeListener(moving)
        was.focusOwnerProperty.removeListener(moved)
      }
      watch(now)
    })
  }

  private def watch(scene: Scene): Unit = if (scene != null) {
    owner = scene.getFocusOwner
    holding = false
    scene.focusOwnerProperty.addListener(moving)
    scene.focusOwnerProperty.addListener(moved)
  }
}
