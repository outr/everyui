package everyui.list

import everyui.component.{Component, Container, WhileHovered}
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import reactify.*

/** What a list of cards (an inbox) asks of a virtual list: gaps between the rows and padding round them, a card that
  * unfolds where it is with the one under the cursor kept still, more items asked for near the end and appended in
  * place, one item replaced with only its row drawn again, a part made while hovered and let go after, and a
  * checkbox in a row that adds or takes away its row with no modifier. And what the list keeps: the heights of items
  * gone are let go, and a plain row is three components. */
class CardListSpec extends FxSpec {
  // a card: its item's name, as tall as the item says ("name:height"), counting the times it is handed an item
  private var handed = 0
  private def heightOf(s: String): Double = s.split(':').lift(1).map(_.toDouble).getOrElse(60.0)
  private def keyOf(s: String): String = s.split(':')(0)

  private class Card extends ListRow[String] {
    item.attach(i => if (i.nonEmpty) handed += 1)
    size.height := item().map(heightOf).getOrElse(60.0)
  }

  // a card whose box is its checkbox
  private class CheckCard extends Card {
    val box: Container = new Container
    box.size.width @= 20.0
    box.size.height @= 20.0
    children += box
    checks(box)
  }

  private def cards(n: Int): Var[Vector[String]] = Var((0 until n).map(i => "c" + i.toString).toVector)

  private def made(items: Var[Vector[String]]): VirtualList[String] = {
    val list = new VirtualList[String](items, keyOf, keyOf, 60.0, measuredRows = true)(() => new Card)
    list.size.width @= 300.0
    list.size.height @= 400.0
    list
  }

