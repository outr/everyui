package everyui.picker

import everyui.async.Task
import everyui.component.*
import everyui.focus.Focus
import everyui.render.Nodes
import everyui.text.TextRun
import everyui.{FxSpec, ui}

/** The filtering picker: the query asked once the typing pauses, a stale answer dropped, tens of thousands of matches
  * made a screenful at a time, the arrows and Enter from the field, Escape; and the matching and lighting of letters. */
class FilterPickerSpec extends FxSpec {
  private def press(key: String): Unit = fx {
    Focus.key(key, true)
    Focus.key(key, false)
  }

  private val names: Vector[String] = Vector.tabulate(50000)(i => "File" + i + ".scala")

  private def onScreen[T](test: Component => T): T = {
    val card = fx {
      ui.children.clear()
      val c = new Component(Nodes.container())
      c.focusable @= true
      c.size.width @= 100.0
      c.size.height @= 40.0
      ui.children += c
      c.focus()
      c
    }
    try test(card) finally fx {
      while (Modal.showing) Modal.close()
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A filtering picker" should {
    "ask once the typing pauses, and drop an answer to words since changed" in onScreen { _ =>
      var asked: List[String] = Nil
      var answers: Map[String, Vector[String] => Unit] = Map.empty
      val picker = fx(new FilterPicker[String](q => Task.async[Vector[String]] { done =>
        asked = asked :+ q
        answers = answers + (q -> done)
      }, identity, identity, debounce = 0.05))
      fx(picker.open())
      eventually(asked == List("")) shouldBe true
      fx {
        picker.field.value @= "F"
        picker.field.value @= "Fi"
      }
      eventually(asked == List("", "Fi")) shouldBe true
      fx(picker.field.value @= "Fil")
      eventually(asked.last == "Fil") shouldBe true
      fx(answers("Fil")(Vector("File1.scala")))
      fx(answers("Fi")(Vector("stale")))
      fx(picker.matches()) shouldBe Vector("File1.scala")
      fx(picker.answered()) shouldBe Some("Fil")
    }
    "hold tens of thousands of matches, making only what shows, and choose with the arrows and Enter" in onScreen { card =>
      var chose: List[String] = Nil
      val picker = fx(FilterPicker.show[String](q => Task.pure(names.filter(n => Match.spans(q, n).nonEmpty)),
        identity, identity)(n => chose = chose :+ n))
      eventually(picker.matches().size == 50000) shouldBe true
      fx(picker.list.rows.size) should be < 40
      fx(Focus.focused()) shouldBe Some(picker.field)
      press("ArrowDown")
      press("ArrowDown")
      fx(picker.list.currentItem) shouldBe Some("File2.scala")
      fx(picker.field.submitted @= picker.field.value())
      chose shouldBe List("File2.scala")
      fx(Modal.showing) shouldBe false
      fx(Focus.focused()) shouldBe Some(card)
    }
    "narrow as it is typed, and close on Escape with nothing chosen" in onScreen { card =>
      var chose: List[String] = Nil
      val picker = fx(FilterPicker.show[String](q => Task.pure(names.filter(_.contains(q))),
        identity, identity)(n => chose = chose :+ n))
      fx(picker.field.value @= "e4999.")
      eventually(picker.answered().contains("e4999.")) shouldBe true
      fx(picker.matches()) shouldBe Vector("File4999.scala")
      fx(picker.list.currentItem) shouldBe Some("File4999.scala")
      press("Escape")
      fx(Modal.showing) shouldBe false
      chose shouldBe Nil
      fx(Focus.focused()) shouldBe Some(card)
    }
  }

  "Matching" should {
    "find the query's letters in order, as runs, and light them" in {
      Match.spans("gfp", "GoFilePicker") shouldBe Some(List((0, 1), (2, 3), (6, 7)))
      Match.spans("file", "GoFilePicker") shouldBe Some(List((2, 6)))
      Match.spans("xyz", "GoFilePicker") shouldBe None
      Match.spans("", "GoFilePicker") shouldBe Some(Nil)
      val runs = fx(HighlightRow.runs("GoFile", List((2, 6)), 14.0))
      runs.map(_.text) shouldBe List("Go", "File")
      runs.map(_.weight > 400) shouldBe List(false, true)
    }
  }
}
