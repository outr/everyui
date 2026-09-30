package everyui.chat

import everyui.component.{Component, Container, Modal}
import everyui.event.{Pointer, PointerKind}
import everyui.focus.Focus
import everyui.menu.MenuRow
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** Copying from a transcript on the desktop, which selects no text: Ctrl+C copies the message under the cursor, a click
  * puts the cursor on the entry it lands on, and the context menu offers Copy message over a message and nothing (the
  * platform's own) over a day's separator. What the machine's clipboard held before is put back after. */
class TranscriptCopySpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val me = Participant("u-me", "Me")

  private def ctrl(code: KeyCode, text: String): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code, false, true, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, text, text, code, false, true, false, false))
  }

  // (on the FX thread)
  private def copied: String = Option(javafx.scene.input.Clipboard.getSystemClipboard.getString).getOrElse("")

  private def onScreen[T](c: Component)(test: => T): T = {
    val kept = fx {
      val clipboard = javafx.scene.input.Clipboard.getSystemClipboard
      val content = new javafx.scene.input.ClipboardContent
      clipboard.getContentTypes.forEach(t => Option(clipboard.getContent(t)).foreach(v => content.put(t, v)))
      ui.children.clear()
      ui.children += c
      content
    }
    try test finally fx {
      Modal.close()
      ui.children.clear()
      c.dispose()
      Focus.focused @= None
      javafx.scene.input.Clipboard.getSystemClipboard.setContent(kept)
    }
  }

  private def settle(t: Transcript): Unit = (1 to 5).foreach(_ => fx(t.list.refresh()))

  "A transcript" should {
    "copy the message under the cursor with Ctrl+C, put the cursor where a click lands, and offer Copy message" in {
      val entries = Var[Vector[ChatEntry]](Vector(
        ChatEntry.message("m0", ada, self = false, "Hello there", time = Some(1790000000000L)),
        ChatEntry.message("m1", me, self = true, "**Bold** words, as written", time = Some(1790000060000L))))
      val t = fx(new Transcript(entries))
      fx {
        t.size.width @= 600.0
        t.size.height @= 400.0
      }
      onScreen(t) {
        settle(t)
        fx(t.focus())
        fx(t.list.current @= Some("m1"))
        ctrl(KeyCode.C, "c")
        eventually(copied == "**Bold** words, as written") shouldBe true
        // a click on the first message puts the cursor there, where it is
        val first = fx(t.list.indexOf("m0"))
        val offset = fx(t.list.offset())
        fx(t.list.clickAt(t.list.absoluteLeft + 100.0, t.list.absoluteTop + t.list.top(first) - offset + 20.0,
          everyui.event.Modifiers(false, false, false, false)))
        fx(t.list.current()) shouldBe Some("m0")
        fx(t.list.offset()) shouldBe offset
        ctrl(KeyCode.C, "c")
        eventually(copied == "Hello there") shouldBe true
        // the secondary button over a message: the app's menu, whose Copy message copies it
        val answered = fx {
          Pointer.consumed = None
          t.list.event.pointer.heard(t.list.event.pointer.menu, PointerKind.Menu, t.list.absoluteLeft + 100.0,
            t.list.absoluteTop + t.list.top(t.list.indexOf("m1")) - t.list.offset() + 20.0)
          Pointer.menuAnswered
        }
        answered shouldBe true
        val labels = fx(Focus.focused().collect { case r: MenuRow => r }.toList.flatMap(r =>
          r.parent().toList.flatMap { case c: Container => c.children(); case _ => Nil }.collect { case m: MenuRow => m.item.label }))
        labels shouldBe List(ChatWords.copyMessage)
        fx(Focus.focused().collect { case r: MenuRow => r.item.action() })
        fx(Modal.close())
        eventually(copied == "**Bold** words, as written") shouldBe true
        // over the day's separator, nothing of the app's: the platform's own
        fx(t.list(0).isInstanceOf[ChatEntry.Marker]) shouldBe true
        val onDay = fx {
          Pointer.consumed = None
          t.list.event.pointer.heard(t.list.event.pointer.menu, PointerKind.Menu, t.list.absoluteLeft + 100.0,
            t.list.absoluteTop + t.list.top(0) - t.list.offset() + 4.0)
          Pointer.menuAnswered
        }
        onDay shouldBe false
        fx(Modal.showing) shouldBe false
      }
    }
  }
}
