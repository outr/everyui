package everyui.list

import everyui.component.{Component, Container, ScrollContainer}
import everyui.event.Pointer
import everyui.focus.Focus
import everyui.layout.{HorizontalLayout, LayoutPass, VerticalLayout}
import everyui.perf.Quality
import everyui.{FxSpec, ui}
import reactify.*

/** A Home of rails made a row at a time: only the rows near the view are made, wherever the column sits in its page,
  * more as the page scrolls, those far away let go where it says, the focus carried down into rows not made yet, and
  * each row placed at the height it drew. */
class LazyColumnSpec extends FxSpec {
  override protected def keyed: Boolean = true

  override def afterAll(): Unit = {
    fx {
      ui.children.clear()
      Focus.focused @= None
    }
    super.afterAll()
  }

  // a row as a rail's: a strip `tall` high of three focusable cards
  private def row(i: Int, tall: Double): Container = {
    val r = new Container
    r.size.width @= 600.0
    r.layout @= Some(HorizontalLayout(10.0))
    r.children @= Vector.tabulate(3) { j =>
      val c = new Container
      c.size.width @= 100.0
      c.size.height @= tall
      c.focusable @= true
      c.semantics @= everyui.component.types.Semantics(label = "card " + i.toString + "/" + j.toString)
      c
    }
    r
  }

  /** A page 600 tall, a header 300 tall above a column of `n` rows of `tall` (taken to be `estimate`), 20 apart. */
  private def page(n: Int, tall: Int => Double = _ => 200.0, estimate: Double = 200.0): (ScrollContainer, LazyColumn) = fx {
    ui.children.clear()
    Quality.forget()
    val s = new ScrollContainer
    s.size.width @= 600.0
    s.size.height @= 600.0
    s.content.layout @= Some(VerticalLayout(0.0))
    val header = new Container
    header.size.width @= 600.0
    header.size.height @= 300.0
    val column = new LazyColumn(gap = 20.0)
    column.size.width @= 600.0
    column.items @= Vector.tabulate(n)(i => LazyItem("row" + i.toString, estimate, () => row(i, tall(i))))
    s.content.children @= Vector(header, column)
    ui.children += s
    LayoutPass.settle()
    (s, column)
  }

  private def press(key: String): Unit = fx {
    Pointer.usedAt = -1.0e9
    Focus.key(key, true)
    Focus.key(key, false)
  }

  private def settled(): Unit = {
    fx(LayoutPass.settle())
    fx(LayoutPass.settle())
  }

