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
  * A source can be searched through it where it says so (`searchable`, `query`, sent with each request) and sorted by
  * the columns it names (`sortable`): a `DataTable` of its rows (`DataTable.paged`) offers only those. A request that
  * takes a while says how far it has got (`PageRequest.progress`, heard as `progress`) and stops once it is no longer
  * wanted (`PageRequest.live`); `walk` reads every row of the query in order, a page at a time, for an export; `close`
  * is done with it.
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
  /** The words the rows must match, where the source is searched (`searchable`): sent with every request
    * (`PageRequest.query`), and a change asks again from the first page, as a new sort does. */
  val query: Var[String] = Var("")
  /** Whether the server can be searched by `query`: a table offers its filter field only then. */
  val searchable: Var[Boolean] = Var(false)
  /** The columns (by key) the server can sort by: a table offers to sort by those headings alone. */
  val sortable: Var[Set[String]] = Var(Set.empty)

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
  private val scanned: Var[Option[Double]] = Var(None)
  /** How far a request that takes a while has got (0 to 1), as its server says (`PageRequest.progress`), while it is
    * under way: "Searching… 38%". None once it has answered, and for a query that has not said. */
  val progress: Val[Option[Double]] = scanned

  // the query's number: an answer to one before is dropped
  private var generation = 0
  private var closed = false
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
  query.attach(_ => invalidate())

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

  /** How far the rows that have come reach: the place after the furthest. */
  def reached: Int = extent

  /** Whether every row of the query has come to be counted: the last page has, or the server said how many. */
  def countKnown: Boolean = !waiting() && (!more || total().nonEmpty)

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
    scanned @= None
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

  /** Done with: nothing more is asked for, a request under way is told it is no longer wanted (`PageRequest.live`), and
    * whatever it answers is dropped. */
  def close(): Unit = {
    closed = true
    generation += 1
    flying = Set.empty
    if (busy()) busy @= false
    scanned @= None
  }

  /** Every row of the query as it is now (its words and its sort), read from the first page in order, a page at a time
    * apart from what the view keeps: each page handed to `each`, which answers whether to go on once it has done with
    * it, so no more than a page is held. For an export of every row. It ends at the last page (one short of a page,
    * one with no `next` where the server pages by cursor, or the count reached), when `each` answers false, or when the
    * source is closed; it answers how many rows were handed over, or why a page failed. A request it makes is `live`
    * while it goes on, whatever the view does meanwhile. */
  def walk(each: Vector[T] => Task[Boolean]): Task[Either[String, Int]] = {
    val words = query()
    val order = sort()
    var stopped = false
    def from(offset: Int, cursor: Option[String], first: Boolean): Task[Either[String, Int]] =
      if (closed) Task.pure(Right(offset))
      else fetch(PageRequest(offset, pageSize, order, cursor, words, () => !stopped && !closed)).flatMap {
        case Left(why) =>
          stopped = true
          Task.pure(Left(why))
        case Right(page) =>
          val end = offset + page.rows.length
          val last = page.rows.isEmpty || (if (page.next.nonEmpty) false
            else if (cursor.nonEmpty && !first) true
            else page.rows.length < pageSize || page.total.exists(_ <= end))
          each(page.rows).flatMap { more =>
            if (!more) stopped = true
            // the next page a turn later: an export never holds the screen up, nor a source that answers at once the stack
            if (!more || last || closed) Task.pure(Right(end))
            else Task.async[Either[String, Int]](done =>
              everyui.render.Platform.later(() => from(end, page.next, first = false).map(done).start()))
          }
      }
    from(0, None, first = true)
  }

  // what page `offset` is asked for with, for the query whose number is `asked`
  private def request(offset: Int, cursor: Option[String], asked: Int): PageRequest =
    PageRequest(offset, pageSize, sort(), cursor, query(), () => asked == generation && !closed,
      (done: Double) => if (asked == generation && !closed) scanned @= Some(math.max(0.0, math.min(1.0, done))))

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

  private def dispatch(): Unit = if (!closed) {
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
    fetch(request(page * pageSize, starts.getOrElse(page, None), asked)).map { answer =>
      if (asked == generation) arrived(page, answer)
    }.start()
  }

  private def arrived(page: Int, answer: Either[String, Page[T]]): Unit = {
    flying -= page
    if (scanned().nonEmpty) scanned @= None
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
