package everyui.table

import everyui.component.types.TextDirection
import everyui.markdown.TableLook

/** How a `DataTable` sets its cells: their size in points, how the table is drawn (`look`: its header band, stripes,
  * corners and the room round each cell's words), whether the words are quieter (the muted colour, as in a Markdown
  * quote), and which way they run (`TextDirection.Auto` for words the app did not write). */
case class DataTableStyle(points: Double = 14.0, look: TableLook = TableLook(), quiet: Boolean = false,
                          direction: TextDirection = TextDirection.Inherit)
