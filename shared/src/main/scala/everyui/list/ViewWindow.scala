package everyui.list

import everyui.component.{Component, ScrollContainer}
import everyui.ui
import reactify.*

/** Where the view onto `c` is, down it: the part of `c` that the nearest ScrollContainer it is in scrolling up and down
  * shows (its `offset` and its height), or the screen's part where it is in none. `changed` hears whatever may move
  * it: that scroll moving or changing its height, and `c` or any container between it and the scroll moving, or being
  * put somewhere else (on the screen, say). It listens only to `c`, what it is in and that scroll, as `OnScreen` does,
  * so what is dropped without being disposed of is still let go. Walked with a loop (a Roku's stack is small). */
private[list] class ViewWindow(c: Component, changed: () => Unit) {
  private var undo: List[() => Unit] = Nil
  private var stopped = false
  private var scroll: Option[ScrollContainer] = None
  watch()

  /** Where the view starts, from `c`'s top: negative while `c` starts below it. Before `c` is anywhere it shows, its
    * top. */
  def start: Double = scroll match {
    case Some(s) => s.offset() - above()
    case None => if (onScreen) -above() else 0.0
  }

  /** How long the view is: the scroll's height, else the screen's (720 before either has one). */
  def length: Double = {
    val own = scroll.map(_.size.height()).getOrElse(0.0)
    if (own > 0.0) own else if (ui.size.height() > 0.0) ui.size.height() else 720.0
  }

  /** The scroll moved by `by` (rows above the view grew or shrank, and what shows is kept where it was). */
  def shift(by: Double): Unit = scroll.foreach(_.scrollBy(by))

  /** The scroll moved to show `c` from `y` down it. */
  def scrollTo(y: Double): Unit = scroll.foreach(_.scrollTo(above() + y))

  /** Listening no more, for good. */
  def stop(): Unit = {
    stopped = true
    clear()
  }

  // how far `c`'s top is below the top of what the scroll scrolls (or of the screen)
  private def above(): Double = {
    var at: Option[Component] = Some(c)
    var sum = 0.0
    var done = false
    while (!done && at.nonEmpty) {
      val x = at.get
      if (scroll.exists(s => x eq s.content)) done = true
      else {
        sum += x.position.top()
        at = x.parent()
      }
    }
    sum
  }

  /** Whether `c` is under `ui`: until it is, where it will show is a guess, and what is made for it is what shows of it
    * from its top. */
  def onScreen: Boolean = {
    var at: Component = c
    while (at.parent().nonEmpty) at = at.parent().get
    at eq ui
  }

  private def clear(): Unit = {
    undo.foreach(f => f())
    undo = Nil
  }

  private def hear[V](v: Val[V], f: () => Unit): Unit = {
    val heard = v.attach(_ => if (!stopped) f())
    undo = (() => v.reactions -= heard) :: undo
  }

  // `c` and each container up to the nearest scroll down the page listened to, for moving; and each up to the top, for
  // being put elsewhere (on the screen, say)
  private def watch(): Unit = {
    scroll = None
    var at: Option[Component] = Some(c)
    while (at.nonEmpty) {
      val x = at.get
      hear(x.parent, () => rewatch())
      if (scroll.isEmpty) {
        hear(x.position.top, changed)
        x.parent().flatMap(_.parent()).foreach {
          case s: ScrollContainer if !s.horizontal && x.parent().exists(_ eq s.content) =>
            scroll = Some(s)
            hear(s.offset, changed)
            hear(s.size.height, changed)
          case _ => ()
        }
      }
      at = x.parent()
    }
    if (scroll.isEmpty) hear(ui.size.height, changed)
  }

  private def rewatch(): Unit = {
    clear()
    watch()
    changed()
  }
}
