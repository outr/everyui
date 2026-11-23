package everyui.list

import everyui.component.{Component, Container, ImageView}
import everyui.event.Key
import everyui.focus.Focus
import everyui.perf.Quality
import everyui.render.Platform
import reactify.*

/** A column of rows of any kind, each made only as it comes near the view: a Home of rails, a page of sections. Each
  * row is a `LazyItem`, its key, how tall it is taken to be and how to make it; a row is made once the view comes
  * within `ahead` views of it (half a screen, at Full: less below it, `everyui.perf.Quality`), and from then on
  * placed as tall as it draws itself, the rows after it moving with it. A row above the view that comes out taller or
  * shorter than it was taken to be moves what shows by as much the other way, so nothing in view jumps.
  *
  * It scrolls with whatever it is in: the view is the nearest ScrollContainer it is in that scrolls up and down (a
  * page, with a header above the column and more below it), or the screen. Its height is every row's, the rows not made
  * at their estimates, so the scroll is about as long as it will be; its width, unless given one, is its widest made
  * row's.
  *
  * Up and Down move the focus from row to row, making the next first where it is not made yet; Left and Right are the
  * rows' own (a rail's). A made row out of sight is not drawn and lets its pictures go, as a
  * ScrollContainer's children do, until it comes back into sight. Made rows stay made unless `releaseBeyond` says how far away one is taken apart, to be made
  * again from its item as it comes back; never the row the focus is in. `nearEnd` asks for more as the end comes near.
  *
  * Each row's key should be its own. One given to more than one row is told apart by its place among them (the
  * second `key#2`), so each is made where it belongs, and told once (`scribe.warn`).
  *
  * {{{
  *   val home = new LazyColumn(gap = 24.0)
  *   home.size.width := page.size.width()
  *   home.items @= rails.map(r => LazyItem(r.id, 260.0, () => railOf(r)))
  *   page.content.children ++= List(hero, home, footer)   // page: a ScrollContainer
  * }}} */
class LazyColumn(val gap: Double = 0.0) extends Container {
  override def kind: String = "LazyColumn"

  /** The rows, in order: the app's to set. A row whose key stays among them stays as it was made. */
  val items: Var[Vector[LazyItem]] = own(Var(Vector.empty))
  /** How far beyond each end of the view rows are made, in views: a half at Full, as far as a ScrollContainer draws (so
    * what is made is drawn, its pictures asked for once and kept), a quarter at Reduced and a tenth at Minimal. */
  val ahead: Var[Double] = own(Var(0.5))
  /** How far beyond each end of the view, in views, a made row is taken apart, to be made again as it comes back: 0,
    * by default, keeps every row made once it is. */
  val releaseBeyond: Var[Double] = own(Var(0.0))
  /** Told, with how many rows there are, as the rows made reach the last: once for each count, so an app reads its
    * next page here and appends it to `items`. */
  val nearEnd: Channel[Int] = Channel[Int]

  private var rows: Vector[LazyItem] = Vector.empty
  // each row's key, made unique (LazyKeys): what its made row is held by
  private var keys: Vector[String] = Vector.empty
  // (immutable maps: a Roku keeps one keyed by strings as an associative array, and has no mutable one)
  private var indexes: Map[String, Int] = Map.empty
  private var made: Map[String, Component] = Map.empty
  private var heights: Map[String, Double] = Map.empty
  private var tops: Array[Double] = Array(0.0)
  private var stale = false
  private val length: Var[Double] = own(Var(0.0))
  private var toldNear = -1
  private var filling = false
  private var again = false
  private var asked = false
  // the made rows out of sight, not drawn
  private var unseen: Set[Component] = Set.empty

  private val view = new ViewWindow(this, () => fill())
  onDispose(() => view.stop())
  size.height := length()
  layout @= Some(new LazyLayout(() => place(), (from: Component, key: Key) => beside(from, key), true))
  items.attach(_ => reread())
  ahead.attach(_ => fill())
  releaseBeyond.attach(_ => fill())

