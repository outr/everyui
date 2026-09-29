package everyui.list

import reactify.*

/** Which rows of a list or a tree are chosen, by key: `keys` is the app's to read and set. `anchor` is where a range
  * (Shift+click, Shift+arrow) starts from. With `multiple` off only one row is ever chosen, and it follows the
  * cursor: what a remote wants, and the default where one drives the app.
  *
  * With `all` on, every row is chosen but those in `keys`, the rows not loaded yet among them: what choosing all of a
  * list over a paged source means (`VirtualList.selectAll` there), since its rows are not all known. Choosing one row
  * alone, a range, or clearing turns it off; toggling a row takes it out of the rest, or puts it back. */
class Selection(multi: Boolean) {
  val keys: Var[Set[String]] = Var(Set.empty)
  val anchor: Var[Option[String]] = Var(None)
  val multiple: Var[Boolean] = Var(multi)
  /** Every row chosen, but those in `keys`. */
  val all: Var[Boolean] = Var(false)

  def contains(key: String): Boolean = all() != keys().contains(key)

  /** Whether nothing is chosen. */
  def isEmpty: Boolean = !all() && keys().isEmpty

  /** `key` alone, and the anchor there. */
  def only(key: String): Unit = {
    all @= false
    keys @= Set(key)
    anchor @= Some(key)
  }

  /** `key` in if it was out, out if it was in; the anchor there. */
  def toggle(key: String): Unit = {
    keys @= (if (keys().contains(key)) keys() - key else keys() + key)
    anchor @= Some(key)
  }

  /** Every row chosen, none left out (`all`). */
  def everything(): Unit = {
    keys @= Set.empty
    all @= true
  }

  def clear(): Unit = {
    all @= false
    keys @= Set.empty
    anchor @= None
  }

  /** How many rows are chosen, of `count`. */
  def size(count: Int): Int = if (all()) math.max(0, count - keys().size) else keys().size

  /** Only the keys `present` still has: what left the list leaves the selection. */
  def keep(present: String => Boolean): Unit = {
    val kept = keys().filter(present)
    if (kept.size != keys().size) keys @= kept
    if (anchor().exists(k => !present(k))) anchor @= None
  }
}
