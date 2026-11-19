package everyui.chat

import everyui.component.Component
import everyui.component.types.Semantics
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** The app's own chips in a composer's row of attachments (`Composer.extraChips`), on the desktop's real nodes: the row
  * shown for them alone and hidden without them, after the files; a press handing over the chip and its × taking it
  * off, neither while the composer is out of use; never sent; changed in place by key; named for a screen reader; and
  * reached from the field by Up and left by Down. */
class ComposerChipsSpec extends FxSpec {
  private var sent: List[Draft] = Nil
  private var pressed: List[Component] = Nil
  private var removed = 0

  private def make(): Composer = fx {
    sent = Nil
    pressed = Nil
    removed = 0
    val c = new Composer(d => sent = sent :+ d, touch = false)
    ui.children.clear()
    Focus.focused @= None
    ui.children += c
    c.focusField()
    c
  }

  private def place(label: String, removable: Boolean = true): ComposerChip =
    ComposerChip("location", MaterialIcon.Place, label, onPress = c => pressed = pressed :+ c,
      onRemove = if (removable) Some(() => removed += 1) else None)

  private def press(code: KeyCode): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }

  private def click(c: Component): Unit = fx(c.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))

  // every `T` under `c`, in tree order
  private def under[T](c: Component)(using t: scala.reflect.ClassTag[T]): Vector[T] = {
    var found = Vector.empty[T]
    var pending = List(c)
    while (pending.nonEmpty) {
      val at = pending.head
      pending = at.childComponents.toList ::: pending.tail
      at match {
        case x: T => found = found :+ x
        case _ => ()
      }
    }
    found
  }

  "A composer's own chips" should {
    "show the row for them alone, after the files, and hide it without them" in {
      val c = make()
      val bare = fx(c.size.height())
      fx(c.chipsShown()) shouldBe false
      fx(c.extraChips @= List(place("Lisbon")))
      fx(c.chipsShown()) shouldBe true
      val chips = fx(under[ComposerChipView](c))
      chips.map(v => fx(v.chip().label)) shouldBe Vector("Lisbon")
      fx(c.size.height()) should be > bare
      // after the files, in the same row
      fx(c.attach(Attachment("notes.txt", 12L, "text/plain", everyui.async.Task(Array.emptyByteArray))))
      val row = fx(under[ComposerChipView](c).head.parent().get)
      fx(row.childComponents.map(_.kind).toVector) shouldBe Vector("AttachmentChip", "ComposerChipView(location)")
      fx(c.attachments @= Nil)
      fx(c.chipsShown()) shouldBe true
      fx(c.extraChips @= Nil)
      fx(c.chipsShown()) shouldBe false
      fx(under[ComposerChipView](c)) shouldBe Vector.empty
      fx(c.size.height()) shouldBe (bare +- 0.5)
    }

    "hand the chip to its press, take it off by its ×, and do neither out of use" in {
      val c = make()
      fx(c.extraChips @= List(place("Lisbon")))
      val chip = fx(under[ComposerChipView](c).head)
      click(chip.body)
      pressed shouldBe List(chip)
      chip.removal.nonEmpty shouldBe true
      click(chip.removal.get)
      removed shouldBe 1
      fx(c.enabled @= false)
      click(chip.body)
      click(chip.removal.get)
      pressed.length shouldBe 1
      removed shouldBe 1
      // no × where it cannot be taken off
      fx(c.enabled @= true)
      fx(c.extraChips @= List(place("Lisbon", removable = false)))
      fx(under[ComposerChipView](c).head.removal) shouldBe None
    }

    "never go with a message, staying with the draft until the app clears them" in {
      val c = make()
      fx(c.extraChips @= List(place("Lisbon")))
      fx(c.sendable()) shouldBe false
      fx(c.text @= "Hello")
      fx(c.send())
      sent shouldBe List(Draft("Hello", Nil))
      fx(c.extraChips().map(_.label)) shouldBe List("Lisbon")
      fx(c.chipsShown()) shouldBe true
    }

    "be changed in place by key, the others kept" in {
      val c = make()
      val tag = ComposerChip("tag", MaterialIcon.Label, "Urgent")
      fx(c.extraChips @= List(place("Lisbon"), tag))
      val Vector(first, second) = fx(under[ComposerChipView](c))
      fx(c.extraChips @= List(place("Porto", removable = false), tag))
      val Vector(again, still) = fx(under[ComposerChipView](c))
      (again eq first) shouldBe true
      (still eq second) shouldBe true
      fx(again.chip().label) shouldBe "Porto"
      fx(again.body.semantics()) shouldBe Semantics.button("Porto")
      fx(again.removal) shouldBe None
      // one gone is taken apart, and the rest left as they are
      fx(c.extraChips @= List(tag))
      fx(first.takenApart) shouldBe true
      (fx(under[ComposerChipView](c).head) eq second) shouldBe true
      fx(second.takenApart) shouldBe false
    }

    "be named for a screen reader: a button by its label, its × Remove and the label" in {
      val c = make()
      fx(c.extraChips @= List(place("Lisbon").copy(tooltip = Some("Where this message is sent from"))))
      val chip = fx(under[ComposerChipView](c).head)
      fx(chip.body.semantics()) shouldBe Semantics.button("Lisbon")
      fx(chip.removal.get.semantics()) shouldBe Semantics.button("Remove Lisbon")
      fx(chip.body.tooltip()) shouldBe "Where this message is sent from"
    }

    "be reached from the field by Up and left by Down, its × beside it" in {
      val c = make()
      fx(c.extraChips @= List(place("Lisbon")))
      val chip = fx(under[ComposerChipView](c).head)
      eventually(Focus.focused().contains(c.field)) shouldBe true
      press(KeyCode.UP)
      eventually(Focus.focused().exists(_ eq chip.body)) shouldBe true
      press(KeyCode.RIGHT)
      eventually(Focus.focused().exists(f => chip.removal.exists(_ eq f))) shouldBe true
      press(KeyCode.DOWN)
      eventually(Focus.focused().contains(c.field)) shouldBe true
      // Enter on the chip presses it
      press(KeyCode.UP)
      eventually(Focus.focused().exists(_ eq chip.body)) shouldBe true
      press(KeyCode.ENTER)
      eventually(pressed == List(chip)) shouldBe true
      // with no chips, Up stays in the field
      fx(c.field.focus())
      fx(c.extraChips @= Nil)
      press(KeyCode.UP)
      fx(Focus.focused()) shouldBe Some(c.field)
    }
  }
}
