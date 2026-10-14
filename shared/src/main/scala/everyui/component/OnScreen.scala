package everyui.component

import everyui.ui

/** Follows whether `c` is on the screen: in `ui`, through each container it is in, with neither it nor any of them
  * hidden (`visible`). `changed` hears each change of that as it happens, whether `c` itself or a container anywhere
  * above it was put in, taken out or hidden: a tab's content taken off the screen while another tab shows, a view
  * kept aside to be shown again.
  *
  * It listens only to `c` and the containers it is in, up to the first in none: taken off the screen, it is held by
  * nothing that stays there, so what is dropped without being disposed of is still let go (no weak references, which
  * a Roku has none of). Walked with a loop rather than by recursion (a Roku's stack is small). */
class OnScreen(c: Component, changed: Boolean => Unit) {
  private var undo: List[() => Unit] = Nil
  private var stopped = false
  private var on: Boolean = watch()

  /** Whether `c` is on the screen now. */
  def apply(): Boolean = on

  /** Listening no more, for good. */
  def stop(): Unit = {
    stopped = true
    clear()
  }

  private def clear(): Unit = {
    undo.foreach(f => f())
    undo = Nil
  }

  // each of `c` and the containers it is in listened to, for being moved or hidden: whether the last of them is `ui`,
  // none hidden
  private def watch(): Boolean = {
    var at: Option[Component] = Some(c)
    var top: Component = c
    var seen = true
    while (at.nonEmpty) {
      val x = at.get
      val moved = x.parent.attach(_ => look())
      val hidden = x.visible.attach(_ => look())
      undo = (() => {
        x.parent.reactions -= moved
        x.visible.reactions -= hidden
      }) :: undo
      if (!x.visible()) seen = false
      top = x
      at = x.parent()
    }
    seen && (top eq ui)
  }

  private def look(): Unit = if (!stopped) {
    clear()
    val now = watch()
    if (now != on) {
      on = now
      changed(now)
    }
  }
}
