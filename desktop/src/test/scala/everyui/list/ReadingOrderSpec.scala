package everyui.list

import everyui.focus.Focus
import everyui.{FxSpec, ui}
import reactify.*

/** A list scrolled hands the rows leaving one end the items arriving at the other, which leaves its rows out of the
  * order they read; a selection of words across rows (on the web) takes them in their nodes' order, so the rows are
  * put back in order first, and nothing else among the list's parts moves. */
class ReadingOrderSpec extends FxSpec {
  "A list's rows" should {
    "be put in the order they read, the list's other parts where they were" in {
      val list = fx(VirtualList.text[String](Var((0 until 200).map(i => "item " + i).toVector), s => s, s => s))
      fx {
        ui.children.clear()
        ui.children += list
        list.size.height @= 280.0
      }
      try {
        fx(list.scrollTop(28.0 * 37))
        fx(list.refresh())
        def shownOrder: Vector[String] = fx(list.rows.filter(_.visible()).flatMap(_.item().toList))
        // what sits at each place among the list's parts that is not a row
        def others: Vector[Option[everyui.component.Component]] =
          fx(list.children().map(c => if (c.isInstanceOf[ListRow[?]]) None else Some(c)))
        val othersBefore = others
        val before = shownOrder
        before should not be before.sortBy(_.drop(5).toInt)
        fx(list.inReadingOrder())
        shownOrder shouldBe before.sortBy(_.drop(5).toInt)
        others.zip(othersBefore).forall(p => p._1.isEmpty == p._2.isEmpty && p._1.forall(a => p._2.exists(_ eq a))) shouldBe true
        fx(list.rowAt(37).flatMap(_.item())) shouldBe Some("item 37")
      } finally fx {
        ui.children.clear()
        Focus.focused @= None
      }
    }
  }
}
