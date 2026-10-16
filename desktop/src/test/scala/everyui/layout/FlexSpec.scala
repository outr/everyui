package everyui.layout

import everyui.component.*
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.{FxSpec, ui}
import reactify.*

/** A child with a `flex` takes the room its row or column has left: a body under a header, as tall as the panel leaves
  * it, that shrinks as the header grows, so the two never overlap; and `Overlaps` tells of siblings that do. */
class FlexSpec extends FxSpec {
  private def box(w: Double, h: Double): Container = new Container {
    size.width @= w
    size.height @= h
  }

  private def tops(c: Container): List[Double] = fx(c.children().toList.map(_.position.top()))
  private def lefts(c: Container): List[Double] = fx(c.children().toList.map(_.position.left()))
  private def heights(c: Container): List[Double] = fx(c.children().toList.map(_.size.height()))
  private def widths(c: Container): List[Double] = fx(c.children().toList.map(_.size.width()))

  /** `test` with the page read right to left, back to left to right after. */
  private def rtl[T](test: => T): T = {
    fx(Direction.set(true))
    try test finally fx(Direction.set(false))
  }

  /** `test` with what `scribe.warn` says collected, and the check off again after. */
  private def warnings[T](test: (() => List[String]) => T): T = {
    var said: List[String] = Nil
    fx(everyui.scribe.forward = (level, message) => if (level == "warn" && message.startsWith("overlap")) said = said :+ message)
    // read as it is: written on the FX thread, and read after an `fx` or inside one (`eventually`)
    try test(() => said)
    finally fx {
      Overlaps.warn @= false
      everyui.scribe.forward = (_, _) => ()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A column with a height of its own" should {
    "give a filling body what the header leaves, below it, and shrink it as the header grows" in {
      val header = fx(box(200.0, 40.0))
      val body = fx(box(200.0, 10.0))
      val column = fx {
        val c = Column(8.0, padding = 10.0)(header, Fill(body))
        c.size.height @= 400.0
        c
      }
      tops(column) shouldBe List(10.0, 58.0)
      heights(column) shouldBe List(40.0, 332.0)
      // the filter wraps to a second line
      fx(header.size.height @= 80.0)
      tops(column) shouldBe List(10.0, 98.0)
      heights(column) shouldBe List(80.0, 292.0)
      fx(body.position.top() >= header.position.top() + header.size.height()) shouldBe true
      fx(body.position.top() + body.size.height()) shouldBe 390.0
    }
    "share the room among filling children by their flex" in {
      val column = fx {
        val c = Column()(Fill(box(50.0, 0.0), 1.0), Fill(box(50.0, 0.0), 2.0))
        c.size.height @= 300.0
        c
      }
      heights(column) shouldBe List(100.0, 200.0)
      tops(column) shouldBe List(0.0, 100.0)
    }
    "give a filling child nothing, never less, where the others take all the room" in {
      val column = fx {
        val c = Column(10.0)(box(50.0, 500.0), Fill(box(50.0, 20.0)))
        c.size.height @= 300.0
        c
      }
      heights(column) shouldBe List(500.0, 0.0)
      tops(column) shouldBe List(0.0, 510.0)
    }
    "take the padding and every gap out, but none for a child with no size" in {
      val column = fx {
        val c = Column(5.0, padding = 20.0)(box(50.0, 30.0), box(0.0, 0.0), Fill(box(50.0, 0.0)), box(50.0, 40.0))
        c.size.height @= 300.0
        c
      }
      // 300 - 40 padding - 30 - 40 - two gaps (the empty child books none)
      heights(column) shouldBe List(30.0, 0.0, 180.0, 40.0)
      tops(column) shouldBe List(20.0, 55.0, 55.0, 240.0)
    }
    "follow its own height as it is resized" in {
      val tall = Var(400.0)
      val body = fx(box(100.0, 0.0))
      val column = fx {
        val c = Column(0.0)(box(100.0, 50.0), Fill(body))
        c.size.height := tall()
        c
      }
      fx(body.size.height()) shouldBe 350.0
      fx(tall @= 250.0)
      fx(body.size.height()) shouldBe 200.0
      fx(column.size.height()) shouldBe 250.0
    }
    "lay out again as a child's flex changes" in {
      val a = fx(box(50.0, 0.0))
      val b = fx(box(50.0, 0.0))
      val column = fx {
        val c = Column()(Fill(a), Fill(b))
        c.size.height @= 300.0
        c
      }
      heights(column) shouldBe List(150.0, 150.0)
      fx(b.flex @= 2.0)
      heights(column) shouldBe List(100.0, 200.0)
    }
  }

  "A column sized to its children" should {
    "leave a child with a flex at its own size" in {
      val column = fx(Column(4.0)(box(50.0, 20.0), Fill(box(50.0, 30.0))))
      heights(column) shouldBe List(20.0, 30.0)
      fx(column.size.height()) shouldBe 54.0
    }
  }

  "A layout with no flex" should {
    "place the children exactly as before" in {
      def column(): Container = fx {
        val c = Column(6.0, padding = 4.0)(box(50.0, 20.0), box(0.0, 0.0), box(80.0, 30.0))
        c.size.height @= 500.0
        c
      }
      tops(column()) shouldBe List(4.0, 30.0, 30.0)
      heights(column()) shouldBe List(20.0, 0.0, 30.0)
      def row(): Container = fx {
        val r = Row(6.0, padding = 4.0)(box(50.0, 20.0), box(0.0, 10.0), box(80.0, 30.0))
        r.size.width @= 500.0
        r
      }
      lefts(row()) shouldBe List(4.0, 60.0, 60.0)
      widths(row()) shouldBe List(50.0, 0.0, 80.0)
      // mirrored within the run, as a row given its width after its layout always was
      rtl(lefts(row()) shouldBe List(90.0, 84.0, 4.0))
    }
  }

  "A row with a width of its own" should {
    "give a filling child the width the others leave" in {
      def row(): Container = fx {
        val r = Row(10.0, padding = 5.0)(box(100.0, 20.0), Fill(box(0.0, 20.0)), box(50.0, 20.0))
        r.size.width @= 400.0
        r
      }
      widths(row()) shouldBe List(100.0, 220.0, 50.0)
      lefts(row()) shouldBe List(5.0, 115.0, 345.0)
    }
    "mirror it in a right-to-left language" in {
      rtl {
        val r = fx {
          val r = Row(10.0, padding = 5.0)(box(100.0, 20.0), Fill(box(0.0, 20.0)), box(50.0, 20.0))
          r.size.width @= 400.0
          r
        }
        widths(r) shouldBe List(100.0, 220.0, 50.0)
        lefts(r) shouldBe List(295.0, 65.0, 5.0)
      }
    }
    "share among two filling children and follow a resize" in {
      val wide = Var(330.0)
      val r = fx {
        val r = Row(0.0)(box(30.0, 20.0), Fill(box(0.0, 20.0), 2.0), Fill(box(0.0, 20.0)))
        r.size.width := wide()
        r
      }
      widths(r) shouldBe List(30.0, 200.0, 100.0)
      fx(wide @= 630.0)
      widths(r) shouldBe List(30.0, 400.0, 200.0)
      lefts(r) shouldBe List(0.0, 30.0, 430.0)
    }
  }

  "A panel" should {
    "keep its body under its header" in {
      val header = fx(box(300.0, 36.0))
      val body = fx(box(300.0, 900.0))
      val panel = fx {
        val p = Panel(header, body, spacing = 8.0)
        p.size.width @= 300.0
        p.size.height @= 500.0
        p
      }
      tops(panel) shouldBe List(0.0, 44.0)
      fx(body.size.height()) shouldBe 456.0
      fx(header.size.height @= 60.0)
      fx(body.position.top()) shouldBe 68.0
      fx(body.size.height()) shouldBe 432.0
    }
  }

  "Overlaps" should {
    "tell of a list placed under a header that grew, once, and again when it changes" in warnings { said =>
      val header = fx(box(300.0, 40.0))
      val list = fx(box(300.0, 200.0))
      val placed = fx {
        val c = new Container
        c.size.width @= 300.0
        c.size.height @= 300.0
        list.position.top @= 40.0
        c.children ++= List(header, list)
        c
      }
      fx(Overlaps.check(placed)) shouldBe Nil
      fx(header.size.height @= 56.0)
      fx(Overlaps.check(placed)).map(_.boxes) shouldBe List("Container (0,0 300x56) and Container (0,40 300x200)")
      said().length shouldBe 1
      said().head should include("Container (0,0 300x56) and Container (0,40 300x200)")
      fx(Overlaps.check(placed))
      said().length shouldBe 1
      fx(header.size.height @= 60.0)
      fx(Overlaps.check(placed))
      said().length shouldBe 2
      fx(list.position.top @= 60.0)
      fx(Overlaps.check(placed)) shouldBe Nil
      said().length shouldBe 2
    }
    "pass over what lies wholly over or under another, what is hidden, and what is allowed" in warnings { said =>
      val c = fx {
        val c = new Container
        c.size.width @= 300.0
        c.size.height @= 300.0
        val backdrop = box(300.0, 300.0)
        val words = box(100.0, 20.0)
        words.position.top @= 20.0
        val hidden = box(100.0, 100.0)
        hidden.position.top @= 10.0
        hidden.visible @= false
        val badge = Overlaps.allow(box(20.0, 20.0))
        badge.position.left @= 90.0
        badge.position.top @= 10.0
        c.children ++= List(backdrop, words, hidden, badge)
        c
      }
      fx(Overlaps.check(c)) shouldBe Nil
      said() shouldBe Nil
    }
    "tell of neighbours in a column that overlap at all" in warnings { said =>
      val c = fx(Column(-10.0)(box(50.0, 20.0), box(50.0, 20.0)))
      fx(Overlaps.check(c)).length shouldBe 1
      said().length shouldBe 1
    }
    "look over the screen after a layout pass while on, and say nothing while off" in warnings { said =>
      val header = fx(box(300.0, 40.0))
      val list = fx(box(300.0, 200.0))
      val placed = fx {
        val c = new Container
        c.size.width @= 300.0
        c.size.height @= 300.0
        list.position.top @= 40.0
        c.children ++= List(header, list)
        ui.children.clear()
        Focus.focused @= None
        ui.children += c
        c
      }
      // off: an overlap made and a pass run tell nothing
      fx(header.size.height @= 56.0)
      fx(LayoutPass.flush())
      fx(())
      said() shouldBe Nil
      fx(Overlaps.warn @= true)
      eventually(said().nonEmpty) shouldBe true
      said().length shouldBe 1
      fx(header.size.height @= 70.0)
      fx(LayoutPass.flush())
      eventually(said().length == 2) shouldBe true
      fx(Overlaps.warn @= false)
      fx(header.size.height @= 80.0)
      fx(LayoutPass.flush())
      fx(())
      said().length shouldBe 2
    }
  }
}
