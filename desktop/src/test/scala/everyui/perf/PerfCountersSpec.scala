package everyui.perf

import everyui.component.*
import everyui.layout.VerticalLayout
import everyui.{FxSpec, ui}
import reactify.*

/** The counters count nothing while off, and while on: flushes and containers placed (again in a frame too), fits
  * with the unchanged among them, and a list's children made against kept. */
class PerfCountersSpec extends FxSpec {
  private def count(name: String): Int = PerfCounters.counts.find(_._1 == name).map(_._2).getOrElse(-1)

  private def settle(): Unit = (1 to 4).foreach(_ => fx(()))

  "The counters" should {
    "count nothing while off" in {
      fx {
        PerfCounters.on = false
        PerfCounters.reset()
        val c = new Container
        c.layout @= Some(VerticalLayout())
        c.children += TextView("one")
        ui.children += c
      }
      settle()
      fx(PerfCounters.counts.forall(_._2 == 0)) shouldBe true
      fx(ui.children.clear())
    }
    "count flushes, containers placed, fits and a list's children made and kept" in {
      val items = Var(List("a", "b"))
      val (column, words) = fx {
        PerfCounters.on = true
        PerfCounters.reset()
        val w = TextView("hello")
        val c = new Container
        c.layout @= Some(VerticalLayout())
        c.children += w
        val list = new Container
        list.layout @= Some(VerticalLayout())
        list.children.bindMaking(items())(s => TextView(s))
        c.children += list
        ui.children += c
        (c, w)
      }
      try {
        settle()
        fx(count("flushes")) should be > 0
        fx(count("placed")) should be > 0
        fx(PerfCounters.report) should include ("Container")
        fx(count("listMade")) shouldBe 2
        fx(items @= List("a", "b", "c"))
        fx(count("listMade")) shouldBe 3
        fx(count("listKept")) shouldBe 2
        val fits = fx(count("fits"))
        fx(words.content @= "hello again")
        fx(count("fits")) shouldBe fits + 1
        fx(PerfCounters.json) should startWith ("{\"frames\":")
        fx(PerfCounters.reset())
        fx(count("fits")) shouldBe 0
      } finally fx {
        PerfCounters.on = false
        PerfCounters.reset()
        ui.children.clear()
      }
    }
  }
}
