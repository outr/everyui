package everyui.tray

import everyui.menu.MenuItem
import java.lang.foreign.MemorySegment

/** A tray icon's menu on macOS: an NSMenu of the app's `MenuItem`s, made again from them as they change and each time
  * it opens (its delegate, `target`, is asked `menuNeedsUpdate:`), so a tick follows its `on`. Each entry is numbered as
  * `TrayMenu.flatten` numbers it, its tag, which a choice (`chosen:` to `target`) comes back with. Made and filled on
  * the main thread. */
private[tray] class MacMenu(target: MemorySegment) {
  import ObjC.*

  /** The NSMenu (retained until `release`). */
  val ns: MemorySegment = id(id(cls("NSMenu"), "alloc"), "initWithTitle:", str(""))
  withBool(ns, "setAutoenablesItems:", false)
  id(ns, "setDelegate:", target)

  private var flat = Vector.empty[MenuItem]

  /** The entries shown, as they are now. */
  def fill(items: List[MenuItem]): Unit = {
    val (f, kids) = TrayMenu.flatten(items)
    flat = f
    id(ns, "removeAllItems")
    add(ns, 0, kids)
  }

  private def add(into: MemorySegment, parent: Int, kids: Map[Int, Vector[Int]]): Unit =
    kids.getOrElse(parent, Vector.empty).foreach { k =>
      val m = flat(k - 1)
      if (m.separator) id(into, "addItem:", id(cls("NSMenuItem"), "separatorItem"))
      else {
        val action = if (MacMenu.runs(m)) sel("chosen:") else NULL
        val item = id(id(cls("NSMenuItem"), "alloc"), "initWithTitle:action:keyEquivalent:", str(m.label), action, str(""))
        id(item, "setTarget:", target)
        withLong(item, "setTag:", k.toLong)
        withBool(item, "setEnabled:", m.enabled)
        MacMenu.state(m).foreach(s => withLong(item, "setState:", s))
        if (m.submenu.nonEmpty) {
          val sub = id(id(cls("NSMenu"), "alloc"), "initWithTitle:", str(m.label))
          withBool(sub, "setAutoenablesItems:", false)
          add(sub, k, kids)
          id(item, "setSubmenu:", sub)
          ObjC.release(sub)
        }
        id(into, "addItem:", item)
        ObjC.release(item)
      }
    }

  /** The entry tagged `tag`, where choosing it does something. */
  def entry(tag: Long): Option[MenuItem] = MacMenu.chosen(flat, tag)

  def release(): Unit = {
    id(ns, "setDelegate:", NULL)
    ObjC.release(ns)
  }
}

private[tray] object MacMenu {
  /** NSControlStateValueOn and Off. */
  val On: Long = 1L
  val Off: Long = 0L

  /** Whether choosing `m` runs its action: one that can be chosen, with no submenu. */
  def runs(m: MenuItem): Boolean = m.choosable && m.submenu.isEmpty

  /** The tick an entry shows, where it is one that can be ticked. */
  def state(m: MenuItem): Option[Long] =
    if (m.checked.nonEmpty || m.on.nonEmpty) Some(if (m.ticked) On else Off) else None

  /** Of the entries numbered by `TrayMenu.flatten`, the one tagged `tag`, where choosing it runs its action. */
  def chosen(flat: Vector[MenuItem], tag: Long): Option[MenuItem] =
    if (tag < 1 || tag > flat.size) None else Some(flat((tag - 1).toInt)).filter(runs)
}
