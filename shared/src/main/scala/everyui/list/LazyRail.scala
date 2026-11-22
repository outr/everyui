package everyui.list

import everyui.component.{Component, ScrollContainer}
import everyui.event.Key
import everyui.i18n.Direction
import everyui.layout.Neighbours
import everyui.perf.Quality
import everyui.render.Platform
import reactify.*

/** A rail of cards of one width that makes only the cards in its view and `ahead` beyond each end, adding more as it
  * scrolls toward them, where a `ScrollContainer` of every card makes them all and culls those out of view. The rail
  * is as long as every card would make it, so it scrolls as far and its scrollbar is right from the first.
  *
  * Left and Right move the focus along it, making the next card first where it is not made yet, so the cards always
  * run on under the focus; and the focus landing on a card anywhere (a click, Tab, the app) scrolls it into view, which
  * makes those around it. Made cards stay made unless `releaseBeyond` says how many cards away one is taken apart;
  * never the card the focus is in. It is as tall as its tallest made card, with the content's padding round them,
  * unless given a height. `nearEnd` asks for more as the end comes near.
  *
  * {{{
  *   val rail = new LazyRail(Var(titles), (t: Title) => t.id, cardWidth = 120.0, gap = 12.0)(t => new PosterCard(t))
  *   rail.size.width := page.size.width()
  * }}} */
