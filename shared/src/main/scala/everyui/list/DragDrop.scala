package everyui.list

import everyui.component.{Component, Container, TextView}
import everyui.event.Key
import everyui.focus.Focus
import everyui.render.Platform
import everyui.theme.Theme
import everyui.{Shadow, ui}
import reactify.*

/** Rows dragged within and between lists and trees. A drag starts on a `draggable` list and follows the pointer with a
  * label beside it; over a list, its `accepts` says whether the rows may land where they are, a line or a lit row shows
  * where, the list scrolls near its ends, and a tree opens a folder held over. Let go there, the list's `dropped` hears
  * it; Escape, or letting go where nothing accepts it, drops nothing.
  *
  * From the keyboard, `cut` on a list holds its chosen rows and `paste` on a list puts them against the row under its
  * cursor, through the same `accepts` and `dropped`: an app puts the two on its keymap. */
object DragDrop {
  /** The rows being dragged by the pointer. */
  val dragging: Var[Option[Dragged]] = Var(None)
  /** The rows cut, waiting to be pasted. */
  val held: Var[Option[Dragged]] = Var(None)
  /** Whether a drag has just ended: the click it ends in, heard before the next turn, is not a click. */
  private[list] var justEnded: Boolean = false

  private var ghost: Option[Container] = None
  private var targets: Vector[VirtualList[?]] = Vector.empty
  private var over: Option[VirtualList[?]] = None
  private var escape: () => Unit = () => ()
  private var atX = 0.0
  private var atY = 0.0

  private[list] def begin(rows: Dragged, label: String, x: Double, y: Double): Unit = {
    finish()
    dragging @= Some(rows)
    targets = lists()
    val words = new TextView {
      content @= label
      font.size @= 13.0
      color := Theme.text()
      position.left @= 10.0
      position.top @= 5.0
    }
    val tag = new Container {
      backgroundColor := Theme.raised()
      borderColor := Theme.accent()
      borderWidth @= 1.0
      radius @= 4.0
      shadow @= Some(Shadow(0.0, 2.0, 8.0, Theme.backdrop()))
      opacity @= 0.92
      size.width := words.size.width() + 20.0
      size.height := words.size.height() + 10.0
      children += words
    }
    ghost = Some(tag)
    ui.children += tag
    escape = Focus.onKey(k => if ((k == Key.Escape || k == Key.Back) && dragging().nonEmpty) {
      cancel()
      true
    } else false)
    move(x, y)
  }

  /** The pointer at `x`, `y` while rows are dragged. */
  private[list] def move(x: Double, y: Double): Unit = dragging().foreach { rows =>
    atX = x
    atY = y
    ghost.foreach { g =>
      g.position.left @= x + 14.0
      g.position.top @= y + 10.0
    }
    val under = targets.reverse.find(t => Focus.showing(t) && inside(t, x, y))
    if (!under.exists(u => over.exists(_ eq u))) over.foreach(_.leaveDrop())
    over = under
    under.foreach(_.hoverDrop(x, y, rows))
  }

  /** Where the pointer was last, the list under it asked again (it scrolled under the pointer). */
  private[list] def again(): Unit = move(atX, atY)

  /** Let go at `x`, `y`: the rows land where the list under the pointer accepts them. */
  private[list] def end(x: Double, y: Double): Unit = dragging().foreach { rows =>
    move(x, y)
    val target = over
    finish()
    target.foreach(_.dropAt(x, y, rows))
  }

  /** The drag let go, nothing dropped. */
  def cancel(): Unit = finish()

  /** Whether `list` is where the pointer is dragging over now. */
  private[list] def hovering(list: VirtualList[?]): Boolean = over.exists(_ eq list)

  private def finish(): Unit = {
    if (dragging().nonEmpty) {
      justEnded = true
      Platform.later(() => justEnded = false)
    }
    over.foreach(_.leaveDrop())
    over = None
    targets = Vector.empty
    ghost.foreach { g =>
      ui.children -= g
      g.dispose()
    }
    ghost = None
    escape()
    escape = () => ()
    dragging @= None
  }

  private def inside(c: Component, x: Double, y: Double): Boolean =
    x >= c.absoluteLeft && x < c.absoluteLeft + c.size.width() && y >= c.absoluteTop && y < c.absoluteTop + c.size.height()

  // the lists on the screen, in the order they are drawn
  private def lists(): Vector[VirtualList[?]] = {
    var found: Vector[VirtualList[?]] = Vector.empty
    var pending: List[Component] = List(ui)
    while (pending.nonEmpty) {
      val c = pending.head
      pending = c.childComponents.toList ++ pending.tail
      c match {
        case l: VirtualList[?] => found = found :+ l
        case _ => ()
      }
    }
    found
  }
}
