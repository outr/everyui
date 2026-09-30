package everyui.paging

import everyui.async.Task
import everyui.list.ListSource
import everyui.table.Sorting
import reactify.*

/** A server's rows, a page at a time, for a `VirtualList` or a `VirtualTable` of any length: only the pages near what
  * shows are asked for, and at most `maxPages` are kept, so a million rows cost what a few hundred do.
  *
  * `fetch` asks the server for a page (`PageRequest`: an offset and a limit, the sort, a cursor) and answers the rows,
  * the count where the server gives one, and the next page's cursor where it pages by cursor; or, failing, a message.
  * It must always complete. Whether any page can be asked for by its offset, or only the one after the last, is
  * `paging` (`Paging.Anywhere`, `Paging.InOrder`).
  *
  * A row not come yet is a placeholder (the list's `placeholder`, the table's a grey bar per cell), its page asked for as it comes
  * within `ahead` rows of the view; no more than `concurrent` pages are asked for at once, nearest the view first, and a
  * page the view has moved away from before its turn is never asked for at all. A new `sort`, or `invalidate()` (the
  * app's filter changed), starts again from the first page: what was kept is let go, and an answer to the query before
  * is dropped when it comes. A page that fails is not asked for again until `retry()`; `error` says why.
  *
  * {{{
  *   val source = new PagedSource[Entity](_.id, pageSize = 100)(request => Task.async { done =>
  *     api.list(filters(), request.offset, request.limit, request.sort)(
  *       r => done(Right(Page(r.entities, Some(r.total)))),
  *       e => done(Left(e.getMessage)))
  *   })
  *   filters.attach(_ => source.invalidate())
  *   val table = VirtualTable.paged(source, _.name, columns)
  *   status := source.total().map(n => n.toString + " results").getOrElse("Searching…")
  * }}} */