class LazyRail[T](val items: Var[Vector[T]], val keyOf: T => String, val cardWidth: Double, val gap: Double = 16.0)
                 (card: T => Component) extends ScrollContainer(horizontal = true) {
  override def kind: String = "LazyRail"

  /** How many cards are made beyond each end of the view: 2 at Full (within what a ScrollContainer draws beside its
    * view, on all but the narrowest, so what is made is drawn and its picture asked for once), 1 below it. */
  val ahead: Var[Int] = own(Var(2))
  /** How many cards beyond each end of the view a made card is taken apart, to be made again as it comes back: 0, by
    * default, keeps every card made once it is. */
  val releaseBeyond: Var[Int] = own(Var(0))
  /** Told, with how many items there are, as the cards made reach the last: once for each count. */
  val nearEnd: Channel[Int] = Channel[Int]

  private var shown: Vector[T] = Vector.empty
  private var indexes: Map[String, Int] = Map.empty
  private var made: Map[String, Component] = Map.empty
  private val railLength: Var[Double] = own(Var(0.0))
  private val tallest: Var[Double] = own(Var(0.0))
  private var toldNear = -1
  private var asked = false

  private def stride: Double = cardWidth + gap

  content.size.width := railLength()
  size.height := tallest() + content.padding() * 2.0 + scrollbarRoom()
  content.layout @= Some(new LazyLayout(() => place(), (from: Component, key: Key) => beside(from, key), false))
  items.attach(_ => reread())
  offset.attach(_ => fill())
  size.width.attach(_ => fill())
  ahead.attach(_ => fill())
  releaseBeyond.attach(_ => fill())
  content.padding.attach(_ => lengthen())
  follow(Direction.rtl, (_: Boolean) => content.requestLayout())
  reread()

  /** How many cards are made now. */
  def madeCount: Int = made.size
  /** The card at `index`, where it is made. */
  def cardAt(index: Int): Option[Component] =
    if (index < 0 || index >= shown.length) None else made.get(keyOf(shown(index)))
  /** The card at `index`, made now where it is not yet (to put the focus on it): None past the items. */
  def make(index: Int): Option[Component] = if (index < 0 || index >= shown.length) None else Some(build(index))
  /** Where the item of `key` is, -1 where none is. */
  def indexOf(key: String): Int = indexes.getOrElse(key, -1)

  private def lengthen(): Unit = {
    val n = shown.length
    railLength @= (if (n == 0) 0.0 else n * cardWidth + (n - 1) * gap) + content.padding() * 2.0
  }

  // the items read again: what left is taken apart, what stays keeps its card
  private def reread(): Unit = {
    shown = items()
    var next = Map.empty[String, Int]
    var i = 0
    while (i < shown.length) {
      next = next.updated(keyOf(shown(i)), i)
      i += 1
    }
    indexes = next
    var gone: List[Component] = Nil
    made.foreach { case (k, c) => if (!next.contains(k)) gone = c :: gone }
    made = made.filter { case (k, _) => next.contains(k) }
    lengthen()
    order()
    gone.foreach(_.dispose())
    content.requestLayout()
    fill()
  }

  // the made cards as the content's children, in the items' order (the order they read in, and Tab moves through)
  private def order(): Unit = {
    val now = made.toVector.map { case (k, c) => (indexes(k), c) }.sortBy(_._1).map(_._2)
    val was = content.children()
    if (now.length != was.length || !now.indices.forall(i => now(i) eq was(i))) content.children @= now
  }

  // where card `index` starts from the content's left: from its right, right to left
  private def leftOf(index: Int): Double = {
    val from = content.padding() + index * stride
    if (Direction.rtl()) content.size.width() - from - cardWidth else from
  }

  // card `index` made, where it is not, at its place
  private def build(index: Int, alone: Boolean = true): Component = {
    val key = keyOf(shown(index))
    made.get(key) match {
      case Some(c) => c
      case None =>
        val c = card(shown(index))
        c.position.left @= leftOf(index)
        c.position.top @= content.padding()
        made = made.updated(key, c)
        if (alone) order()
        c
    }
  }

  // the made cards placed, and the rail as tall as the tallest
  private def place(): Unit = {
    var tall = 0.0
    made.foreach { case (k, c) =>
      c.position.left @= leftOf(indexes(k))
      c.position.top @= content.padding()
      if (c.size.height() > tall) tall = c.size.height()
    }
    if (tall != tallest()) tallest @= tall
    if (!asked) {
      asked = true
      Platform.afterTurn(() => {
        asked = false
        fill()
      })
    }
  }

  // the cards in the view and `ahead` beyond made, those past `releaseBeyond` let go, and the app told near the end
  // (none before it has a width: it is made as it is given one)
  private def fill(): Unit = if (!takenApart && shown.nonEmpty && size.width() > 0.0) {
    val along = size.width()
    val pad = content.padding()
    val more = Quality.overscan(ahead())
    val first = math.max(0, ((offset() - pad) / stride).toInt - more)
    val last = math.min(shown.length - 1, ((offset() + along - pad) / stride).toInt + more)
    var changed = false
    var i = first
    while (i <= last) {
      if (!made.contains(keyOf(shown(i)))) {
        build(i, false)
        changed = true
      }
      i += 1
    }
    var gone: List[Component] = Nil
    if (releaseBeyond() > 0) {
      val from = first - releaseBeyond()
      val to = last + releaseBeyond()
      val kept = LazyColumn.focusIn(content)
      made.foreach { case (k, c) =>
        val j = indexes(k)
        if ((j < from || j > to) && !kept.exists(_ eq c)) gone = c :: gone
      }
      if (gone.nonEmpty) {
        made = made.filter { case (_, c) => !gone.exists(_ eq c) }
        changed = true
      }
    }
    // (the made and let go put in at once, in one layout of them all)
    if (changed) order()
    gone.foreach(_.dispose())
    if (last >= shown.length - 1 && toldNear != shown.length) {
      toldNear = shown.length
      nearEnd @= shown.length
    }
  }

  /** Left or Right from the card `from`, as the rail reads: the card beside it, made first where it is not. */
  private def beside(from: Component, key: Key): Option[Component] =
    if (key != Key.ArrowLeft && key != Key.ArrowRight) None
    else {
      var i = -1
      made.foreach { case (k, c) => if (c eq from) i = indexes(k) }
      val j = i + Neighbours.forward(key)
      if (i < 0 || j < 0 || j >= shown.length) None else Some(build(j))
    }
}
