package everyui.table

import everyui.component.types.TextAlign

/** A table as text, a header line and then its rows: tab-separated for a spreadsheet's paste (`tsv`), comma-separated
  * for a file (`csv`), or a Markdown table (`markdown`). A file written a row at a time has each row's line alone
  * (`csvLine`, `markdownHead` and `markdownLine`). */
object TableText {
  /** Tab-separated, a line a row. A cell holding a tab, a line break or a leading quote is quoted, its quotes doubled,
    * as a spreadsheet reads a pasted cell of several lines; any other cell is as it is. */
  def tsv(header: Vector[String], rows: Vector[Vector[String]]): String =
    (header +: rows).map(_.map(tsvCell).mkString("\t")).mkString("\n")

  private def tsvCell(text: String): String =
    if (text.indexOf('\t') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0 || text.startsWith("\""))
      "\"" + text.replace("\"", "\"\"") + "\""
    else text

  /** Comma-separated as RFC 4180 has it: a line a row, each ended by CR LF; a cell holding a comma, a quote, a CR or an
    * LF quoted, its quotes doubled. */
  def csv(header: Vector[String], rows: Vector[Vector[String]]): String = (header +: rows).map(csvLine).mkString

  /** One row of `csv`, its CR LF after it. */
  def csvLine(cells: Vector[String]): String = cells.map(csvCell).mkString(",") + "\r\n"

  private def csvCell(text: String): String =
    if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0)
      "\"" + text.replace("\"", "\"\"") + "\""
    else text

  /** A Markdown (GFM) table, its columns aligned as `align` says: a cell's bars escaped and its line breaks `<br>`. */
  def markdown(header: Vector[String], rows: Vector[Vector[String]], align: Vector[TextAlign]): String =
    markdownHead(header, align) + rows.map(markdownLine).mkString

  /** The header of `markdown` and the rule under it, each line ended. */
  def markdownHead(header: Vector[String], align: Vector[TextAlign]): String = {
    val rule = header.indices.map { c =>
      align.lift(c).getOrElse(TextAlign.Start) match {
        case TextAlign.Center => ":---:"
        case TextAlign.End => "---:"
        case _ => "---"
      }
    }.mkString("|", "|", "|")
    markdownLine(header) + rule + "\n"
  }

  /** One row of `markdown`, its line ended. */
  def markdownLine(cells: Vector[String]): String = cells.map(c => " " + mdCell(c) + " ").mkString("|", "|", "|") + "\n"

  private def mdCell(text: String): String =
    text.replace("\\", "\\\\").replace("|", "\\|").replace("\r\n", "<br>").replace("\n", "<br>").replace("\r", "<br>")
}