  private def onScreen[T](list: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += list
    }
    fx(())
    try test finally fx {
      ui.children.clear()
      list.dispose()
      Focus.focused @= None
    }
  }

  // a row's place on the screen, from the list's top
  private def screenTop(list: VirtualList[String], key: String): Double = fx {
    val i = list.indexOf(key)
    list.top(i) - list.offset()
  }

  "A list of cards" should {
    "set its rows apart by its gap, inside its padding, and pick no row from a gap" in {
      val list = fx(made(cards(20)))
      onScreen(list) {
        fx {
          list.gap @= 8.0
          list.padding @= 12.0
        }
        fx(())
        fx(list.top(0)) shouldBe 12.0
        fx(list.top(3)) shouldBe 12.0 + 3 * 68.0
        fx(list.total) shouldBe 12.0 * 2 + 20 * 60.0 + 19 * 8.0
        val row = fx(list.rowAt(1).get)
        fx(row.position.left()) shouldBe 12.0
        fx(row.size.width()) shouldBe 276.0
        fx(row.position.top()) shouldBe 12.0 + 68.0
        fx(list.indexAt(12.0 + 68.0 + 10.0)) shouldBe 1
        // a click in the gap after the first row picks nothing; on the second, it
        fx(list.event.click @= PointerEvent(PointerKind.Click, 20.0, list.absoluteTop + 12.0 + 62.0))
        fx(list.selection.keys()) shouldBe Set.empty
        fx(list.event.click @= PointerEvent(PointerKind.Click, 20.0, list.absoluteTop + 12.0 + 90.0))
        fx(list.selection.keys()) shouldBe Set("c1")
        // the last row shown with the padding below it
        fx(list.reveal(19))
        fx(list.offset()) shouldBe fx(list.total - 400.0)
      }
    }

    "keep the row under the cursor still as a card above it unfolds, and the rest moved on" in {
      val items = cards(40)
      val list = fx(made(items))
      onScreen(list) {
        fx(list.scrollTop(600.0))
        fx(list.current @= Some("c12"))
        fx(())
        val cursorAt = screenTop(list, "c12")
        val belowAt = screenTop(list, "c14")
        // c11, shown above the cursor, unfolds to 200
        fx(list.replace("c11:200"))
        fx(())
        fx(())
        screenTop(list, "c12") shouldBe cursorAt
        screenTop(list, "c14") shouldBe belowAt
        fx(list.heightAt(list.indexOf("c11"))) shouldBe 200.0
        // and folds again, the cursor's still where it was
        fx(list.replace("c11:60"))
        fx(())
        fx(())
        screenTop(list, "c12") shouldBe cursorAt
        // with no cursor in view, a card unfolding below the first that shows moves only those after it
        fx(list.current @= None)
        val first = screenTop(list, "c10")
        fx(list.replace("c13:150"))
        fx(())
        fx(())
        screenTop(list, "c10") shouldBe first
        screenTop(list, "c14") shouldBe first + 4 * 60.0 + 150.0 - 60.0
      }
    }

    "ask for more as the end comes near, once for each count, and keep its place as they are appended" in {
      val items = cards(20)
      val list = fx(made(items))
      var told = List.empty[Int]
      fx(list.nearEnd.attach(n => told = told :+ n))
      onScreen(list) {
        told shouldBe Nil
        // 20 cards of 60 are 1200 tall: a screen (400) from the end is past 400
        fx(list.scrollTop(300.0))
        told shouldBe Nil
        fx(list.scrollTop(450.0))
        told shouldBe List(20)
        fx(list.scrollTop(460.0))
        told shouldBe List(20)
        val shownAt = screenTop(list, "c9")
        fx(items @= items() ++ (20 until 40).map(i => "c" + i.toString))
        fx(())
        screenTop(list, "c9") shouldBe shownAt
        fx(list.scrollTop(1700.0))
        told shouldBe List(20, 40)
      }
    }

    "draw only the row of an item replaced by its key" in {
      val items = cards(30)
      val list = fx(made(items))
      onScreen(list) {
        handed = 0
        fx(list.replace("c2:60")) shouldBe true
        fx(())
        handed shouldBe 1
        fx(items.get(2)) shouldBe "c2:60"
        fx(list.rowAt(2).flatMap(_.item())) shouldBe Some("c2:60")
        fx(list.replace("nothing:60")) shouldBe false
        handed shouldBe 1
      }
    }

    "add or take away a row through its checkbox, with no modifier, and a range with Shift" in {
      val list = fx(new VirtualList[String](cards(20), keyOf, keyOf, 60.0)(() => new CheckCard))
      fx(list.size.height @= 400.0)
      def tick(index: Int, shift: Boolean = false): Unit = fx {
        val box = list.rowAt(index).get.asInstanceOf[CheckCard].box
        box.event.click @= PointerEvent(PointerKind.Click, 5.0, 5.0, shift = shift)
      }
      onScreen(list) {
        fx(list.select("c0"))
        tick(2)
        fx(list.selection.keys()) shouldBe Set("c0", "c2")
        tick(5, shift = true)
        fx(list.selection.keys()) shouldBe Set("c0", "c2", "c3", "c4", "c5")
        tick(3)
        fx(list.selection.keys()) shouldBe Set("c0", "c2", "c4", "c5")
        fx(list.current()) shouldBe Some("c3")
        // the click went no further: nothing activated, nothing chosen alone
        tick(0)
        fx(list.selection.keys()) shouldBe Set("c2", "c4", "c5")
      }
    }

    "let the heights of items gone go" in {
      val items = cards(30)
      val list = fx(made(items))
      onScreen(list) {
        fx(())
        val kept = fx(list.keptHeights)
        kept should be > 0
        fx(items @= (100 until 110).map(i => "c" + i.toString).toVector)
        fx(())
        fx(list.keptHeights) should be <= 10
        fx(items @= Vector.empty)
        fx(list.keptHeights) shouldBe 0
      }
    }

    "make a plain text row of three components" in {
      val list = fx(VirtualList.text[String](Var((0 until 100).map(_.toString).toVector), s => s, s => s))
      onScreen(list) {
        fx(list.size.height @= 280.0)
        fx(())
        val rows = fx(list.rows.filter(_.visible()))
        rows.size should be > 5
        def count(c: Component): Int = 1 + c.childComponents.map(count).sum
        fx(rows.map(count).distinct) shouldBe Vector(3)
        // a badge, once a row has one, is made and kept
        val decorated = fx(VirtualList.text[String](Var(Vector("a", "b")), s => s, s => s,
          s => if (s == "a") Decoration(badge = "3") else Decoration.plain))
        fx {
          ui.children += decorated
          ()
        }
        fx(decorated.rowAt(0).get.asInstanceOf[TextRow[String]].label.badge.visible()) shouldBe true
        fx(count(decorated.rowAt(1).get)) shouldBe 3
        fx(decorated.dispose())
      }
    }
  }

  "A part made while hovered" should {
    "be made as the pointer comes over, kept while it holds the focus, and let go as it leaves" in {
      val host = fx(new Container)
      var parts = List.empty[Container]
      val hovered = fx(WhileHovered(host)(() => {
        val p = new Container
        p.focusable @= true
        p.size.width @= 10.0
        p.size.height @= 10.0
        parts = p :: parts
        p
      }))
      fx {
        host.size.width @= 100.0
        host.size.height @= 100.0
      }
      onScreen(host) {
        fx(hovered.made()) shouldBe None
        fx(host.event.pointer.enter @= PointerEvent(PointerKind.Enter, 5.0, 5.0))
        fx(hovered.made().nonEmpty) shouldBe true
        fx(host.children().exists(_ eq parts.head)) shouldBe true
        // the focus in it keeps it
        fx(parts.head.focus())
        fx(host.event.pointer.leave @= PointerEvent(PointerKind.Leave, 500.0, 500.0))
        fx(hovered.made().nonEmpty) shouldBe true
        fx(Focus.focused @= None)
        fx(hovered.made()) shouldBe None
        fx(parts.head.takenApart) shouldBe true
        fx(host.children()) shouldBe Vector.empty
        // made again, a new one, as the pointer comes back
        fx(host.event.pointer.enter @= PointerEvent(PointerKind.Enter, 5.0, 5.0))
        parts.size shouldBe 2
      }
      fx(parts.head.takenApart) shouldBe true
    }
  }
}
