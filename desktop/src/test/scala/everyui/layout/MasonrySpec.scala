package everyui.layout

import everyui.component.*
import everyui.event.Key
import everyui.i18n.Direction
import everyui.{FxSpec, ui}

/** Cards in masonry columns: as many columns as fit, each card in the shortest, laid out again as a card grows and as
  * the width changes, mirrored right to left, and gone through in the children's order. */
class MasonrySpec extends FxSpec {
  private def card(h: Double): Container = new Container {
    size.height @= h
  }

  private def onScreen[T](board: Container)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += board
    }
    try test finally fx {
      Direction.set(false)
      ui.children.clear()
    }
  }

  private def placed(board: Container): List[(Double, Double, Double)] =
    fx(board.children().toList.map(c => (c.position.left(), c.position.top(), c.size.width())))

  private def board(width: Double, heights: Double*): Container = fx {
    val b = new Container
    b.size.width @= width
    b.layout @= Some(MasonryLayout(minColumnWidth = 200.0, gap = 10.0))
    b.children ++= heights.map(card)
    b
  }

  "A masonry layout" should {
    "make as many columns as fit, one however narrow" in {
      val m = MasonryLayout(200.0, 10.0)
      List(100.0, 200.0, 409.0, 410.0, 620.0, 1000.0).map(m.columnsFor) shouldBe List(1, 1, 1, 2, 3, 4)
    }
    "put each card in the shortest column, a column wide, and grow to the tallest" in {
      val b = board(410.0, 100.0, 50.0, 30.0, 80.0)
      onScreen(b) {
        placed(b) shouldBe List((0.0, 0.0, 200.0), (210.0, 0.0, 200.0), (210.0, 60.0, 200.0), (210.0, 100.0, 200.0))
        fx(b.size.height()) shouldBe 180.0 +- 0.5
      }
    }
    "lay out again as a card grows, and as the width changes" in {
      val b = board(410.0, 100.0, 50.0, 30.0)
      onScreen(b) {
        fx(b.children()(1).size.height @= 200.0)
        placed(b).map(p => (p._1, p._2)) shouldBe List((0.0, 0.0), (210.0, 0.0), (0.0, 110.0))
        fx(b.size.width @= 620.0)
        placed(b) shouldBe List((0.0, 0.0, 200.0), (210.0, 0.0, 200.0), (420.0, 0.0, 200.0))
        fx(b.size.width @= 150.0)
        placed(b) shouldBe List((0.0, 0.0, 150.0), (0.0, 110.0, 150.0), (0.0, 320.0, 150.0))
      }
    }
    "put the first column at the start of a right-to-left page" in {
      val b = board(410.0, 100.0, 50.0)
      onScreen(b) {
        fx {
          Direction.set(true)
          b.relayout()
        }
        placed(b).map(_._1) shouldBe List(210.0, 0.0)
      }
    }
    "go through the cards in their order with Left and Right, and down a column with Up and Down" in {
      val b = board(410.0, 100.0, 50.0, 30.0, 80.0)
      onScreen(b) {
        val cs = fx(b.children().toVector)
        val m = MasonryLayout(200.0, 10.0)
        fx(m.neighbour(b, cs, cs(0), Key.ArrowRight)) shouldBe Some(cs(1))
        fx(m.neighbour(b, cs, cs(2), Key.ArrowLeft)) shouldBe Some(cs(1))
        fx(m.neighbour(b, cs, cs(1), Key.ArrowDown)) shouldBe Some(cs(2))
        fx(m.neighbour(b, cs, cs(3), Key.ArrowUp)) shouldBe Some(cs(2))
        fx(m.neighbour(b, cs, cs(0), Key.ArrowDown)) shouldBe None
      }
    }
  }
}
