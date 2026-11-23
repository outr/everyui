package everyui.chart

import everyui.Color
import everyui.draw.{Figure, Path}
import everyui.i18n.Direction
import everyui.spatial.Rect
import everyui.theme.Theme
import reactify.*

/** A grid of `rows` by `columns` (a week's days by the hours of a day), each cell as dark as its value is large, in
  * one hue from light to dark (`tone`, the first series colour by default; on a dark palette the other way round, low
  * values receding into the ground). The rows' names at the start, the columns' along the bottom thinned to fit, and
  * a scale at the bottom end from the least value to the most. A cell is a mark the pointer, a press and the keys find.
  *
  * {{{
  *   val busy = Heatmap(weekdays, (0 until 24).toVector.map(_.toString), meetingsByDayAndHour, ChartFormat.number(_))
  * }}} */
class Heatmap extends Chart {
  override def kind: String = "Heatmap"

  val rows: Var[Vector[String]] = own(Var(Vector.empty))
  val columns: Var[Vector[String]] = own(Var(Vector.empty))
  /** The values, a row at a time, each row a value for each column. */
  val values: Var[Vector[Vector[Double]]] = own(Var(Vector.empty))
  val tone: Var[ChartTone] = own(Var(ChartTone.Slot(0)))

  private val eased = new Eased(this)
  own(Val(rows().indices.toVector.map(r => columns().indices.toVector.map(c => Heatmap.at(values(), r, c)))))
    .attachAndFire(eased.to)

  private def rtl: Boolean = Direction.rtl()
  private def low: Double = eased.now().flatten.foldLeft(0.0)(math.min)
  private def high: Double = eased.now().flatten.foldLeft(0.0)(math.max)

  private val rowWords = new ChartLabels(words, 11.0, (i, v) => {
    v.position.left := (if (rtl) grid().right + 8.0 else grid().x - 8.0 - v.size.width())
    v.position.top := grid().y + cell().height * (i + 0.5) - v.size.height() / 2.0
  })
  private val columnWords = new ChartLabels(words, 11.0, (i, v) => {
    v.visible := plan().shows(i)
    v.position.left := {
      val g = grid()
      val across = cell().width * (i + 0.5)
      (if (rtl) g.right - across else g.x + across) - v.size.width() / 2.0
    }
    v.position.top := gridBottom + 6.0
  })
  // the scale: the least value and the most, either side of its steps
  private val scaleWords = new ChartLabels(words, 11.0, (i, v) => {
    v.position.top := strip().y + (strip().height - v.size.height()) / 2.0
    v.position.left := {
      val s = strip()
      if ((i == 0) != rtl) s.x - 6.0 - v.size.width() else s.right + 6.0
    }
  })

  /** How the columns' names are written so that none runs into the next. */
  lazy val plan: Val[LabelPlan] = own(Val {
    LabelPlan(math.max(1, math.ceil((columnWords.widest + LabelPlan.gap) / math.max(1e-9, cell().width)).toInt),
      rotated = false)
  })

  /** Where the cells are, inside the names and over the scale. */
  lazy val grid: Val[Rect] = own(Val {
    val side = rowWords.widest + 8.0
    val bottom = columnWords.lineHeight * 2.0 + 22.0
    val w = math.max(0.0, plotWidth - side)
    val h = math.max(0.0, plotHeight - bottom)
    if (rtl) Rect(0.0, 0.0, w, h) else Rect(side, 0.0, w, h)
  })
  /** A cell's size: square where there is room for it, never taller than wide. */
  lazy val cell: Val[Rect] = own(Val {
    val g = grid()
    val w = g.width / math.max(1, columns().size)
    Rect(0.0, 0.0, w, math.min(g.height / math.max(1, rows().size), math.max(w, 14.0)))
  })
  /** The scale's steps, at the bottom end. */
  private lazy val strip: Val[Rect] = own(Val {
    val g = grid()
    val w = math.min(120.0, g.width / 3.0)
    val y = g.y + cell().height * rows().size + 6.0 + columnWords.lineHeight + 8.0 +
      (columnWords.lineHeight - 10.0) / 2.0
    val gutter = scaleWords.widths.lastOption.getOrElse(0.0) + 6.0
    if (rtl) Rect(g.x + gutter, y, w, 10.0) else Rect(g.right - gutter - w, y, w, 10.0)
  })

  /** Where the cells end, down the grid. */
  private def gridBottom: Double = grid().y + cell().height * rows().size

