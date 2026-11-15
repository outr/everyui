package everyui.table

import everyui.component.{Component, Container, RichText}
import everyui.component.types.WhiteSpace
import everyui.focus.Focus
import everyui.text.TextRun
import everyui.{FxSpec, ui}

/** A `DataTable`'s cells measured for its columns' widths and then let go, only what they read kept: its columns as
  * wide as their widest cell on one line, a short table's every cell and a long one's samples alike, and measured
  * again as its cells change, with no measuring cell left once read. */
class DataTableProbesSpec extends FxSpec {
  private def onScreen[T](view: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      view.size.width @= 900.0
      ui.children += view
    }
    try test finally fx {
      ui.children.clear()
      view.dispose()
      Focus.focused @= None
    }
  }

  // the table's measuring cells, out of sight: its first child
  private def measuring(t: DataTable): Int = t.children().head.asInstanceOf[Container].children().size

  private def table(rows: Int, phrase: String): DataTable = new DataTable(Vector(DataColumn("Name"),
    DataColumn("Kind")), (0 until rows).toVector.map(i => Vector(DataCell("row " + i), DataCell(if (i == 1) phrase
    else "short"))))

  // how wide `words` are on one line in the table's cells
  private def onOneLine(t: DataTable, words: String): Double = {
    val probe = fx(new RichText {
      whiteSpace @= WhiteSpace.NoWrap
      font.size @= t.style.points
      runs @= List(TextRun(words))
    })
    fx(ui.children += probe)
    eventually((probe.size.width()) > 0.0) shouldBe true
    val w = fx(probe.size.width())
    fx {
      ui.children -= probe
      probe.dispose()
    }
    w
  }

  "A table's measuring cells" should {
    Vector(12, 200).foreach { rows =>
      ("be let go once read, its columns as wide as their widest cell, with " + rows + " rows") in {
        val phrase = "a rather longer phrase than the rest"
        val t = fx(table(rows, phrase))
        onScreen(t) {
          eventually((t.size.height()) > 0.0 && measuring(t) == 0) shouldBe true
          val wide = onOneLine(t, phrase)
          val column = fx(t.columnWidth(1))
          column should be >= math.ceil(wide)
          column should be < math.ceil(wide) + 40.0
          // a longer phrase in its place: measured again, wider, and let go again
          val longer = phrase + " and then some"
          fx(t.cells @= t.cells().updated(1, Vector(DataCell("row 1"), DataCell(longer))))
          eventually((t.columnWidth(1)) > column + 20.0 && measuring(t) == 0) shouldBe true
          fx(t.columnWidth(1)) should be >= math.ceil(onOneLine(t, longer))
        }
      }
    }
  }
}