  "A LazyColumn" should {
    "make only the rows near the view, and more as its page scrolls" in {
      val (s, column) = page(22)
      settled()
      // the view (600) and a view beyond it each way, under a header of 300: rows 0 to 4 or so, never all 22
      fx(column.madeCount) should be >= 3
      fx(column.madeCount) should be <= 6
      fx(column.count) shouldBe 22
      // as tall as every row would be
      fx(column.size.height()) shouldBe (22 * 200.0 + 21 * 20.0)
      fx(s.scrollTo(300.0 + 220.0 * 15))
      settled()
      fx(column.rowAt(15).nonEmpty) shouldBe true
      fx(column.rowAt(21)) shouldBe None
      fx(s.scrollTo(1.0e6))
      settled()
      fx(column.rowAt(21).nonEmpty) shouldBe true
    }
    "not draw the made rows out of sight, and draw them again as they come back" in {
      val (s, column) = page(22)
      val first = fx(column.rowAt(0).get)
      fx(first.visible()) shouldBe true
      fx(s.scrollTo(300.0 + 220.0 * 15))
      settled()
      fx(column.rowAt(0).exists(_ eq first)) shouldBe true
      fx(first.visible()) shouldBe false
      fx(column.rowAt(15).get.visible()) shouldBe true
      fx(s.scrollTo(0.0))
      settled()
      fx(first.visible()) shouldBe true
    }
    "let rows far from the view go where it says, and make them again as they come back" in {
      val (s, column) = page(40)
      fx(column.releaseBeyond @= 1.0)
      fx(s.scrollTo(300.0 + 220.0 * 30))
      settled()
      fx(column.rowAt(0)) shouldBe None
      fx(column.madeCount) should be <= 12
      fx(s.scrollTo(0.0))
      settled()
      fx(column.rowAt(0).nonEmpty) shouldBe true
      fx(column.rowAt(30)) shouldBe None
    }
    "place each row at the height it drew, the rows after it moving with it" in {
      val (_, column) = page(10, i => if (i % 2 == 0) 150.0 else 260.0, estimate = 100.0)
      settled()
      val made = fx((0 until 10).filter(i => column.rowAt(i).nonEmpty))
      made.size should be >= 2
      made.sliding(2).filter(p => p(1) == p(0) + 1).foreach { p =>
        val (a, b) = fx((column.rowAt(p(0)).get, column.rowAt(p(1)).get))
        fx(b.position.top()) shouldBe fx(a.position.top() + a.size.height() + 20.0)
      }
    }
    "keep what shows where it is as rows above the view come out taller than taken to be" in {
      val (s, column) = page(60, _ => 200.0, estimate = 100.0)
      fx(s.scrollTo(300.0 + 120.0 * 40))
      settled()
      // a row in view, where it is on the screen; then up past the rows made above it, which come out twice as tall
      val first = fx((16 until 60).find(i => column.rowAt(i).nonEmpty).get)
      val seen = first + 4
      def onScreen(): Double = fx(column.rowAt(seen).get.position.top() + 300.0 - s.offset())
      val was = onScreen()
      fx(s.scrollBy(-700.0))
      settled()
      fx(column.rowAt(first - 1).nonEmpty) shouldBe true
      onScreen() shouldBe (was + 700.0) +- 0.5
    }
    "carry the focus down into rows not made yet, and keep the row it is in" in {
      val (_, column) = page(30)
      fx(column.releaseBeyond @= 1.0)
      fx(column.rowAt(0).get.asInstanceOf[Container].children()(1).focus())
      (1 to 25).foreach { i =>
        press("ArrowDown")
        settled()
        fx(Focus.focused().map(_.semantics().label)) shouldBe Some("card " + i.toString + "/0")
      }
      // the rows far above were let go, never the one the focus is in
      fx(column.rowAt(0)) shouldBe None
      fx(LazyColumn.focusIn(column).exists(r => column.rowAt(25).exists(_ eq r))) shouldBe true
      press("ArrowUp")
      settled()
      fx(Focus.focused().map(_.semantics().label)) shouldBe Some("card 24/0")
    }
    "tell of the end once for each count, as the rows made reach it" in {
      val (s, column) = page(12)
      var told = Vector.empty[Int]
      fx(column.nearEnd.attach(n => told = told :+ n))
      fx(s.scrollTo(300.0 + 220.0 * 12))
      settled()
      told shouldBe Vector(12)
      fx(column.items @= column.items() ++ Vector.tabulate(6)(i => LazyItem("more" + i.toString, 200.0,
        () => row(100 + i, 200.0))))
      fx(s.scrollTo(300.0 + 220.0 * 18))
      settled()
      told shouldBe Vector(12, 18)
    }
    "keep what is made of rows whose keys stay, and take apart those that leave" in {
      val (_, column) = page(5)
      settled()
      val first = fx(column.rowAt(0).get)
      fx(column.items @= column.items().drop(1).take(1) ++ column.items().take(1))
      settled()
      fx(column.rowAt(1).exists(_ eq first)) shouldBe true
      fx(column.count) shouldBe 2
      fx(column.madeCount) shouldBe 2
    }
    "make the rows in view as it is built, before any layout, from their estimates" in {
      val made = fx {
        ui.children.clear()
        Quality.forget()
        LayoutPass.batch {
          val s = new ScrollContainer
          s.size.width @= 600.0
          s.size.height @= 600.0
          val column = new LazyColumn(gap = 20.0)
          column.size.width @= 600.0
          column.items @= Vector.tabulate(22)(i => LazyItem("row" + i.toString, 200.0, () => row(i, 200.0)))
          s.content.children += column
          ui.children += s
          // (layout held: nothing measured yet)
          column.madeCount
        }
      }
      made should be >= 3
    }
    "make a row for each item where keys repeat, each at its own place" in {
      val (_, column) = page(0)
      fx(column.items @= Vector("a", "b", "a", "c", "a").zipWithIndex.map((k, i) => LazyItem(k, 60.0, () => row(i, 60.0))))
      settled()
      fx(column.madeCount) shouldBe 5
      val rows = fx((0 until 5).map(i => column.rowAt(i).get))
      rows.distinct.size shouldBe 5
      fx(rows.map(_.position.top())) shouldBe (0 until 5).map(_ * 80.0)
      fx(rows.map(r => r.asInstanceOf[Container].children().head.semantics().label)) shouldBe
        (0 until 5).map(i => "card " + i.toString + "/0")
      fx(column.indexOf("a")) shouldBe 0
      // the repeated rows keep what they made as an item is added
      val third = rows(2)
      fx(column.items @= column.items() :+ LazyItem("d", 60.0, () => row(5, 60.0)))
      settled()
      fx(column.rowAt(2).exists(_ eq third)) shouldBe true
    }
  }
}
