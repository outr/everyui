package everyui.layout

import everyui.component.Container
import everyui.{FxSpec, ui}

/** A child hidden while it collapses takes no room in its column, row or flow, nor in its container's size, and takes
  * its place back as it shows; one hidden without collapsing keeps its place. */
class CollapseSpec extends FxSpec {
  private def box(w: Double, h: Double): Container = {
    val c = new Container
    c.size.width @= w
    c.size.height @= h
    c
  }

  "A hidden child" should {
    "take no room in a column while it collapses, and its place back as it shows" in {
      val (column, a, b, c) = fx {
        ui.children.clear()
        val col = new Container
        col.layout @= Some(VerticalLayout(10.0))
        val (x, y, z) = (box(100, 20), box(100, 30), box(100, 40))
        col.children ++= List(x, y, z)
        ui.children += col
        (col, x, y, z)
      }
      fx(c.position.top()) shouldBe 70.0
      fx(b.visible @= false)
      // hidden alone keeps its place
      fx(c.position.top()) shouldBe 70.0
      fx(b.collapses @= true)
      fx(c.position.top()) shouldBe 30.0
      fx(column.size.height()) shouldBe 70.0
      fx(b.visible @= true)
      fx(c.position.top()) shouldBe 70.0
      fx(column.size.height()) shouldBe 110.0
    }
    "take no room in a row while it collapses, the last one leaving the row's size" in {
      val (row, x, y) = fx {
        ui.children.clear()
        val r = new Container
        r.layout @= Some(HorizontalLayout(8.0))
        val (p, q) = (box(50, 20), box(60, 20))
        p.collapses @= true
        r.children ++= List(p, q)
        ui.children += r
        (r, p, q)
      }
      fx(y.position.left()) shouldBe 58.0
      fx(x.visible @= false)
      fx(y.position.left()) shouldBe 0.0
      fx(row.size.width()) shouldBe 60.0
      fx(y.collapses @= true)
      fx(y.visible @= false)
      fx(row.size.width()) shouldBe 0.0
    }
  }

  override def afterAll(): Unit = {
    fx(ui.children.clear())
    super.afterAll()
  }
}
