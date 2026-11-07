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

  private def inside(c: Component): List[Component] = c.childComponents.toList.flatMap(x => x :: inside(x))

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
    "choose a row with one click of a mouse, the list scrolled first" in onScreen { _ =>
      var picked: List[String] = Nil
      val picker = fx(new FilterPicker[String](_ => Task.pure(Vector.tabulate(60)(i => "Model " + i)), identity,
        identity, debounce = 0.0))
      fx(picker.chosen.attach(s => picked = picked :+ s))
      fx(picker.open())
      eventually(picker.list.count == 60) shouldBe true
      fx(picker.list.scrollBy(150.0))
      val (key, x, y) = fx {
        val i = picker.list.indexAt(picker.list.offset() + 60.0)
        (picker.list.keyAt(i), picker.list.absoluteLeft + 40.0, picker.list.absoluteTop + picker.list.top(i) -
          picker.list.offset() + 10.0)
      }
      fx(picker.list.clickAt(x, y, everyui.event.Modifiers(false, false, false, false)))
      picked shouldBe List(key)
      fx(Modal.showing) shouldBe false
    }
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

  "A picker with segments" should {
    val models = Vector("llama:local", "qwen:local", "opus:anthropic", "sonnet:anthropic", "gpt:openai")
    def group(m: String): String = m.substring(m.indexOf(':') + 1)
    def make(): FilterPicker[String] = fx(new FilterPicker[String](q => Task.pure(models.filter(_.contains(q))),
      identity, identity, debounce = 0.0,
      segments = List(Segment.all("All"), Segment("local", "Local"), Segment("anthropic", "Anthropic"),
        Segment("openai", "OpenAI")), groupOf = group))

    "show every match under All, the first segment, and narrow on top of the query by the chip chosen" in onScreen { _ =>
      val picker = make()
      fx(picker.open())
      eventually(picker.shown().size == 5) shouldBe true
      fx(picker.segment()) shouldBe ""
      fx(picker.chips.children().size) shouldBe 4
      val anthropic = fx(picker.chips.children()(2))
      fx(anthropic.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click, 0.0, 0.0))
      fx(picker.segment()) shouldBe "anthropic"
      fx(picker.shown()) shouldBe Vector("opus:anthropic", "sonnet:anthropic")
      fx(picker.list.currentItem) shouldBe Some("opus:anthropic")
      fx(picker.field.value @= "son")
      eventually(picker.answered().contains("son")) shouldBe true
      fx(picker.shown()) shouldBe Vector("sonnet:anthropic")
      fx(picker.matches()) shouldBe Vector("sonnet:anthropic")
      fx(picker.field.value @= "l")
      eventually(picker.answered().contains("l")) shouldBe true
      fx(picker.shown()) shouldBe Vector.empty
      fx(picker.matches().size) shouldBe 2
    }
    "go round the segments with Tab and Shift+Tab from the field, the focus kept there" in onScreen { _ =>
      val picker = make()
      fx(picker.open())
      eventually(picker.shown().size == 5) shouldBe true
      press("Tab")
      fx(picker.segment()) shouldBe "local"
      fx(picker.shown()) shouldBe Vector("llama:local", "qwen:local")
      fx(Focus.focused()) shouldBe Some(picker.field)
      press("Tab")
      press("Tab")
      press("Tab")
      fx(picker.segment()) shouldBe ""
      fx(picker.field.event.key.down @= everyui.event.KeyEvent(everyui.event.Key.Tab, "Tab", false, false, false, true))
      fx(picker.segment()) shouldBe "openai"
      fx(picker.shown()) shouldBe Vector("gpt:openai")
    }
    "land on the chosen chip, move along the chips with the arrows, and choose with Enter" in onScreen { _ =>
      val picker = make()
      fx(picker.open())
      eventually(picker.shown().size == 5) shouldBe true
      val chips = fx(picker.chips.children())
      fx(chips.head.focus())
      press("ArrowRight")
      fx(Focus.focused()) shouldBe Some(chips(1))
      press("Enter")
      fx(picker.segment()) shouldBe "local"
      press("ArrowDown")
      fx(Focus.focused()) shouldBe Some(picker.list)
    }
    "show a match in each group it is in, where it names several" in onScreen { _ =>
      val recent = Set("qwen:local", "gpt:openai")
      val picker = fx(new FilterPicker[String](q => Task.pure(models.filter(_.contains(q))), identity, identity,
        debounce = 0.0,
        segments = List(Segment.all("All"), Segment("recent", "Recent"), Segment("local", "Local")),
        groupsOf = Some((m: String) => Set(group(m)) ++ (if (recent.contains(m)) Set("recent") else Set.empty))))
      fx(picker.open())
      eventually(picker.shown().size == 5) shouldBe true
      fx(picker.segment @= "recent")
      fx(picker.shown()) shouldBe Vector("qwen:local", "gpt:openai")
      fx(picker.segment @= "local")
      fx(picker.shown()) shouldBe Vector("llama:local", "qwen:local")
    }
    "give the focus back to the field when a chip is clicked, keep it on one chosen with Enter" in onScreen { _ =>
      val picker = make()
      fx(picker.open())
      eventually(picker.shown().size == 5) shouldBe true
      val chip = fx(picker.chips.children()(1))
      fx(chip.focus())
      fx {
        everyui.event.Pointer.usedAt = everyui.render.Platform.ticks()
        chip.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click, 0.0, 0.0)
      }
      fx(picker.segment()) shouldBe "local"
      eventually(Focus.focused().contains(picker.field)) shouldBe true
      // by the keys, the focus stays on the chip
      fx(everyui.event.Pointer.usedAt = -1.0e9)
      val next = fx(picker.chips.children()(3))
      fx(next.focus())
      press("Enter")
      fx(picker.segment()) shouldBe "openai"
      fx(Focus.focused()) shouldBe Some(next)
    }
    "draw a second line under each row's words where given, the rows two lines tall" in onScreen { _ =>
      val picker = fx(new FilterPicker[String](q => Task.pure(models), identity, identity, debounce = 0.0,
        subtitleOf = Some((m: String) => "by " + group(m))))
      fx(picker.open())
      eventually(picker.shown().size == 5) shouldBe true
      fx(picker.list.rowHeight) shouldBe HighlightRow.twoLines
      eventually(picker.list.children().exists(r => inside(r).exists {
        case t: TextView => t.content() == "by local" && t.visible()
        case _ => false
      })) shouldBe true
    }
    "leave a picker without segments as it was: no chips, every match shown" in onScreen { _ =>
      val picker = fx(new FilterPicker[String](q => Task.pure(models), identity, identity, debounce = 0.0))
      fx(picker.open())
      eventually(picker.shown().size == 5) shouldBe true
      fx(picker.chips.children().isEmpty) shouldBe true
      fx(picker.list.position.top()) shouldBe fx(picker.field.size.height() + 16.0)
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