  /** How many rows there are. */
  def count: Int = rows.length
  /** How many rows are made now. */
  def madeCount: Int = made.size
  /** Where the row of `key` is, -1 where none is. */
  def indexOf(key: String): Int = indexes.getOrElse(key, -1)
  /** The row at `index`, where it is made. */
  def rowAt(index: Int): Option[Component] =
    if (index < 0 || index >= rows.length) None else made.get(keys(index))
  /** The row at `index`, made now where it is not yet (to put the focus in it): None past the rows. */
  def make(index: Int): Option[Component] = if (index < 0 || index >= rows.length) None else Some(build(index))
  /** Where row `index` starts, down the column. */
  def top(index: Int): Double = {
    if (stale) retop()
    tops(math.max(0, math.min(index, rows.length)))
  }
  /** The scroll it is in moved to show row `index` at the top of the view. */
  def scrollTo(index: Int): Unit = if (index >= 0 && index < rows.length) view.scrollTo(top(index))

  /** How tall row `index` is taken to be until it is made and measured: its estimate. */
  protected def guess(index: Int): Double = rows(index).estimate

  /** Row `index` drew itself `height` tall: for a column whose rows are alike (a grid), to take the rest to be so. */
  protected def measured(index: Int, height: Double): Unit = ()

  private def heightOf(index: Int): Double = heights.getOrElse(keys(index), guess(index))

  private def retop(): Unit = {
    val next = new Array[Double](rows.length + 1)
    var i = 0
    while (i < rows.length) {
      next(i + 1) = next(i) + heightOf(i) + gap
      i += 1
    }
    tops = next
    stale = false
  }

  private def total: Double = if (rows.isEmpty) 0.0 else top(rows.length) - gap

  // the row at `y` down the column, kept within the rows
  private def indexAt(y: Double): Int = {
    if (stale) retop()
    var low = 0
    var high = rows.length - 1
    while (low < high) {
      val mid = (low + high + 1) / 2
      if (tops(mid) <= y) low = mid else high = mid - 1
    }
    low
  }

  // the items read again: what left is taken apart, what stays keeps what it made and its height
  private def reread(): Unit = {
    rows = items()
    keys = LazyKeys.unique(rows.map(_.key), kind)
    var next = Map.empty[String, Int]
    var i = 0
    while (i < rows.length) {
      next = next.updated(keys(i), i)
      i += 1
    }
    indexes = next
    var gone: List[Component] = Nil
    made.foreach { case (k, c) => if (!next.contains(k)) gone = c :: gone }
    made = made.filter { case (k, _) => next.contains(k) }
    unseen = unseen.filter(c => !gone.exists(_ eq c))
    heights = heights.filter { case (k, _) => next.contains(k) }
    stale = true
    order()
    gone.foreach(_.dispose())
    length @= total
    requestLayout()
    fill()
  }

  // the made rows as the children, in the rows' order (the order they read in, and Tab moves through)
  private def order(): Unit = {
    val now = made.toVector.map { case (k, c) => (indexes(k), c) }.sortBy(_._1).map(_._2)
    val was = children()
    if (now.length != was.length || !now.indices.forall(i => now(i) eq was(i))) children @= now
  }

  // row `index` made, where it is not, at its place
  private def build(index: Int, alone: Boolean = true): Component = {
    val key = keys(index)
    made.get(key) match {
      case Some(c) => c
      case None =>
        val c = rows(index).build()
        c.position.top @= top(index)
        made = made.updated(key, c)
        if (alone) order()
        c
    }
  }

  // the rows near the view made, those far from it let go, and the app told as the end comes near
  private def fill(): Unit = if (!takenApart) {
    if (filling) again = true
    else {
      filling = true
      try {
        again = true
        while (again) {
          again = false
          fillOnce()
        }
      }
      finally filling = false
    }
  }

