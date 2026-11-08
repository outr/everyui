package everyui.widget

import everyui.font.MaterialIcon

/** What a home-screen widget shows: words, a picture, rows and columns of them, a button, a scrolling list. It is not
  * a `Component` tree: a launcher draws a widget in its own process, from what the platform lets an app describe
  * (RemoteViews on Android, SwiftUI in a WidgetKit extension), so only what both can show is here. Each part's
  * `label` is what a screen reader says for it where the words alone would not do.
  *
  * A list (`Items`) belongs in a column; Android before 12 shows its first rows without scrolling. A tap in one of its
  * rows opens the app (`WidgetTap.Open`); an `Action` there does nothing. */
enum WidgetNode {
  /** Words in a style and a colour, `lines` at most (0 for any number), cut short with an ellipsis past them. */
  case Text(words: String, style: WidgetText = WidgetText.Body, colour: WidgetColour = WidgetColour.Text,
            lines: Int = 1, label: String = "")
  /** A picture `size` points square. */
  case Picture(source: WidgetPicture, size: Double, label: String = "")
  /** Parts side by side, `gap` points apart. */
  case Row(parts: Vector[WidgetNode], gap: Double = 8.0, tap: Option[WidgetTap] = None)
  /** Parts one above another, `gap` points apart. */
  case Column(parts: Vector[WidgetNode], gap: Double = 4.0, tap: Option[WidgetTap] = None)
  /** A button in the accent, with its words and an icon before them. */
  case Button(label: String, icon: Option[MaterialIcon], tap: WidgetTap)
  /** A scrolling list of rows, filling what its column leaves; `empty` is said where it has none. */
  case Items(rows: Vector[WidgetNode], empty: String)
  /** The room left in its row or column, pushing what follows it to the far end. */
  case Spacer
}
