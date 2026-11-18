package everyui.chart

import everyui.Color
import everyui.theme.Theme

/** The colour a series, a slice or a cell is drawn in, from the palette, so it follows the theme, light or dark:
  *  - `Auto`: the palette's series colour for its place among the chart's series (`Theme.series`);
  *  - `Slot(n)`: series colour `n` (0 to 7) wherever it stands, so a series keeps its colour as others come and go;
  *  - `Muted`: the de-emphasis grey, for what is only context beside the one series that matters, or "Other";
  *  - `Accent`, `Success`, `Warning`, `Danger`: the palette's roles, only where the colour means that (an error rate
  *    in danger), never only to tell series apart;
  *  - `Fixed(colour)`: a colour of the app's own (a brand's). */
enum ChartTone {
  case Auto
  case Slot(n: Int)
  case Muted
  case Accent
  case Success
  case Warning
  case Danger
  case Fixed(colour: Color)

  /** The colour for the series in place `i`. */
  def colour(i: Int): Color = this match {
    case Auto => Theme.series(i)
    case Slot(n) => Theme.series(n)
    case Muted => Theme.muted().blend(Theme.surface(), 0.45)
    case Accent => Theme.accent()
    case Success => Theme.success()
    case Warning => Theme.warning()
    case Danger => Theme.danger()
    case Fixed(c) => c
  }
}
