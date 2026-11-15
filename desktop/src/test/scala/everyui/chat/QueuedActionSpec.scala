package everyui.chat

import everyui.component.Component
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** The app's own actions on each queued message (`Composer.queuedActions`), on the desktop's real nodes with real
  * JavaFX key events: buttons on each chip before Edit and Remove, made only where the app gives some and made again
  * as it changes; a press runs one, the message leaving the queue only where it answers so; and the keys going from a
  * chip onto them, along them, and back, the focus on the chip that took the place of one sent. */
class QueuedActionSpec extends FxSpec {
  private var made: Option[Composer] = None

  // a composer with A, B and C queued while a reply comes
  private def three(): Composer = {
    val c = fx {
      made.foreach(_.dispose())
      val c = new Composer(_ => (), touch = false)
      made = Some(c)
      c.queueWhileBusy @= true
      c.size.width @= 700.0
      ui.children.clear()
      Focus.focused @= None
      ui.children += c
      c.busy @= true
      c.queue @= List(Draft("A", Nil), Draft("B", Nil), Draft("C", Nil))
      c
    }
    c
  }

  private def press(code: KeyCode): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }

  private def texts(c: Composer): List[String] = fx(c.queue().map(_.text))
  // (read on the FX thread)
  private def chips(c: Composer): List[QueueChip] = {
    def find(at: Component): Option[DraftQueue] = at match {
      case q: DraftQueue => Some(q)
      case other => other.childComponents.iterator.map(find).collectFirst { case Some(q) => q }
    }
    find(c).map(_.all).getOrElse(Nil)
  }
  private def click(c: Component): Unit = fx(c.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
  private def on(c: Component): Boolean = Focus.focused().exists(_ eq c)

  "A queued message's own actions" should {
    "show before Edit and Remove only where the app gives some, and leave the queue only where they say" in {
      val c = three()
      try {
        fx(chips(c).map(_.actionButtons.size)) shouldBe List(0, 0, 0)
        val plain = fx(chips(c).head.childComponents.size)
        var ran: List[String] = Nil
        fx(c.queuedActions @= (d => if (d.text == "C") Nil else List(
          QueuedAction(MaterialIcon.Send, "Send now", () => { ran = ran :+ ("now " + d.text); true }),
          QueuedAction(MaterialIcon.Schedule, "Later", () => { ran = ran :+ ("later " + d.text); false }))))
        fx(chips(c).map(_.actionButtons.size)) shouldBe List(2, 2, 0)
        fx(chips(c)(2).childComponents.size) shouldBe plain
        val a = fx(chips(c).head)
        fx(a.actionButtons.map(_.semantics().label)) shouldBe Vector("Send now", "Later")
        // in order before Edit, which stays where it was, each in the chip
        val xs = fx(a.actionButtons.map(_.position.left()) :+ a.editButton.position.left())
        xs shouldBe xs.sorted
        fx(a.editButton.position.left()) shouldBe fx(a.size.width() - 58.0 +- 0.5)
        fx(a.actionButtons.head.position.left()) should be > 26.0
        // Later stays; Send now takes it off
        click(fx(a.actionButtons(1)))
        ran shouldBe List("later A")
        texts(c) shouldBe List("A", "B", "C")
        click(fx(a.actionButtons(0)))
        ran shouldBe List("later A", "now A")
        texts(c) shouldBe List("B", "C")
        fx(a.takenApart) shouldBe true
        // taken away again: the chips as they were
        fx(c.queuedActions @= (_ => Nil))
        fx(chips(c).map(_.actionButtons.size)) shouldBe List(0, 0)
        fx(chips(c).head.childComponents.size) shouldBe plain
      } finally fx {
        ui.children.clear()
        made.foreach(_.dispose())
        made = None
        Focus.focused @= None
      }
    }

    "be reached from the chip by the keys, along and back, the focus kept as one leaves the queue" in {
      val c = three()
      try {
        var ran: List[String] = Nil
        fx(c.queuedActions @= (d => List(
          QueuedAction(MaterialIcon.Send, "Send now", () => { ran = ran :+ ("now " + d.text); true }),
          QueuedAction(MaterialIcon.StopCircle, "Stop and send", () => { ran = ran :+ ("stop " + d.text); true }))))
        fx(chips(c).head.focus())
        def chip(i: Int): QueueChip = chips(c)(i)
        press(KeyCode.RIGHT)
        eventually(on(chip(0).actionButtons(0))) shouldBe true
        press(KeyCode.RIGHT)
        eventually(on(chip(0).actionButtons(1))) shouldBe true
        // nothing past the last
        press(KeyCode.RIGHT)
        fx(on(chip(0).actionButtons(1))) shouldBe true
        press(KeyCode.LEFT)
        eventually(on(chip(0).actionButtons(0))) shouldBe true
        press(KeyCode.LEFT)
        eventually(on(chip(0))) shouldBe true
        // Up, Down and Escape back to the chip too
        press(KeyCode.RIGHT)
        eventually(on(chip(0).actionButtons(0))) shouldBe true
        press(KeyCode.DOWN)
        eventually(on(chip(0))) shouldBe true
        press(KeyCode.RIGHT)
        eventually(on(chip(0).actionButtons(0))) shouldBe true
        press(KeyCode.ESCAPE)
        eventually(on(chip(0))) shouldBe true
        // Enter on Stop and send: it goes, and the focus is on the chip in its place
        press(KeyCode.RIGHT)
        press(KeyCode.RIGHT)
        eventually(on(chip(0).actionButtons(1))) shouldBe true
        press(KeyCode.ENTER)
        eventually(ran == List("stop A")) shouldBe true
        texts(c) shouldBe List("B", "C")
        eventually(on(chip(0))) shouldBe true
        fx(chip(0).draft.text) shouldBe "B"
      } finally fx {
        ui.children.clear()
        made.foreach(_.dispose())
        made = None
        Focus.focused @= None
      }
    }
  }
}
