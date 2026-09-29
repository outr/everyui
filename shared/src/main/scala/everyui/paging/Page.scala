package everyui.paging

/** A server's answer to a `PageRequest`: the rows, how many there are in all where it counts them (`total`, which may
  * change from one answer to the next), and where it pages by cursor, the one that asks for the page after (`next`:
  * none on the last).
  *
  * A server that pages by offset alone gives no `next`: its rows end at a page that comes short of the limit, or at
  * `total`. One that pages by cursor gives a `next` on every page but the last. */
case class Page[T](rows: Vector[T], total: Option[Int] = None, next: Option[String] = None)
