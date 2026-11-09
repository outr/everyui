package everyui.widget

import everyui.font.MaterialIcon

/** What a home-screen widget shows: words, a picture, rows and columns of them, a button, a scrolling list. It is not
  * a `Component` tree: a launcher draws a widget in its own process, from what the platform lets an app describe
  * (RemoteViews on Android, SwiftUI in a WidgetKit extension), so only what both can show is here. Each part's
  * `label` is what a screen reader says for it where the words alone would not do.
  *
  * A list (`Items`) belongs in a column, one to a widget; Android before 12 shows its first rows without scrolling. A
  * tap in one of its rows opens the app (`WidgetTap.Open`); an `Action` there does nothing. */
enum WidgetNode {
  /** Words in a style and a colour, `lines` at most (0 for any number), cut short with an ellipsis past them. */
  case Text(words: String, style: WidgetText = WidgetText.Body, colour: WidgetColour = WidgetColour.Text,
            lines: Int = 1, label: String = "")
  /** A picture `size` points square. */
  case Picture(source: WidgetPicture, size: Double, label: String = "")
  /** Parts side by side, `gap` points apart, on a tinted card where it has a `ground`. */
  case Row(parts: Vector[WidgetNode], gap: Double = 8.0, tap: Option[WidgetTap] = None,
           ground: Option[WidgetGround] = None)
  /** Parts one above another, `gap` points apart, on a tinted card where it has a `ground`. */
  case Column(parts: Vector[WidgetNode], gap: Double = 4.0, tap: Option[WidgetTap] = None,
              ground: Option[WidgetGround] = None)
  /** A button in the accent, with its words and an icon before them. */
  case Button(label: String, icon: Option[MaterialIcon], tap: WidgetTap)
  /** A scrolling list of rows, filling what its column leaves; `empty` is said where it has none. */
  case Items(rows: Vector[WidgetNode], empty: String)
  /** The room left in its row or column, pushing what follows it to the far end. */
  case Spacer
  /** `node` with the size of every `Text` in it (a list's rows' too, which grow with their words) times `scale`,
    * held to `WidgetNode.leastScale`..`mostScale`; a `Scaled` within it multiplies again, the product held the
    * same. A widget's text size, chosen for it in its configure screen (`WidgetSetting`), usually wraps its whole
    * tree. Pictures, gaps and a button's words keep their size. */
  case Scaled(scale: Double, node: WidgetNode)
}

object WidgetNode {
  /** The least and the most a `Scaled` multiplies its words' size by. */
  val leastScale: Double = 0.5
  val mostScale: Double = 2.0

  /** `scale` held to `leastScale`..`mostScale`, and 1 where it is not a number. */
  def scale(scale: Double): Double =
    if (scale.isNaN) 1.0 else math.max(leastScale, math.min(mostScale, scale))
}