  protected def marks: Vector[ChartMark] = {
    val g = grid()
    val c = cell()
    val all = eased.now()
    all.indices.toVector.flatMap(r => all(r).indices.map { k =>
      val x = if (rtl) g.right - c.width * (k + 1) else g.x + c.width * k
      ChartMark(k, r, all(r)(k), x, g.y + c.height * r, c.width, c.height)
    })
  }
  protected def pointedOf(m: ChartMark, pressed: Boolean): Pointed =
    Pointed(columns().lift(m.category).getOrElse(""), rows().lift(m.series).getOrElse(""),
      Heatmap.at(values(), m.series, m.category), m.category, m.series, pressed)
  protected def tipOf(p: Pointed): (String, Vector[(Color, String, String)]) =
    (p.series + " · " + p.category, Vector((Heatmap.colour(tone().colour(0), share(p.value)), format()(p.value), "")))
  protected def kindWords: String = ChartWords.heatmap
  protected def pressWords: String = ChartWords.pressCell
  // a row of the table is a row of cells
  override protected def rowMarks(row: Int): Vector[ChartMark] = marks.filter(_.series == row).sortBy(_.category)
  protected def tableNow: ChartTable = ChartTable("" +: columns(),
    rows().indices.toVector.map(r => rows()(r) +: columns().indices.toVector.map(c => format()(Heatmap.at(values(), r, c)))))
  protected def described: String = {
    val all = values().flatten
    if (all.isEmpty) kindWords + ". " + ChartWords.noData
    else {
      var best = (0, 0)
      rows().indices.foreach(r => columns().indices.foreach(c =>
        if (Heatmap.at(values(), r, c) > Heatmap.at(values(), best._1, best._2)) best = (r, c)))
      kindWords + ". " + ChartWords.range(all.size, format()(all.min), format()(all.max)) + "; " +
        ChartWords.highest(rows().lift(best._1).getOrElse("") + " " + columns().lift(best._2).getOrElse(""),
          format()(Heatmap.at(values(), best._1, best._2))) + "."
    }
  }

  private def share(v: Double): Double = if (high > low) (v - low) / (high - low) else 0.0

  own(Val(rows())).attachAndFire(rowWords.set)
  own(Val(columns())).attachAndFire(columnWords.set)
  own(Val(Vector(format()(low), format()(high)))).attachAndFire(scaleWords.set)

  plot.figures := {
    val base = tone().colour(0)
    val gap = 2.0
    val cells = marks.toList.map { m =>
      Figure(Path.rect(m.x + gap / 2.0, m.y + gap / 2.0, math.max(0.0, m.width - gap),
        math.max(0.0, m.height - gap)), fill = Some(Heatmap.colour(base, share(m.value))))
    }
    val lit = hovered().toList.flatMap(h => marks.find(m => m.category == h.categoryIndex && m.series == h.seriesIndex))
      .map(m => Figure(Path.rect(m.x + 0.5, m.y + 0.5, m.width - 1.0, m.height - 1.0), stroke = Some(Theme.text()),
        width = 1.5))
    val s = strip()
    val steps = (0 until 5).toList.map { i =>
      val w = s.width / 5.0
      val x = if (rtl) s.right - w * (i + 1) else s.x + w * i
      Figure(Path.rect(x, s.y, w - 1.0, s.height), fill = Some(Heatmap.colour(base, i / 4.0)))
    }
    if (gridBottom <= 0.0) Nil else cells ++ steps ++ lit
  }
  ready()
}

object Heatmap {
  def apply(rows: => Vector[String], columns: => Vector[String], values: => Vector[Vector[Double]],
            format: Double => String = ChartFormat.compact): Heatmap = {
    val map = new Heatmap
    map.rows := rows
    map.columns := columns
    map.values := values
    map.format @= format
    map
  }

  /** Row `r`'s value at column `c`, 0 where there is none. */
  def at(values: Vector[Vector[Double]], r: Int, c: Int): Double =
    if (r >= 0 && r < values.size && c >= 0 && c < values(r).size) values(r)(c) else 0.0

  /** The colour a cell of `t` of the way from the least value to the most is drawn in: one hue, `base`'s, from nearly
    * the ground to deeper than `base` itself (to lighter, on a dark palette, where the ground is dark). */
  def colour(base: Color, t: Double): Color = {
    val x = math.max(0.0, math.min(1.0, t))
    val near = base.blend(Theme.surface(), 0.86)
    val far = base.blend(Theme.text(), 0.4)
    if (x <= 0.6) near.blend(base, x / 0.6) else base.blend(far, (x - 0.6) / 0.4)
  }
}
