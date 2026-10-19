package everyui.picker

import everyui.async.Task
import everyui.component.*
import everyui.event.Key
import everyui.focus.Focus
import everyui.render.Nodes
import everyui.{FxSpec, ui}
import reactify.*

/** A picker's heading: a muted line under the field, over the chips and the list, that takes no room without words,
  * follows its value and wraps, is never focused nor counted, and leaves the field where it was. */
class PickerHeadingSpec extends FxSpec {
  private val hits = Vector("Auto:mode", "Fast:mode", "Careful:mode", "Plain:style")
  private def group(h: String): String = h.substring(h.indexOf(':') + 1)
  private val segments = List(Segment.all("All"), Segment("mode", "Modes"), Segment("style", "Styles"))

  private def picker(heading: Option[Val[String]], extra: Option[PickerField] = None): FilterPicker[String] =
    fx(new FilterPicker[String](q => Task.pure(hits.filter(_.contains(q))), identity, identity, debounce = 0.0,
      segments = segments, groupOf = group, extraField = extra, heading = heading))

  /** Where the field, the chips and the list are, and how tall the picker is. */
  private def places(p: FilterPicker[String]): (Double, Double, Double, Double, Double) = fx((p.field.position.left(),
    p.field.position.top(), p.chips.position.top(), p.list.position.top(), p.size.height()))

  private def onScreen[T](test: => T): T = {
    fx {
      ui.children.clear()
      val c = new Component(Nodes.container())
      c.focusable @= true
      c.size.width @= 100.0
      c.size.height @= 40.0
      ui.children += c
      c.focus()
    }
    try test finally fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A picker's heading" should {
    "take no room as None or with no words, and come and go as its words do" in onScreen {
      val plain = picker(None)
      fx(plain.open())
      eventually(plain.shown().size == 4) shouldBe true
      fx(plain.headingLine) shouldBe None
      val without = places(plain)
      fx(plain.close())
      val words = Var("")
      val titled = picker(Some(words))
      fx(titled.open())
      eventually(titled.shown().size == 4) shouldBe true
      val line = titled.headingLine.get
      fx(line.visible()) shouldBe false
      places(titled) shouldBe without
      fx(words @= "Current: Auto")
      fx(line.visible()) shouldBe true
      eventually(titled.chips.position.top() > without._3) shouldBe true
      fx(words @= "")
      fx(line.visible()) shouldBe false
      places(titled) shouldBe without
    }
    "show its words muted under the field, over the chips, follow them and wrap to the picker's width" in onScreen {
      val words = Var("Current: Auto")
      val p = picker(Some(words))
      fx(p.open())
      val line = p.headingLine.get
      fx(line.content()) shouldBe "Current: Auto"
      fx(line.color()) shouldBe fx(everyui.theme.Theme.muted())
      fx(line.position.top()) shouldBe fx(p.field.position.top() + p.field.size.height() + 8.0)
      fx(line.position.left()) shouldBe fx(p.chips.position.left())
      val one = fx(line.size.height())
      one should be > 0.0
      fx(p.chips.position.top()) shouldBe fx(line.position.top() + one + 8.0)
      fx(p.list.position.top()) shouldBe fx(p.chips.position.top() + p.chips.size.height() + 10.0)
      fx(words @= "Showing: src/main/scala")
      fx(line.content()) shouldBe "Showing: src/main/scala"
      fx(p.field.accessibleDescription()) shouldBe "Showing: src/main/scala"
      // and the platform's field in it, which is where a screen reader is
      fx(p.field.children().toList.collect { case i: InputView => i.accessibleDescription() }) shouldBe
        List("Showing: src/main/scala")
      // many words wrap within the picker, the chips and the list moved down under them
      fx(words @= List.fill(30)("Showing: src/main/scala").mkString(" "))
      eventually(line.size.height() > one * 2.0) shouldBe true
      fx(line.size.width()) shouldBe fx(p.size.width() - 24.0)
      fx(line.position.left() + line.size.width()) should be <= fx(p.size.width())
      fx(p.chips.position.top()) shouldBe fx(line.position.top() + line.size.height() + 8.0)
      fx(words @= "")
      fx(p.field.accessibleDescription()) shouldBe ""
    }
    "sit under the second field where there is one" in onScreen {
      val p = picker(Some(Val("Current: Auto")), Some(PickerField.fileMask(Var(""))))
      fx(p.open())
      val extra = p.extraInput.get
      val line = p.headingLine.get
      fx(line.position.top()) shouldBe fx(extra.position.top() + extra.size.height() + 8.0)
      fx(p.chips.position.top()) shouldBe fx(line.position.top() + line.size.height() + 8.0)
    }
    "never be focused nor counted among the matches, the arrows and Tab going past it" in onScreen {
      val p = picker(Some(Val("Current: Auto")))
      fx(p.open())
      eventually(p.shown().size == 4) shouldBe true
      val line = p.headingLine.get
      fx(line.focusable()) shouldBe false
      fx(p.list.count) shouldBe 4
      fx(Focus.focused()) shouldBe Some(p.field)
      fx(Focus.move(Key.ArrowDown))
      fx(Focus.focused()) shouldBe Some(fx(p.chips.children().head))
      fx(Focus.move(Key.ArrowDown))
      fx(Focus.focused()) shouldBe Some(p.list)
      fx(Focus.move(Key.ArrowUp))
      fx(Focus.move(Key.ArrowUp))
      fx(Focus.focused()) shouldBe Some(p.field)
      fx {
        Focus.key("Tab", true)
        Focus.key("Tab", false)
      }
      fx(Focus.focused()) shouldBe Some(p.field)
      fx(p.segment()) shouldBe "mode"
      fx(Focus.focused().contains(line)) shouldBe false
    }
    "leave the field where it is without one" in onScreen {
      val plain = picker(None)
      fx(plain.open())
      val (left, top) = fx((plain.field.position.left(), plain.field.position.top()))
      fx(plain.close())
      val p = picker(Some(Val("Current: Auto")))
      fx(p.open())
      fx((p.field.position.left(), p.field.position.top())) shouldBe (left, top)
      fx(p.field.size.width()) shouldBe fx(plain.field.size.width())
    }
  }
}
