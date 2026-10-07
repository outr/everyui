package everyui.list

import reactify.*

/** Rows a `VirtualList` reads by place instead of holding them all: how many there are, the item at a place where it
  * has come (a row not here yet is drawn as a placeholder), each place's key, and where a key is. A list over a source
  * tells it which places are in view (`showing`), for a source that fetches its rows as they are neared, and follows
  * it whenever it says it `changed`.
  *
  * A place whose row has not come still has a key (one the source makes up for the place), so the cursor and the
  * selection can stand on it; once the row comes, the list moves them onto the row's own key. `everyui.paging` has the
  * source of a server's paged rows. */
trait ListSource[T] {
  /** How many rows there are, those not here yet among them. */
  def count: Int

  /** The row at `index`, where it has come. */
  def at(index: Int): Option[T]

  /** The key at `index`: the row's own where it has come, else one standing for the place. */
  def keyAt(index: Int): String

  /** Where `key` is (a row's own, or one standing for a place), -1 where it is not known. */
  def indexOf(key: String): Int

  /** The rows here now, in their order: all that can be read without asking for more. */
  def here: Vector[T]

  /** The rows from `first` to `last` are in view (or about to be). */
  def showing(first: Int, last: Int): Unit

  /** Heard when anything above changes: rows come, the count, a new query. */
  def changed: Channel[Unit]

  /** Whether the rows are being looked for and none has come yet (a query not answered): the list shows its
    * `loadingView` meanwhile. Heard through `changed`. */
  def searching: Val[Boolean] = ListSource.idle

  /** Whether its rows are of heights it knows without their being drawn (`top`, `indexAt`): a log's lines wrapped,
    * each as tall as the lines it wraps onto. Otherwise each is the list's `rowHeight` tall. */
  def sized: Boolean = false

  /** Where row `index` starts, from the top of the first (`count` for the bottom of the last), where it is `sized`. */
  def top(index: Int): Double = 0.0

  /** The row at `y` down the rows, where it is `sized`. */
  def indexAt(y: Double): Int = 0
}

object ListSource {
  private val idle: Val[Boolean] = Val(false)
}
