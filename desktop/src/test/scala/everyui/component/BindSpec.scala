package everyui.component

import everyui.FxSpec
import reactify.*

/** A bound list takes apart what its `render` made once it has left, a change later, so a list rendered again every
  * few seconds does not hold every component it ever made; what `render` hands back from elsewhere is left whole, and
  * a `render` that hands back one it made (a `lazy val`) keeps them all. */
class BindSpec extends FxSpec {
  "A bound list" should {
    "take apart what its render made, a change after it left" in {
      val (values, list) = fx {
        val values = Var(List(1))
        val list = new Container
        list.children.bind(values())(i => TextView(i.toString, 12.0))
        (values, list)
      }
      val first = fx(list.children().head)
      fx(values @= List(2))
      fx(first.takenApart) shouldBe false
      val second = fx(list.children().head)
      fx(values @= List(3))
      fx(first.takenApart) shouldBe true
      fx(second.takenApart) shouldBe false
      fx(list.dispose())
      fx(second.takenApart) shouldBe true
    }
    "leave whole what its render hands back from elsewhere" in {
      val (values, a, b) = fx {
        val a = TextView("a", 12.0)
        val b = TextView("b", 12.0)
        val parts = Map("a" -> a, "b" -> b)
        val values = Var(List("a"))
        new Container().children.bind(values())(parts)
        (values, a, b)
      }
      fx(values @= List("b"))
      fx(values @= List("a"))
      fx(values @= List("b"))
      fx(a.takenApart) shouldBe false
      fx(b.takenApart) shouldBe false
    }
    "keep what its render made when it hands one back again" in {
      val holder = fx(new BindSpec.Holder)
      fx(holder.values @= List("error"))
      fx(holder.values @= List("card"))
      fx(holder.values @= List("error"))
      fx(holder.values @= List("other"))
      fx(holder.card.takenApart) shouldBe false
      fx(holder.list.children().head.takenApart) shouldBe false
    }
  }
}

object BindSpec {
  /** A screen's shape: a card made the first time the list asks for it, and handed back each time after. */
  class Holder {
    val values: Var[List[String]] = Var(List("card"))
    lazy val card: TextView = TextView("card", 12.0)
    val list: Container = new Container
    list.children.bind(values()) {
      case "card" => card
      case other => TextView(other, 12.0)
    }
  }
}
