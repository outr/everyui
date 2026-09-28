package everyui.tree

import everyui.Color
import everyui.focus.Focus
import everyui.list.{Decoration, VirtualList}
import everyui.table.{GridColumn, TextCell, VirtualTable}
import everyui.{FxSpec, ui}
import reactify.*

/** A row's look changes at once, on the row already showing: when its item changes in the model, and when what the look
  * comes from is outside the item (`redecorate`). */
class DecorateSpec extends FxSpec {
  case class Entry(path: String, status: String = "")

  private val red = Color.fromHex("ff0000")
  private val green = Color.fromHex("00ff00")

  private def onScreen[T](c: everyui.component.Component)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += c
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A tree's row" should {
    "be dressed again at once when its node is updated, on the same row" in {
      val model = fx(new TreeModel[Entry](_.path))
      val tree = fx(new Tree[Entry](model, _.path))
      fx {
        model.setChildren(None, List(Entry("a"), Entry("b")))
        tree.decorate @= (e => Decoration(color = Some(if (e.status == "M") red else green), badge = e.status))
      }
      onScreen(tree) {
        val row = fx(tree.list.rowAt(1).get.asInstanceOf[TreeRow[Entry]])
        fx(row.label.label.color()) shouldBe green
        fx(model.update(Entry("b", "M")))
        fx(tree.list.rowAt(1).get eq row) shouldBe true
        fx(row.label.label.color()) shouldBe red
      }
    }
    "be dressed again from what the app keeps outside the node, when asked" in {
      val model = fx(new TreeModel[Entry](_.path))
      val tree = fx(new Tree[Entry](model, _.path))
      val statuses = scala.collection.mutable.Map("a" -> "", "b" -> "")
      fx {
        model.setChildren(None, List(Entry("a"), Entry("b")))
        tree.decorate @= (e => Decoration(color = Some(if (statuses(e.path) == "M") red else green)))
      }
      onScreen(tree) {
        val a = fx(tree.list.rowAt(0).get.asInstanceOf[TreeRow[Entry]])
        val b = fx(tree.list.rowAt(1).get.asInstanceOf[TreeRow[Entry]])
        statuses("a") = "M"
        statuses("b") = "M"
        fx(tree.redecorate("a"))
        fx(a.label.label.color()) shouldBe red
        fx(b.label.label.color()) shouldBe green
        fx(tree.redecorateAll())
        fx(b.label.label.color()) shouldBe red
      }
    }
  }

  "A list's and a table's rows" should {
    "be made again from their items when asked" in {
      val flags = scala.collection.mutable.Map("x" -> "old")
      val list = fx(VirtualList.text[String](Var(Vector("x")), s => s, s => s + " " + flags(s)))
      val table = fx(new VirtualTable[String](Var(Vector("x")), s => s, s => s,
        Vector(GridColumn.text[String]("c", "C", 100.0)(s => flags(s)))))
      onScreen(list) {
        flags("x") = "new"
        fx(list.redecorate("x"))
        fx(list.rowAt(0).get.asInstanceOf[everyui.list.TextRow[String]].label.label.content()) shouldBe "x new"
      }
      flags("x") = "old"
      onScreen(table) {
        flags("x") = "new"
        fx(table.redecorateAll())
        fx(table.list.rowAt(0).get.asInstanceOf[everyui.table.TableRow[String]].cells(0)
          .asInstanceOf[TextCell[String]].label.content()) shouldBe "new"
      }
    }
  }
}
