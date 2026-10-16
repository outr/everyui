package everyui.picker

import everyui.async.Task
import everyui.component.*
import everyui.component.types.{Autofill, TextDirection}
import everyui.event.{Key, KeyEvent, Keys, PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.keymap.Keymap
import everyui.render.Nodes
import everyui.{FxSpec, ui}
import reactify.*

/** A picker's options: toggle buttons and a second field, each shown for its segments, which ask the query again as
  * they change, turned over by their chords, reached by the arrows, mirrored right to left; and a picker without them
  * as it was. */
class PickerOptionsSpec extends FxSpec {
  private val hits = Vector("Main.scala:file", "Picker.scala:file", "hello:message", "Plans:conversation")
  private def group(h: String): String = h.substring(h.indexOf(':') + 1)
  private val segments = List(Segment.all("All"), Segment("file", "Files"), Segment("conversation", "Conversations"),
    Segment("message", "Messages"))

  /** A Search Everywhere: Match case, Regex and Whole word, and a file mask, for Files; every ask heard with the
    * options it was asked with. */
  private class Find {
    val matchCase: Var[Boolean] = Var(false)
    val regex: Var[Boolean] = Var(false)
    val whole: Var[Boolean] = Var(false)
    val mask: Var[String] = Var("")
    var asked: List[(String, Boolean, Boolean, Boolean, String)] = Nil
    val picker: FilterPicker[String] = fx(new FilterPicker[String](q => {
      asked = asked :+ ((q, matchCase(), regex(), whole(), mask()))
      Task.pure(hits.filter(_.contains(q)))
    }, identity, identity, debounce = 0.0, segments = segments, groupOf = group,
      toggles = List(PickerToggle.matchCase(matchCase, Set("file")), PickerToggle.regex(regex, Set("file")),
        PickerToggle.wholeWord(whole, Set("file"))),
      extraField = Some(PickerField.fileMask(mask, Set("file")))))
    def buttons: Vector[Component] = picker.toggleBar.children()
    def shown: Vector[Boolean] = fx(buttons.map(_.visible()))
  }

  private def press(key: String): Unit = fx {
    Focus.key(key, true)
    Focus.key(key, false)
  }

  private def click(c: Component): Unit = fx(c.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))

  /** Alt and `letter`, as the keymap hears it (on a Mac, Cmd+Option and the letter). */
  private def chord(letter: String): Boolean = fx(Keymap.run(KeyEvent(Keys.of(letter), letter, alt = true)))

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
      Direction.set(false)
    }
  }

  "A picker's options" should {
    "show only while one of their segments is chosen, the field short of them then" in onScreen {
      val find = new Find
      fx(find.picker.open())
      eventually(find.picker.shown().size == 4) shouldBe true
      find.shown shouldBe Vector(false, false, false)
      fx(find.picker.toggleBar.size.width()) shouldBe 0.0
      val full = fx(find.picker.field.size.width())
      fx(find.picker.extraInput.get.visible()) shouldBe false
      fx(find.picker.segment @= "file")
      find.shown shouldBe Vector(true, true, true)
      fx(find.picker.toggleBar.size.width()) shouldBe 3 * ToggleButton.side + 2 * 4.0
      fx(find.picker.field.size.width()) shouldBe full - fx(find.picker.toggleBar.size.width()) - 6.0
      fx(find.picker.extraInput.get.visible()) shouldBe true
      // the options beside the field, on its middle, inside the picker
      val (fieldRight, barLeft, barRight, across) = fx((find.picker.field.position.left() +
        find.picker.field.size.width(), find.picker.toggleBar.position.left(), find.picker.toggleBar.position.left() +
        find.picker.toggleBar.size.width(), find.picker.size.width()))
      barLeft shouldBe fieldRight + 6.0
      barRight shouldBe across - 8.0
      fx(find.picker.toggleBar.position.top() + find.picker.toggleBar.size.height() / 2.0) shouldBe
        fx(find.picker.field.position.top() + find.picker.field.size.height() / 2.0)
      // the second field under the field, and the chips under it
      val extra = find.picker.extraInput.get
      fx(extra.position.top()) shouldBe fx(find.picker.field.size.height() + 16.0)
      fx(find.picker.chips.position.top()) shouldBe fx(extra.position.top() + extra.size.height() + 8.0)
      fx(find.picker.segment @= "message")
      find.shown shouldBe Vector(false, false, false)
      fx(find.picker.field.size.width()) shouldBe full
      fx(find.picker.chips.position.top()) shouldBe fx(find.picker.field.size.height() + 16.0)
    }
    "ask the query again with an option's new value as it is pressed, and as a chip shows or hides them" in onScreen {
      val find = new Find
      fx(find.picker.open())
      eventually(find.asked.nonEmpty) shouldBe true
      fx(find.picker.segment @= "file")
      eventually(find.asked.size == 2) shouldBe true
      click(fx(find.buttons.head))
      fx(find.matchCase()) shouldBe true
      eventually(find.asked.lastOption.contains(("", true, false, false, ""))) shouldBe true
      click(fx(find.buttons(1)))
      eventually(find.asked.lastOption.contains(("", true, true, false, ""))) shouldBe true
      // OK on one turns it over too
      fx(find.buttons(2).focus())
      press("Enter")
      fx(find.whole()) shouldBe true
      eventually(find.asked.lastOption.contains(("", true, true, true, ""))) shouldBe true
      val before = find.asked.size
      // a chip that shows the same options asks nothing more
      fx(find.picker.segment @= "message")
      eventually(find.asked.size == before + 1) shouldBe true
      fx(find.picker.segment @= "conversation")
      fx(find.asked.size) shouldBe before + 1
      // said as toggle buttons, pressed or not, named with their chords in their tooltips
      fx(find.buttons.head.semantics()) shouldBe types.Semantics.toggle(PickerWords.matchCase, true)
      fx(find.buttons.head.tooltip()) shouldBe "Match case (Alt+C)"
    }
    "turn over with their chords from anywhere in the picker, only while they show, and leave the keys as it closes" in
      onScreen {
        val find = new Find
        val before = fx(Keymap.global.commands().size)
        fx(find.picker.open())
        eventually(find.asked.nonEmpty) shouldBe true
        chord("c") shouldBe false
        fx(find.matchCase()) shouldBe false
        fx(find.picker.segment @= "file")
        chord("c") shouldBe true
        chord("r") shouldBe true
        chord("w") shouldBe true
        fx((find.matchCase(), find.regex(), find.whole())) shouldBe (true, true, true)
        eventually(find.asked.lastOption.contains(("", true, true, true, ""))) shouldBe true
        // from the list too
        fx(find.picker.list.focus())
        chord("C") shouldBe true
        fx(find.matchCase()) shouldBe false
        // Alt+F to the file mask
        chord("f") shouldBe true
        fx(Focus.focused()) shouldBe find.picker.extraInput
        fx(find.picker.close())
        fx(Keymap.global.commands().size) shouldBe before
        chord("c") shouldBe false
        // Cmd+Option+C on a Mac is named by its key, not by the "ç" Option makes of it, so the Mac's chord matches
        def key(text: String, alt: Boolean, meta: Boolean): String = everyui.render.Host.keyName(
          new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED, text, text,
            javafx.scene.input.KeyCode.C, false, false, alt, meta))
        key("ç", alt = true, meta = true) shouldBe "c"
        key("ç", alt = true, meta = false) shouldBe "ç"
        key("c", alt = false, meta = false) shouldBe "c"
      }
    "send the second field's words to the query, and say nothing to autofill from either field" in onScreen {
      val find = new Find
      fx(find.picker.open())
      fx(find.picker.segment @= "file")
      fx(find.picker.extraInput.get.value @= "*.scala")
      fx(find.mask()) shouldBe "*.scala"
      eventually(find.asked.lastOption.exists(_._5 == "*.scala")) shouldBe true
      fx(find.mask @= "*.md")
      fx(find.picker.extraInput.get.value()) shouldBe "*.md"
      eventually(find.asked.lastOption.exists(_._5 == "*.md")) shouldBe true
      fx(find.picker.field.autofill()) shouldBe Some(Autofill.Off)
      fx(find.picker.extraInput.get.autofill()) shouldBe Some(Autofill.Off)
      fx(find.picker.extraInput.get.tooltip()) shouldBe "File mask (Alt+F)"
    }
    "hold the file mask literal, its words left to right in a right-to-left language" in onScreen {
      val find = new Find
      fx(Direction.set(true))
      fx(find.picker.open())
      fx(find.picker.segment @= "file")
      val extra = find.picker.extraInput.get
      fx(extra.literal()) shouldBe true
      fx(extra.value @= "*.scala")
      val inner = fx(extra.children().collectFirst { case i: InputView => i }.get)
      fx(inner.runs()) shouldBe TextDirection.LeftToRight
      fx(inner.node.fx.getNodeOrientation) shouldBe javafx.geometry.NodeOrientation.LEFT_TO_RIGHT
      fx(find.picker.field.value @= "ملف")
      val main = fx(find.picker.field.children().collectFirst { case i: InputView => i }.get)
      fx(main.runs()) shouldBe TextDirection.Inherit
    }
    "be reached by the arrows: Right of the field to the options, then down to the second field, the chips, the list" in
      onScreen {
        val find = new Find
        fx(find.picker.open())
        fx(find.picker.segment @= "file")
        val buttons = fx(find.buttons)
        val extra = find.picker.extraInput.get
        val files = fx(find.picker.chips.children()(1))
        fx(Focus.focused()) shouldBe Some(find.picker.field)
        fx(Focus.move(Key.ArrowRight))
        fx(Focus.focused()) shouldBe Some(buttons.head)
        fx(Focus.move(Key.ArrowRight))
        fx(Focus.move(Key.ArrowRight))
        fx(Focus.focused()) shouldBe Some(buttons(2))
        fx(Focus.move(Key.ArrowRight))
        fx(Focus.focused()) shouldBe Some(buttons(2))
        fx(Focus.move(Key.ArrowLeft))
        fx(Focus.move(Key.ArrowLeft))
        fx(Focus.move(Key.ArrowLeft))
        fx(Focus.focused()) shouldBe Some(find.picker.field)
        fx(Focus.move(Key.ArrowRight))
        fx(Focus.move(Key.ArrowDown))
        fx(Focus.focused()) shouldBe Some(extra)
        fx(Focus.move(Key.ArrowDown))
        fx(Focus.focused()) shouldBe Some(files)
        fx(Focus.move(Key.ArrowDown))
        fx(Focus.focused()) shouldBe Some(find.picker.list)
        fx(Focus.move(Key.ArrowUp))
        fx(Focus.move(Key.ArrowUp))
        fx(Focus.focused()) shouldBe Some(extra)
        fx(Focus.move(Key.ArrowUp))
        fx(Focus.focused()) shouldBe Some(find.picker.field)
        // Tab on from an option: the next, then the second field
        fx(buttons(1).focus())
        press("Tab")
        fx(Focus.focused()) shouldBe Some(buttons(2))
        press("Tab")
        fx(Focus.focused()) shouldBe Some(extra)
        // with the options hidden, the ways are the chips' again, and something keeps the focus
        fx(buttons(2).focus())
        val messages = fx(find.picker.chips.children()(3))
        click(messages)
        fx(find.picker.segment()) shouldBe "message"
        fx(Focus.focused().exists(f => Focus.within(f, find.picker))) shouldBe true
        fx(find.picker.field.focus())
        fx(Focus.move(Key.ArrowRight))
        fx(Focus.focused()) shouldBe Some(find.picker.field)
        fx(Focus.move(Key.ArrowDown))
        fx(Focus.focused()) shouldBe Some(messages)
        fx(Focus.move(Key.ArrowUp))
        fx(Focus.focused()) shouldBe Some(find.picker.field)
      }
    "read from the right in a right-to-left language: the options at the field's left, Left to them" in onScreen {
      fx(Direction.set(true))
      val find = new Find
      fx(find.picker.open())
      fx(find.picker.segment @= "file")
      val (barLeft, barRight, fieldLeft, fieldRight, across) = fx((find.picker.toggleBar.position.left(),
        find.picker.toggleBar.position.left() + find.picker.toggleBar.size.width(),
        find.picker.field.position.left(), find.picker.field.position.left() + find.picker.field.size.width(),
        find.picker.size.width()))
      barLeft shouldBe 8.0
      fieldLeft shouldBe barRight + 6.0
      fieldRight shouldBe across - 8.0
      // the first option, Match case, nearest the field's start, on the right of the row
      val first = fx(find.buttons.head)
      fx(first.position.left()) should be > fx(find.buttons(2).position.left())
      fx(find.picker.field.focus())
      fx(Focus.move(Key.ArrowLeft))
      fx(Focus.focused()) shouldBe Some(first)
      fx(Focus.move(Key.ArrowRight))
      fx(Focus.focused()) shouldBe Some(find.picker.field)
    }
    "leave a picker without them as it was" in onScreen {
      val before = fx(Keymap.global.commands().size)
      val picker = fx(new FilterPicker[String](q => Task.pure(hits), identity, identity, debounce = 0.0,
        segments = segments, groupOf = group))
      fx(picker.open())
      eventually(picker.shown().size == 4) shouldBe true
      fx(picker.children().toList) shouldBe fx(List(picker.field, picker.chips, picker.list) :+ picker.children().last)
      fx(picker.extraInput) shouldBe None
      fx(picker.field.size.width()) shouldBe fx(picker.size.width() - 16.0)
      fx(picker.field.position.left()) shouldBe 8.0
      fx(picker.chips.position.top()) shouldBe fx(picker.field.size.height() + 16.0)
      fx(picker.list.position.top()) shouldBe fx(picker.field.size.height() + 16.0 + picker.chips.size.height() + 10.0)
      fx(Keymap.global.commands().size) shouldBe before
      fx(Focus.move(Key.ArrowDown))
      fx(Focus.focused()) shouldBe Some(fx(picker.chips.children().head))
    }
  }
}
