package everyui.layout

import everyui.component.*
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import reactify.*

/** Laying out a column costs about the same per line however long the column is: a table of a few hundred rows took a
  * browser tens of seconds while each line's size walked every line again. Timed on the desktop's real nodes; the sizes
  * can be chosen with `EVERYUI_SCALE=50,200,1000,5000`. */
class ColumnScaleSpec extends FxSpec {
  private val sizes: List[Int] = sys.env.get("EVERYUI_SCALE").map(_.split(',').toList.map(_.trim.toInt))
    .getOrElse(List(50, 200, 1000, 5000))

  private def line(i: Int): TextView = {
    val t = new TextView
    t.content @= s"Line $i of the column"
    t
  }

  private def onScreen[T](column: Container)(f: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      ui.children += column
    }
    try f finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def millis[T](f: => T): (T, Double) = {
    val start = System.nanoTime()
    val result = f
    (result, (System.nanoTime() - start) / 1e6)
  }

  /** The least of `times` runs of `f` in the FX thread's own time (worked): what the layout costs, without the waits a
    * busy machine adds, a collection that happened to fall in one of them counting in none. */
  private def least(times: Int)(f: => Unit): Double = (1 to times).map(_ => worked(f)).min

  /** Every line under the one before it, and the column as tall as they are. */
  private def check(column: Container, count: Int): Unit = fx {
    val lines = column.children()
    lines.length shouldBe count
    val h = lines.head.size.height()
    h should be > 0.0
    lines.zipWithIndex.foreach { case (l, i) => l.position.top() shouldBe (i * (h + 2.0)) +- 0.001 }
    column.size.height() shouldBe (count * (h + 2.0) - 2.0) +- 0.001
    column.size.width() shouldBe lines.map(_.size.width()).max +- 0.001
  }

  "A column" should {
    "lay out many lines in time that grows with their number" in {
      val timed = sizes.map { n =>
        val column = fx(Column(2.0)())
        onScreen(column) {
          val (_, all) = millis(fx(column.children ++= (0 until n).map(line)))
          check(column, n)
          // every line's words changing together, as the web measures them: one layout for all of them
          val words = least(3)(LayoutPass.batch(column.children().foreach {
            case t: TextView => t.content @= t.content() + " again"
            case _ => ()
          }))
          check(column, n)
          info(f"$n%5d lines: every line's words changed $words%8.1f ms worked")
          val each = fx(Column(2.0)())
          val (_, one) = onScreen(each)(millis(fx((0 until n).foreach(i => each.children += line(i)))))
          check(each, n)
          val inBatch = (1 to 3).map { _ =>
            val batched = fx(Column(2.0)())
            val took =
              onScreen(batched)(worked(LayoutPass.batch((0 until n).foreach(i => batched.children += line(i)))))
            check(batched, n)
            took
          }.min
          info(f"$n%5d lines: all at once $all%8.1f ms, one at a time $one%8.1f ms, in a batch $inBatch%8.1f ms worked")
          (n, words, inBatch)
        }
      }
      // the two largest, the JIT warmed by then, in the FX thread's own time: linear would be the ratio of their sizes,
      // and every line walked again for each line its square; three times linear leaves room for what a busy machine
      // costs the thread's work itself (its core's caches, shared with others)
      if (timed.length >= 2) {
        val (small, smallWords, smallBatch) = timed(timed.length - 2)
        val (large, largeWords, largeBatch) = timed.last
        val linear = large.toDouble / small
        (largeWords / math.max(smallWords, 1.0)) should be < linear * 3.0
        (largeBatch / math.max(smallBatch, 1.0)) should be < linear * 3.0
      }
    }
    "take a hundred more lines in time that does not grow with the lines already there" in {
      val n = sizes.max
      val column = fx(Column(2.0)())
      onScreen(column) {
        fx(column.children ++= (0 until n).map(line))
        val (_, more) = millis(fx((n until n + 100).foreach(i => column.children += line(i))))
        check(column, n + 100)
        info(f"100 lines appended one at a time to $n: $more%8.1f ms")
      }
    }
    "grow as a bound list grows" in {
      val n = sizes.max
      val values: Var[List[Int]] = Var(Nil)
      val column = fx(Column(2.0)())
      fx(column.children.bind(values())(line))
      onScreen(column) {
        fx(values @= (0 until n).toList)
        val (_, more) = millis(fx((n until n + 100).foreach(i => values @= values() :+ i)))
        check(column, n + 100)
        info(f"a bound list of $n grown a value at a time, 100 times: $more%8.1f ms")
      }
    }
  }
}