class PagedSource[T](val keyOf: T => String, val pageSize: Int = 100, val paging: Paging = Paging.Anywhere,
                     val maxPages: Int = 20)(fetch: PageRequest => Task[Either[String, Page[T]]]) extends ListSource[T] {
  val changed: Channel[Unit] = Channel[Unit]
  /** Heard when the rows start again from the first page: a new sort, or `invalidate`. */
  val reset: Channel[Unit] = Channel[Unit]
  /** How the server is asked to sort the rows; a change asks again from the first page. */
  val sort: Var[Option[Sorting]] = Var(None)
  /** How many rows before and after the view are fetched ahead of it. */
  val ahead: Var[Int] = Var(math.max(1, pageSize / 2))
  /** How many pages may be asked for at once. */
  val concurrent: Var[Int] = Var(2)

  private val counted: Var[Option[Int]] = Var(None)
  private val waiting: Var[Boolean] = Var(true)
  private val busy: Var[Boolean] = Var(false)
  private val failure: Var[Option[String]] = Var(None)
  /** How many rows the server says there are, from its latest answer to this query. */
  val total: Val[Option[Int]] = counted
  /** Whether this query has had no answer yet: "Searching…". */
  override val searching: Val[Boolean] = waiting
  /** Whether a page is being fetched. */
  val loading: Val[Boolean] = busy
  /** Why the last page to fail failed, until `retry` or a new query. */
  val error: Val[Option[String]] = failure

  // the query's number: an answer to one before is dropped
  private var generation = 0
  private var pages: Map[Int, Vector[T]] = Map.empty
  // when each page was last in view, for letting go of the one out of view longest
  private var used: Map[Int, Int] = Map.empty
  private var tick = 0
  // where each row that is here is, by its key
  private var keys: Map[String, Int] = Map.empty
  private var flying: Set[Int] = Set.empty
  private var failed: Set[Int] = Set.empty
  // the pages to have, nearest the view first
  private var wanted: Vector[Int] = Vector(0)
  // the cursor each page is asked for with, where it is known (in order, a page is reached from the one before)
  private var starts: Map[Int, Option[String]] = Map(0 -> None)
  // where the rows that have come end, and whether there is more after
  private var extent = 0
  private var more = true
  private var cursored = false

  sort.attach(_ => invalidate())

  /** How many rows there are: the count, or what has come and a page of placeholders after it while there is more; a
    * page of placeholders while the first answer is awaited. */
  def count: Int = if (waiting()) pageSize else counted() match {
    case Some(n) if paging == Paging.Anywhere => n
    case other => extent + (if (!more) 0 else math.max(0, math.min(pageSize, other.map(_ - extent).getOrElse(pageSize))))
  }

  def at(index: Int): Option[T] = pages.get(index / pageSize).flatMap(_.lift(index % pageSize))

  def keyAt(index: Int): String = at(index).map(keyOf).getOrElse(PagedSource.placeKey(index))

  def indexOf(key: String): Int = keys.get(key) match {
    case Some(i) => i
    case None => PagedSource.placeOf(key).filter(_ < count).getOrElse(-1)
  }

  def here: Vector[T] = pages.keys.toVector.sorted.flatMap(p => pages(p))

  /** How many rows are kept now: never more than `maxPages` pages' worth, beyond what one view needs. */
  def kept: Int = pages.values.map(_.size).sum

  def showing(first: Int, last: Int): Unit = {
    val end = count - 1
    if (end >= 0) {
      val near = math.max(0, first) / pageSize
      val far = math.min(end, last) / pageSize
      val from = math.max(0, first - ahead()) / pageSize
      val to = math.min(end, last + ahead()) / pageSize
      var next = Vector.empty[Int]
      var p = near
      while (p <= far) {
        next = next :+ p
        p += 1
      }
      p = from
      while (p <= to) {
        if (p < near || p > far) next = next :+ p
        p += 1
      }
      tick += 1
      next.foreach(page => used = used.updated(page, tick))
      wanted = next.filter(reachable)
      dispatch()
    }
  }

  /** The query changed (the app's filter, say): the rows start again from the first page, and whatever is still
    * coming for the query before is dropped. */
  def invalidate(): Unit = {
    generation += 1
    pages = Map.empty
    used = Map.empty
    keys = Map.empty
    flying = Set.empty
    failed = Set.empty
    wanted = Vector(0)
    starts = Map(0 -> None)
    extent = 0
    more = true
    cursored = false
    counted @= None
    failure @= None
    busy @= false
    waiting @= true
    reset @= (())
    changed @= (())
    dispatch()
  }

  /** The pages that failed asked for again, where they are still wanted. */
  def retry(): Unit = {
    failed = Set.empty
    failure @= None
    dispatch()
  }

  /** `item` put in place of the row with its key, where that row is here: a change the app made on the server. */
  def replace(item: T): Unit = keys.get(keyOf(item)).foreach { i =>
    val page = i / pageSize
    pages.get(page).foreach { rows =>
      pages = pages.updated(page, rows.updated(i % pageSize, item))
      changed @= (())
    }
  }

  private def reachable(page: Int): Boolean =
    if (paging == Paging.InOrder || counted().isEmpty) starts.contains(page) || page * pageSize < extent
    else page * pageSize < counted().getOrElse(0)

  private def dispatch(): Unit = {
    var i = 0
    while (flying.size < concurrent() && i < wanted.length) {
      val page = wanted(i)
      if (!pages.contains(page) && !flying.contains(page) && !failed.contains(page)) ask(page)
      i += 1
    }
  }

  private def ask(page: Int): Unit = {
    val asked = generation
    flying += page
    if (!busy()) busy @= true
    fetch(PageRequest(page * pageSize, pageSize, sort(), starts.getOrElse(page, None))).map { answer =>
      if (asked == generation) arrived(page, answer)
    }.start()
  }

  private def arrived(page: Int, answer: Either[String, Page[T]]): Unit = {
    flying -= page
    answer match {
      case Left(message) =>
        failed += page
        failure @= Some(message)
      case Right(p) =>
        if (p.next.nonEmpty) cursored = true
        val end = page * pageSize + p.rows.length
        if (end >= extent) {
          extent = end
          more = p.next.nonEmpty || (!cursored && p.rows.length >= pageSize && p.total.forall(_ > end))
          if (more) starts = starts.updated(page + 1, p.next)
        }
        store(page, p.rows)
        if (p.total.nonEmpty && counted() != p.total) counted @= p.total
        if (waiting()) waiting @= false
    }
    if (busy() != flying.nonEmpty) busy @= flying.nonEmpty
    changed @= (())
    dispatch()
  }

  private def store(page: Int, rows: Vector[T]): Unit = {
    pages.get(page).foreach(old => forget(page, old))
    pages = pages.updated(page, rows)
    var i = 0
    while (i < rows.length) {
      keys = keys.updated(keyOf(rows(i)), page * pageSize + i)
      i += 1
    }
    if (!used.contains(page)) used = used.updated(page, tick)
    // the page out of view longest let go, while there are too many (never one in view)
    var over = pages.size - maxPages
    while (over > 0) {
      var oldest = -1
      pages.keys.foreach { p =>
        if (!wanted.contains(p) && (oldest < 0 || used.getOrElse(p, 0) < used.getOrElse(oldest, 0))) oldest = p
      }
      if (oldest < 0) over = 0
      else {
        pages.get(oldest).foreach(old => forget(oldest, old))
        pages = pages - oldest
        used = used - oldest
        over -= 1
      }
    }
  }

  // page `page`'s rows no longer found by their keys (where a key has not since been found on another page)
  private def forget(page: Int, rows: Vector[T]): Unit = rows.foreach { t =>
    val key = keyOf(t)
    if (keys.get(key).exists(_ / pageSize == page)) keys = keys - key
  }
}

object PagedSource {
  // what a place's key starts with, while its row has not come: nothing an app keys its rows by
  private val placeMark = "…#"

  /** The key standing for place `index` while its row has not come. */
  def placeKey(index: Int): String = placeMark + index.toString

  /** Whether `key` stands for a place rather than being a row's own. */
  def isPlace(key: String): Boolean = key.startsWith(placeMark)

  /** The place `key` stands for, where it stands for one. */
  def placeOf(key: String): Option[Int] = if (!isPlace(key) || key.length == placeMark.length) None else {
    var n = 0
    var i = placeMark.length
    var digits = true
    while (digits && i < key.length) {
      val c = key.charAt(i)
      if (c >= '0' && c <= '9') n = n * 10 + (c - '0') else digits = false
      i += 1
    }
    if (digits) Some(n) else None
  }
}
