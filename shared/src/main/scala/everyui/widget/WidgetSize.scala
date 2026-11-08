package everyui.widget

/** A widget's size class, from the size the launcher gives it: compact (a cell or two square: an unread count, the
  * next event), medium (wide and short: a few rows) and large (a list). A kind may give one layout for several. */
enum WidgetSize {
  case Compact, Medium, Large
}

object WidgetSize {
  /** Under this many points wide, a widget is compact. */
  val compactBelow: Double = 200.0
  /** Under this many points tall, a widget wider than a compact one is medium; at it or over, large. */
  val mediumBelow: Double = 200.0

  /** The size class of a widget `width` by `height` points. */
  def of(width: Double, height: Double): WidgetSize =
    if (width < compactBelow) Compact
    else if (height < mediumBelow) Medium
    else Large

  /** `size` as a kind that draws only `sizes` has it: the largest of them not larger, or the smallest of them where
    * every one is larger. A kind with no sizes draws every size. */
  def within(size: WidgetSize, sizes: Set[WidgetSize]): WidgetSize =
    if (sizes.isEmpty || sizes.contains(size)) size
    else {
      val under = sizes.filter(_.ordinal < size.ordinal)
      if (under.nonEmpty) under.maxBy(_.ordinal) else sizes.minBy(_.ordinal)
    }
}