  private def fillOnce(): Unit = if (rows.nonEmpty) {
    val along = view.length
    val at = view.start
    // (none beyond the view before it is on the screen, where it will show is a guess)
    val beyond = if (view.onScreen) along * ahead() * Quality.slack / 0.5 else 0.0
    val first = indexAt(at - beyond)
    val last = indexAt(at + along + beyond)
    var changed = false
    var i = first
    while (i <= last) {
      if (!made.contains(keys(i))) {
        build(i, false)
        changed = true
      }
      i += 1
    }
    var gone: List[Component] = Nil
    if (releaseBeyond() > 0.0) {
      val far = along * releaseBeyond()
      val kept = LazyColumn.focusIn(this)
      made.foreach { case (k, c) =>
        val j = indexes(k)
        if ((top(j + 1) - gap < at - far || top(j) > at + along + far) && !kept.exists(_ eq c)) gone = c :: gone
      }
      if (gone.nonEmpty) {
        made = made.filter { case (_, c) => !gone.exists(_ eq c) }
        unseen = unseen.filter(c => !gone.exists(_ eq c))
        changed = true
      }
    }
    // (the made and let go put in at once, in one layout of them all)
    if (changed) order()
    gone.foreach(_.dispose())
    cull(at, along)
    if (last >= rows.length - 1 && toldNear != rows.length) {
      toldNear = rows.length
      nearEnd @= rows.length
    }
  }

  // each made row drawn where it is in sight, or half a view beside it (less below Full), and where it holds the focus;
  // the rest not, their pictures let go, as a ScrollContainer culls its children (on the web hidden from the page, found
  // by its find in page)
  private def cull(at: Double, along: Double): Unit = {
    val slack = along * Quality.slack
    val kept = LazyColumn.focusIn(this)
    made.foreach { case (k, c) =>
      val i = indexes(k)
      seen(c, (top(i + 1) - gap > at - slack && top(i) < at + along + slack) || kept.exists(_ eq c))
    }
  }

  private def seen(c: Component, on: Boolean): Unit = if (unseen.contains(c) == on) {
    unseen = if (on) unseen - c else unseen + c
    if (Platform.nativeScroll()) c.node.setCulled(!on, () => seen(c, true)) else c.visible @= on
    ImageView.showing(c, on)
  }

  // the made rows placed, at the heights they drew; the first made row starting in the view (once it has scrolled) is
  // kept where it is on the screen, the view moved by as much as the rows above it grew or shrank
  private def place(): Unit = {
    if (stale) retop()
    val at = view.start
    var anchor = -1
    if (at > 0.0) made.foreach { case (k, _) =>
      val i = indexes(k)
      if (tops(i) >= at - 0.5 && (anchor < 0 || i < anchor)) anchor = i
    }
    val anchorTop = if (anchor >= 0) tops(anchor) else 0.0
    made.foreach { case (k, c) =>
      val h = c.size.height()
      if (h > 0.0 && !heights.get(k).contains(h)) {
        val i = indexes(k)
        heights = heights.updated(k, h)
        measured(i, h)
        stale = true
      }
    }
    if (stale) retop()
    made.foreach { case (k, c) => c.position.top @= tops(indexes(k)) }
    length @= total
    if (anchor >= 0 && math.abs(tops(anchor) - anchorTop) >= 0.5) view.shift(tops(anchor) - anchorTop)
    // what those drawn shorter than taken to be leave room for, made once the pass is done
    if (!asked) {
      asked = true
      Platform.afterTurn(() => {
        asked = false
        fill()
      })
    }
  }

  /** Up or Down from the row holding `from`: the row before or after it, made first where it is not. */
  protected def beside(from: Component, key: Key): Option[Component] =
    if (key != Key.ArrowUp && key != Key.ArrowDown) None
    else {
      var i = -1
      made.foreach { case (k, c) => if (c eq from) i = indexes(k) }
      val j = if (key == Key.ArrowDown) i + 1 else i - 1
      if (i < 0 || j < 0 || j >= rows.length) None
      else {
        // (drawn, to be moved to: what is not drawn takes no focus)
        val next = build(j)
        seen(next, true)
        Some(next)
      }
    }
}

object LazyColumn {
  /** The child of `box` the focus is in, or on, where it is in one. */
  private[list] def focusIn(box: Component): Option[Component] = Focus.focused().flatMap { f =>
    var at: Option[Component] = Some(f)
    var found: Option[Component] = None
    while (found.isEmpty && at.nonEmpty) {
      if (at.get.parent().exists(_ eq box)) found = at
      at = at.get.parent()
    }
    found
  }
}
