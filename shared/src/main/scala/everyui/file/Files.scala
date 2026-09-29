package everyui.file

import everyui.async.Task
import everyui.component.Component
import everyui.render.FileAccess

/** Files from the person: chosen in the platform's own picker, or dragged onto a component from outside the app. The
  * web takes them with a file input and the page's drag and drop, the desktop with its file dialog and the window's,
  * Android with the system's document picker and its drag and drop. A Roku and an Apple TV have neither, and say so
  * (`pickable`, `droppable` false), so an app hides what would offer them.
  *
  * {{{
  *   if (Files.pickable) attach.event.click.attach(_ => Files.pick(List("image/\*")).map(add).start())
  *   Files.dropTarget(panel)(over => panel.borderWidth @= (if (over) 2.0 else 0.0))(add)
  * }}} */
object Files {
  /** Whether this platform has a picker to choose files in. */
  def pickable: Boolean = FileAccess.pickable()

  /** Whether files can be dropped on components here. */
  def droppable: Boolean = FileAccess.droppable()

  /** The files chosen in the platform's picker, which offers those of `types` (the web's `accept` words: "image/\*",
    * "text/plain", ".md"; empty for any), several at once with `multiple`. Nothing chosen, or no picker, is an empty
    * list. */
  def pick(types: List[String] = Nil, multiple: Boolean = true): Task[List[PickedFile]] =
    Task.async[List[PickedFile]](done => FileAccess.pick(types, multiple, done))

  /** Files dropped on `target` from outside the app: `hover` hears true while files are held over it and false once
    * they leave or land, and `dropped` what landed. Nothing happens where files cannot be dropped. */
  def dropTarget(target: Component)(hover: Boolean => Unit)(dropped: List[PickedFile] => Unit): Unit =
    FileAccess.onDrop(target.node, hover, dropped)
}
