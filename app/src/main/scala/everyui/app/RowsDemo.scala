package everyui.app

import everyui.component.{Column, Component, Container, Row, TextView}
import everyui.render.Platform
import everyui.ui

/** A table of `n=<count>` rows (200 when not given), four words to a row, then a hundred more rows added one at a
  * time: how long each takes to be laid out, with its sizes read, measured from the start to the turn it settles. It
  * prints (tools/check.sh, or a browser's console):
  *
  *   [ui] rows 200 built in 41.0 ms
  *   [ui] rows 100 appended in 12.0 ms
  */
object RowsDemo {
  def run(): Unit = {
    val asked = Platform.launchArg("n")
    val n = if (asked.isEmpty) 200 else asked.toInt
    val column = Column(2.0)()
    ui.children += column
    val start = Platform.ticks()
    column.children ++= (0 until n).map(row)
    settled(column, n, () => {
      Platform.log("rows " + n + " built in " + elapsed(start) + " ms")
      val more = Platform.ticks()
      (n until n + 100).foreach(i => column.children += row(i))
      settled(column, n + 100, () => Platform.log("rows 100 appended in " + elapsed(more) + " ms"))
    })
  }

  private def row(i: Int): Container =
    Row(12.0, middle = true)(words("#" + i), words("pipeline-" + (i % 17)), words(if (i % 3 == 0) "failed" else "done"),
      words((i * 37 % 1000) + " records"))

  private def words(s: String): TextView = {
    val t = new TextView
    t.content @= s
    t
  }

  private def elapsed(since: Double): String = (math.round((Platform.ticks() - since) * 10.0) / 10.0).toString

  /** `next` once every row has its size and the column is as tall as they are. */
  private def settled(column: Container, count: Int, next: () => Unit): Unit = {
    val rows = column.children()
    val h = if (rows.isEmpty) 0.0 else rows.head.size.height()
    val done = rows.length == count && h > 0.0 && rows.last.size.height() == h
    if (done && column.size.height() == count * (h + 2.0) - 2.0) next()
    else Platform.after(0.0, () => settled(column, count, next))
  }
}
