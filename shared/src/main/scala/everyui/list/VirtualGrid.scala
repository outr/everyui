package everyui.list

import everyui.component.{Component, Container}
import everyui.event.Key
import everyui.focus.Focus
import everyui.layout.HorizontalLayout
import reactify.*

/** A grid of cells of any number, made a row at a time as the rows come near the view (a `LazyColumn` of rows): a
  * search's results, a service's whole catalogue. A row holds as many cells as `columns` says for the grid's width (a
  * fixed count, or as many as fit at a least width), each `cell` made at the width they share, `spacing` apart across
  * and down. The rows are taken to be `estimate` of the cells' width tall until one is made, and as tall as the last
  * made one from then on. Give the grid a width; it is as tall as its rows.
  *
  * The arrows move the focus along a row and from row to row, Up and Down to the same place in the next row (its last,
  * where that row is shorter), making it first where it is not made yet. `nearEnd` asks for more as the end comes near.
  *
  * {{{
  *   val grid = new VirtualGrid(results, (t: Title) => t.id, GridColumns.adaptive(110.0), 12.0)((t, w) => new PosterCard(t, w))
  *   grid.size.width := page.size.width()
  *   grid.nearEnd.attach(_ => more())
  * }}} */
class VirtualGrid[T](val cells: Var[Vector[T]], val keyOf: T => String, val columns: GridColumns,
                     spacing: Double = 16.0)(cell: (T, Double) => Component) extends LazyColumn(spacing) {
  override def kind: String = "VirtualGrid"

  /** How tall a row of cells `width` wide is taken to be until a row is made: a poster's half again its width. */
  var estimate: Double => Double = (w: Double) => w * 1.5

  private var across = 1
  private var cellWidth = 0.0
  private var typical = 0.0

  cells.attach(_ => regroup())
  size.width.attach(_ => regroup())
  regroup()

  /** How many cells a row holds now. */
  def perRow: Int = across

  /** The cell at `index`, where its row is made. */
  def cellAt(index: Int): Option[Component] = rowAt(index / across).flatMap(r => cellIn(r, index % across))

  /** The cell at `index`, its row made now where it is not yet (to put the focus on it). */
  def makeCell(index: Int): Option[Component] = make(index / across).flatMap(r => cellIn(r, index % across))

  private def cellIn(row: Component, at: Int): Option[Component] = row match {
    case r: Container if at >= 0 && at < r.children().length => Some(r.children()(at))
    case _ => None
  }

  // the cells grouped into rows for the width now: a row whose cells and width are as they were keeps what it made
  private def regroup(): Unit = {
    val width = size.width()
    if (width <= 0.0) items @= Vector.empty
    else {
      across = columns.across(width, gap)
      cellWidth = columns.cellWidth(width, gap)
      val all = cells()
      val w = cellWidth
      items @= Vector.tabulate((all.length + across - 1) / across) { r =>
        val part = all.slice(r * across, math.min(all.length, (r + 1) * across))
        var key = across.toString + "/" + math.round(w).toString
        part.foreach(t => key = key + "|" + keyOf(t))
        LazyItem(key, estimate(w), () => rowOf(part, w))
      }
    }
  }

  private def rowOf(part: Vector[T], width: Double): Component = {
    val row = new Container
    row.size.width := size.width()
    row.layout @= Some(HorizontalLayout(gap))
    row.children @= part.map(t => cell(t, width))
    row
  }

  override protected def guess(index: Int): Double = if (typical > 0.0) typical else super.guess(index)

  override protected def measured(index: Int, height: Double): Unit = typical = height

  /** Up or Down: the next row's cell at the place the focus is at in its own, or its last. */
  override protected def beside(from: Component, key: Key): Option[Component] = super.beside(from, key).map { next =>
    var place = -1
    from match {
      case row: Container =>
        Focus.focused().foreach { f =>
          var at: Component = f
          while (at.parent().exists(p => !(p eq row))) at = at.parent().get
          place = row.children().indexWhere(_ eq at)
        }
      case _ => ()
    }
    next match {
      case r: Container if place >= 0 && r.children().nonEmpty => r.children()(math.min(place, r.children().length - 1))
      case _ => next
    }
  }
}
