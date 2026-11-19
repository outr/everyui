package everyui.picker

import everyui.async.Task
import everyui.component.*
import everyui.emoji.EmojiPicker
import everyui.event.Pointer
import everyui.focus.Focus
import everyui.kit.SearchField
import everyui.render.{Host, Nodes, Platform}
import everyui.{FxSpec, ui}
import javafx.scene.control.TextInputControl
import javafx.scene.input.{MouseButton, MouseEvent}

/** Where a picker's focus starts as it opens: in its field where a fine pointer or a remote is at hand, to be typed
  * into at once; on its list (or grid) on a phone, or where a finger opened it, so no keyboard comes up until the
  * field is tapped. Back closes it either way, a row tapped at once is chosen, and an app may say which. */
class PickerFocusSpec extends FxSpec {
  private val models = Vector("llama", "qwen", "opus", "sonnet", "gpt")

  private def inside(c: Component): List[Component] = c.childComponents.toList.flatMap(x => x :: inside(x))

  // on a platform as `args` say (pointer=false: a phone's coarse touch; remote=true: a television)
  private def on[T](args: (String, String)*)(test: Component => T): T = {
    val was = fx(Host.args)
    val card = fx {
      Host.args = was ++ args
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
      Pointer.usedAt = -1.0e9
      Pointer.finger = false
      Host.args = was
    }
  }

  private def make(segments: List[Segment] = Nil): FilterPicker[String] =
    fx(new FilterPicker[String](q => Task.pure(models.filter(_.contains(q))), identity, identity, debounce = 0.0,
      segments = segments, groupOf = (_: String) => "all"))

  private def back(): Unit = fx {
    Focus.key("Back", true)
    Focus.key("Back", false)
  }

  // a finger's tap on the field's own control, as a touch screen's comes to JavaFX (synthesized from the touch)
  private def tap(field: TextInput): Unit = fx {
    val control = field.field.get.node.fx.asInstanceOf[TextInputControl]
    for (kind <- List(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED))
      javafx.event.Event.fireEvent(control, new MouseEvent(kind, 4.0, 4.0, 4.0, 4.0, MouseButton.PRIMARY, 1, false,
        false, false, false, kind == MouseEvent.MOUSE_PRESSED, false, false, true, false,
        kind == MouseEvent.MOUSE_CLICKED, null))
    control.requestFocus()
  }

  "A filtering picker" should {
    "open with the focus in its field where a fine pointer is at hand" in on() { _ =>
      val picker = make()
      fx(picker.open())
      fx(Focus.focused()) shouldBe Some(picker.field)
    }
    "open with the focus in its field on a television" in on("pointer" -> "false", "remote" -> "true") { _ =>
      val picker = make()
      fx(picker.open())
      fx(Focus.focused()) shouldBe Some(picker.field)
    }
    "open on its list on a phone, the field focused once it is tapped, and close on Back" in
      on("pointer" -> "false") { card =>
        fx(Platform.pointerSupport()) shouldBe false
        val picker = make()
        fx(picker.open())
        eventually(picker.list.count == 5) shouldBe true
        fx(Focus.focused()) shouldBe Some(picker.list)
        fx(picker.field.focused()) shouldBe false
        tap(picker.field)
        eventually(Focus.focused().contains(picker.field)) shouldBe true
        back()
        fx(Modal.showing) shouldBe false
        fx(Focus.focused()) shouldBe Some(card)
      }
    "close on Back where it opened on its list" in on("pointer" -> "false") { card =>
      val picker = make()
      fx(picker.open())
      back()
      fx(Modal.showing) shouldBe false
      fx(Focus.focused()) shouldBe Some(card)
    }
    "choose a row tapped as soon as it opens on its list" in on("pointer" -> "false") { _ =>
      var chose: List[String] = Nil
      val picker = make()
      fx(picker.chosen.attach(s => chose = chose :+ s))
      fx(picker.open())
      eventually(picker.list.count == 5) shouldBe true
      val (x, y) = fx((picker.list.absoluteLeft + 40.0, picker.list.absoluteTop + picker.list.top(2) + 10.0))
      fx(picker.list.clickAt(x, y, everyui.event.Modifiers(false, false, false, false)))
      chose shouldBe List("opus")
      fx(Modal.showing) shouldBe false
    }
    "open on its list where a finger opened it, on a desktop with a touch screen" in on() { _ =>
      val picker = make()
      fx {
        Pointer.usedAt = Platform.ticks()
        Pointer.finger = true
        picker.open()
      }
      fx(Focus.focused()) shouldBe Some(picker.list)
    }
    "open in its field where the mouse opened it" in on() { _ =>
      val picker = make()
      fx {
        Pointer.usedAt = Platform.ticks()
        Pointer.finger = false
        picker.open()
      }
      fx(Focus.focused()) shouldBe Some(picker.field)
    }
    "focus its field, or not, as the app says" in on("pointer" -> "false") { _ =>
      val typed = make()
      fx(typed.open(focusField = true))
      fx(Focus.focused()) shouldBe Some(typed.field)
      fx(Modal.close())
      fx(Host.args = Host.args - "pointer")
      val listed = fx(FilterPicker.show[String](_ => Task.pure(models), identity, identity, focusField = false)(
        _ => ()))
      fx(Focus.focused()) shouldBe Some(listed.list)
    }
    "leave the field alone when a chip is tapped, opened on its list" in on("pointer" -> "false") { _ =>
      val picker = make(List(Segment.all("All"), Segment("all", "Mine")))
      fx(picker.open())
      eventually(picker.shown().size == 5) shouldBe true
      val chip = fx(picker.chips.children()(1))
      fx {
        Pointer.usedAt = Platform.ticks()
        Pointer.finger = true
        chip.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click, 0.0, 0.0, finger = true)
      }
      fx(picker.segment()) shouldBe "all"
      // (the hand back would come on the next turn)
      fx(())
      fx(picker.field.focused()) shouldBe false
    }
  }

  "An emoji picker" should {
    def inSearch(p: EmojiPicker): Boolean = fx {
      val search = inside(p).collectFirst { case s: SearchField => s }.get
      Focus.focused().exists(f => Focus.within(f, search))
    }

    "open with the focus in its search where a fine pointer is at hand" in on() { card =>
      val picker = fx(EmojiPicker.open(card, _ => ()))
      inSearch(picker) shouldBe true
    }
    "open on its grid on a phone, and close on Back" in on("pointer" -> "false") { card =>
      val picker = fx(EmojiPicker.open(card, _ => ()))
      inSearch(picker) shouldBe false
      fx(Focus.focused().exists(f => Focus.within(f, picker))) shouldBe true
      back()
      fx(Modal.showing) shouldBe false
    }
    "open in its search on a phone where the app says" in on("pointer" -> "false") { card =>
      val picker = fx(EmojiPicker.open(card, _ => (), focusField = true))
      inSearch(picker) shouldBe true
    }
  }
}
