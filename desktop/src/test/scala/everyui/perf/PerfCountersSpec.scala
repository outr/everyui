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

  "A container placed again" should {
    "be counted by what asked for it: its children, its size" in {
      val column = fx {
        PerfCounters.on = true
        val c = new Container
        c.layout @= Some(VerticalLayout())
        c.children += TextView("one")
        ui.children += c
        c
      }
      try {
        settle()
        // a frame of its own: the column last placed in one before it, which has closed
        val before = fx(PerfCounters.frame)
        eventually {
          PerfCounters.flushed()
          PerfCounters.frame > before
        } shouldBe true
        fx(PerfCounters.reset())
        // in one frame: placed for a child added, then again for a new width, then again for another child
        fx {
          column.children += TextView("two")
          column.size.width @= 300.0
          column.children += TextView("three")
        }
        val json = fx(PerfCounters.json)
        json should include ("\"placedAgainFor\":{")
        fx(PerfCounters.report) should include regex "placed again for: (.*, )?size 1"
        fx(PerfCounters.report) should include regex "placed again for: (.*, )?children 1"
      } finally fx {
        PerfCounters.on = false
        PerfCounters.reset()
        ui.children.clear()
      }
    }
  }

  "Words written" should {
    "be counted by what they were written for, the unchanged among them" in {
      fx {
        PerfCounters.on = true
        PerfCounters.reset()
        PerfCounters.textFor = "box"
        PerfCounters.wroteText(same = true)
        PerfCounters.wroteText(same = false)
        PerfCounters.textFor = ""
        PerfCounters.wroteText(same = true)
      }
      try {
        fx(PerfCounters.report) should include ("words written for: box 2, other 1")
        fx(PerfCounters.report) should include regex "words written unchanged for: (box 1, other 1|other 1, box 1)"
        fx(PerfCounters.json) should include ("\"textUnchangedFor\":{")
        fx(count("writesUnchanged")) shouldBe 2
      } finally fx {
        PerfCounters.on = false
        PerfCounters.reset()
      }
    }
  }

  "A fit" should {
    "do nothing where all it goes by is as it was, and cut again where the face changed" in {
      val label = fx {
        PerfCounters.on = true
        PerfCounters.reset()
        val t = TextView("A title long enough to be cut short at eighty")
        t.maxWidth @= 80.0
        t.textOverflow @= everyui.component.types.TextOverflow.Ellipsis
        ui.children += t
        t
      }
      try {
        settle()
        val (fits, same) = fx((count("fits"), count("fitsUnchanged")))
        // measured again from outside (a container's turn), the words and their limit as they were
        fx(label.measure.trigger())
        fx((count("fits"), count("fitsUnchanged"))) shouldBe (fits + 1, same + 1)
        val cut = fx(label.node.getText())
        // another size: cut again, more of the words fitting
        fx(label.font.size @= 10.0)
        fx(count("fitsUnchanged")) shouldBe same + 1
        fx(label.node.getText().length) should be > cut.length
      } finally fx {
        PerfCounters.on = false
        PerfCounters.reset()
        ui.children.clear()
      }
    }
  }
  "Reads after a write" should {
    "be counted by the first write since the page was laid out, and only once for it" in {
      val after = fx {
        PerfCounters.on = true
        PerfCounters.reset()
        PerfCounters.wrote(same = false, "width off the page")
        PerfCounters.wrote(same = false, "color")
        PerfCounters.read()
        PerfCounters.read()
        PerfCounters.wrote(same = true, "left")
        PerfCounters.read()
        PerfCounters.wroteText(same = false, "text")
        PerfCounters.read()
        val json = PerfCounters.json
        PerfCounters.on = false
        json
      }
      after should include("\"readsAfterAWrite\":2")
      val of = after.substring(after.indexOf("\"readsAfterAWriteOf\""))
      of should include("\"width off the page\":1")
      of should include("\"text\":1")
      of should not include "color"
    }
  }
}
