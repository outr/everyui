package everyui.render

import everyui.file.PickedFile

/** Files from the person (`everyui.file.Files`). A remote has no picker and nothing is dragged onto a television, so
  * this platform offers neither: a pick answers at once with nothing, and no drop ever comes. */
object FileAccess {
  def pickable(): Boolean = false

  def droppable(): Boolean = false

  def pick(types: List[String], multiple: Boolean, done: List[PickedFile] => Unit): Unit = done(Nil)

  def onDrop(node: RenderNode, hover: Boolean => Unit, dropped: List[PickedFile] => Unit): Unit = ()

  /** Done with a picked file's `preview`: nothing is held for one here. */
  def release(preview: String): Unit = ()
}
