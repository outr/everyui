package everyui.table

import everyui.component.types.TextDirection

/** How a `DataTable` sets its cells: their size in points, the padding around each cell's words, whether the words are
  * quieter (the muted colour, as in a Markdown quote), and which way they run (`TextDirection.Auto` for words the app
  * did not write). */
case class DataTableStyle(points: Double = 14.0, cellPadding: Double = 8.0, quiet: Boolean = false,
                          direction: TextDirection = TextDirection.Inherit)
