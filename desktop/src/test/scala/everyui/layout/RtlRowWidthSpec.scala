package everyui.layout

import everyui.FxSpec
import everyui.component.*
import everyui.i18n.Direction
import reactify.*

/** A row or a column given its width after it was made (`Row(...)` then `size.width := card.size.width()`) mirrors
  * right to left within that width, as one given it first does, not within the run of what it holds; one that follows
  * what it holds still mirrors within its run, and left to right nothing moves. */
class RtlRowWidthSpec extends FxSpec {
  private def box(w: Double): Container = new Container {
    size.width @= w
    size.height @= 20.0
  }

  /** `test` with the page read right to left, back to left to right after. */
  private def rtl[T](test: => T): T = {
    fx(Direction.set(true))
    try test finally fx(Direction.set(false))
  }

  private def lefts(c: Container): List[Double] = fx(c.children().toList.map(_.position.left()))
  private def widths(c: Container): List[Double] = fx(c.children().toList.map(_.size.width()))

  // a 40 and a 60 wide box, 8 apart, with 10 of padding, and with room to fill between them
  private def row(): Container = Row(8.0, padding = 10.0)(box(40.0), box(60.0))
  private def filled(): Container = Row(8.0, padding = 10.0)(box(40.0), Fill(box(0.0)), box(60.0))

  /** A row given `width` before its layout, the order that always mirrored within it. */
  private def first(width: => Double, filling: Boolean): Container = {
    val c = new Container
    c.size.width := width
    c.padding @= 10.0
    c.layout @= Some(HorizontalLayout(8.0))
    c.children ++= (if (filling) List(box(40.0), Fill(box(0.0)), box(60.0)) else List(box(40.0), box(60.0)))
    c
  }

  "A row given its width after it was made" should {
    "mirror within that width, right to left, given as a constant" in {
      rtl {
        val r = fx {
          val c = row()
          c.size.width @= 300.0
          c
        }
        fx(r.size.width()) shouldBe 300.0
        // the first at the right edge less the padding, the second the gap before it
        lefts(r) shouldBe List(250.0, 182.0)
      }
    }
    "mirror within a width bound to another's, and follow it as it changes" in {
      rtl {
        val card = fx(box(300.0))
        val r = fx {
          val c = row()
          c.size.width := card.size.width()
          c
        }
        lefts(r) shouldBe List(250.0, 182.0)
        fx(card.size.width @= 420.0)
        fx(r.size.width()) shouldBe 420.0
        lefts(r) shouldBe List(370.0, 302.0)
        fx(card.size.width @= 200.0)
        lefts(r) shouldBe List(150.0, 82.0)
      }
    }
    "give a filling child the room left, between the others, on the right side" in {
      rtl {
        val card = fx(box(300.0))
        val r = fx {
          val c = filled()
          c.size.width := card.size.width()
          c
        }
        // 300 less 20 of padding, 40, 60 and two gaps: the last at the left edge less the padding
        widths(r) shouldBe List(40.0, 164.0, 60.0)
        lefts(r) shouldBe List(250.0, 78.0, 10.0)
        fx(card.size.width @= 400.0)
        widths(r) shouldBe List(40.0, 264.0, 60.0)
        lefts(r) shouldBe List(350.0, 78.0, 10.0)
      }
    }
    "be placed as one given its width first is" in {
      rtl {
        val after = fx {
          val c = row()
          c.size.width @= 300.0
          c
        }
        lefts(after) shouldBe lefts(fx(first(300.0, filling = false)))
        val width = Var(300.0)
        val bound = fx {
          val c = row()
          c.size.width := width()
          c
        }
        val before = fx(first(width(), filling = false))
        lefts(bound) shouldBe lefts(before)
        fx(width @= 360.0)
        lefts(before) shouldBe List(310.0, 242.0)
        lefts(bound) shouldBe lefts(before)
        val fillAfter = fx {
          val c = filled()
          c.size.width @= 300.0
          c
        }
        val fillFirst = fx(first(300.0, filling = true))
        lefts(fillAfter) shouldBe lefts(fillFirst)
        widths(fillAfter) shouldBe widths(fillFirst)
      }
    }
    "be as it was left to right" in {
      val card = fx(box(300.0))
      val r = fx {
        val c = row()
        c.size.width := card.size.width()
        c
      }
      lefts(r) shouldBe List(10.0, 58.0)
      fx(card.size.width @= 420.0)
      lefts(r) shouldBe List(10.0, 58.0)
      val f = fx {
        val c = filled()
        c.size.width @= 300.0
        c
      }
      lefts(f) shouldBe List(10.0, 58.0, 230.0)
      lefts(fx(first(300.0, filling = false))) shouldBe List(10.0, 58.0)
    }
  }

  "A row that follows what it holds" should {
    "mirror within its run right to left, and be as it was left to right" in {
      val ltr = fx(row())
      fx(ltr.size.width()) shouldBe 128.0
      lefts(ltr) shouldBe List(10.0, 58.0)
      rtl {
        val r = fx(row())
        fx(r.size.width()) shouldBe 128.0
        lefts(r) shouldBe List(78.0, 10.0)
        // growing, it is mirrored within its new run
        fx(r.children().head.size.width @= 70.0)
        fx(r.size.width()) shouldBe 158.0
        lefts(r) shouldBe List(78.0, 10.0)
      }
    }
  }

  "A column given its width after it was made" should {
    "put its lines at the right of that width, as one given it first does" in {
      rtl {
        val after = fx {
          val c = Column(4.0, padding = 10.0)(box(100.0), box(200.0))
          c.size.width @= 300.0
          c
        }
        lefts(after) shouldBe List(190.0, 90.0)
        val width = Var(300.0)
        val bound = fx {
          val c = Column(4.0, padding = 10.0)(box(100.0), box(200.0))
          c.size.width := width()
          c
        }
        fx(width @= 400.0)
        lefts(bound) shouldBe List(290.0, 190.0)
      }
      val ltr = fx {
        val c = Column(4.0, padding = 10.0)(box(100.0), box(200.0))
        c.size.width @= 300.0
        c
      }
      lefts(ltr) shouldBe List(10.0, 10.0)
    }
    "centre its lines on that width" in {
      val c = fx {
        val c = Column(4.0, center = true)(box(100.0), box(200.0))
        c.size.width @= 300.0
        c
      }
      lefts(c) shouldBe List(100.0, 50.0)
      rtl(lefts(fx(Column(4.0, center = true)(box(100.0), box(200.0))))) shouldBe List(50.0, 0.0)
    }
  }
}
