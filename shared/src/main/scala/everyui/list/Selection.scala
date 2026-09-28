package everyui.list

import reactify.*

/** Which rows of a list or a tree are chosen, by key: `keys` is the app's to read and set. `anchor` is where a range
  * (Shift+click, Shift+arrow) starts from. With `multiple` off only one row is ever chosen, and it follows the
  * cursor: what a remote wants, and the default where one drives the app. */
class Selection(multi: Boolean) {
  val keys: Var[Set[String]] = Var(Set.empty)
  val anchor: Var[Option[String]] = Var(None)
  val multiple: Var[Boolean] = Var(multi)

  def contains(key: String): Boolean = keys().contains(key)

  /** `key` alone, and the anchor there. */
  def only(key: String): Unit = {
    keys @= Set(key)
    anchor @= Some(key)
  }

  /** `key` in if it was out, out if it was in; the anchor there. */
  def toggle(key: String): Unit = {
    keys @= (if (contains(key)) keys() - key else keys() + key)
    anchor @= Some(key)
  }

  def clear(): Unit = {
    keys @= Set.empty
    anchor @= None
  }

  /** Only the keys `present` still has: what left the list leaves the selection. */
  def keep(present: String => Boolean): Unit = {
    val kept = keys().filter(present)
    if (kept.size != keys().size) keys @= kept
    if (anchor().exists(k => !present(k))) anchor @= None
  }
}
