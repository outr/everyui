package everyui.chat

import everyui.component.{Component, IconButton}
import everyui.event.{Pointer, PointerKind}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.render.Host
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** The buttons beside a message in a transcript, on the desktop's real nodes: shown as the pointer rests on a message and
  * gone once it leaves, beside the bubble on the side away from its edge (mirrored right to left) or on its corner where
  * there is no room, with nothing in the rows moving; Copy message putting the words on the clipboard as written, with
  * a tick for a moment; an app's own per message; the keys in and out; nothing where there is no clipboard; and one
  * row of buttons, following the rows as they are handed one message after another down 320. What the machine's
  * clipboard held before is put back after. */
class MessageActionsSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val me = Participant("u-me", "Me")

  private def press(code: KeyCode): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }

  // (on the FX thread)
  private def copied: String = Option(javafx.scene.input.Clipboard.getSystemClipboard.getString).getOrElse("")

  private def talk(n: Int): Vector[ChatEntry] = (0 until n).toVector.map { i =>
    ChatEntry.message("m" + i, if (i % 3 == 0) me else ada, self = i % 3 == 0,
      "Message " + i + (if (i % 4 == 0) ": long enough to wrap onto a second line in a transcript this narrow, and so a " +
        "row of a height of its own." else ""))
  }

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
      ui.children.clear()
      c.dispose()
      Focus.focused @= None
      javafx.scene.input.Clipboard.getSystemClipboard.setContent(kept)
    }
  }

  private def transcript(entries: Var[Vector[ChatEntry]], width: Double, height: Double = 500.0): Transcript = {
    val t = fx(new Transcript(entries))
    fx {
      t.size.width @= width
      t.size.height @= height
    }
    t
  }

  private def settle(t: Transcript): Unit = (1 to 5).foreach(_ => fx(t.list.refresh()))

  // the pointer moved over the row showing `key`, 10 down it
  private def hover(t: Transcript, key: String): Unit = fx {
    Pointer.consumed = None
    val i = t.list.indexOf(key)
    t.list.event.pointer.heard(t.list.event.pointer.move, PointerKind.Move, t.list.absoluteLeft + 60.0,
      t.list.absoluteTop + t.list.top(i) - t.list.offset() + 10.0)
  }

  private def leave(t: Transcript): Unit = fx {
    Pointer.consumed = None
    t.list.event.pointer.heard(t.list.event.pointer.leave, PointerKind.Leave, t.list.absoluteLeft - 10.0, 0.0)
  }

  private def bar(t: Transcript): MessageActions = t.actions.get
  private def shows(t: Transcript, key: String): Boolean =
    t.actions.exists(b => b.showing() && b.message().exists(_.id == key))

  // the bubble of the row showing `key`, in the transcript: left, top, width, height
  private def bubble(t: Transcript, key: String): (Double, Double, Double, Double) = fx {
    val row = t.list.rowAt(t.list.indexOf(key)).get.asInstanceOf[ChatRow]
    val b = row.message.get.bubble
    (b.position.left(), row.position.top() + b.position.top(), b.size.width(), b.size.height())
  }

  private def box(t: Transcript): (Double, Double, Double, Double) = fx {
    val b = t.actions.get
    (b.position.left(), b.position.top(), b.size.width(), b.size.height())
  }

  // every row placed: its index, where it is, its height and what it holds
  private def rows(t: Transcript): List[(Int, Double, Double, Int)] = fx {
    t.list.rows.filter(_.visible()).map(r => (r.index(), r.position.top(), r.size.height(), r.children().size))
      .sortBy(_._1).toList
  }

  "A message's actions" should {
    "show beside the bubble as the pointer rests on a message, away from its edge, and go when it leaves" in {
      val entries = Var(talk(6))
      val t = transcript(entries, 800.0)
      onScreen(t) {
        settle(t)
        val before = rows(t)
        fx(t.actions) shouldBe None
        // somebody else's (m1): after the bubble's end
        hover(t, "m1")
        eventually(shows(t, "m1")) shouldBe true
        val (bl, bt, bw, bh) = bubble(t, "m1")
        val (al, at, aw, ah) = box(t)
        al shouldBe (bl + bw + MessageActions.gap +- 0.5)
        at should be >= bt
        at + ah should be <= bt + math.max(bh, ah) + 0.5
        fx(bar(t).actionButtons.map(_.tooltip())) shouldBe Vector(ChatWords.copyMessage)
        // nothing in the rows moved or changed size, nor did anything go into them
        rows(t) shouldBe before
        // one's own (m3): before the bubble's start
        hover(t, "m3")
        eventually(shows(t, "m3")) shouldBe true
        val (ol, _, _, _) = bubble(t, "m3")
        val (al2, _, aw2, _) = box(t)
        al2 + aw2 shouldBe (ol - MessageActions.gap +- 0.5)
        rows(t) shouldBe before
        // from the row onto the buttons is still the message
        leave(t)
        fx(Pointer.consumed = None)
        fx(bar(t).event.pointer.heard(bar(t).event.pointer.enter, PointerKind.Enter, al2 + 4.0, 0.0))
        Thread.sleep(300L)
        fx(shows(t, "m3")) shouldBe true
        // and off both, gone
        fx(bar(t).event.pointer.heard(bar(t).event.pointer.leave, PointerKind.Leave, 0.0, 0.0))
        eventually(!bar(t).showing()) shouldBe true
        // nor can the focus land on them while they are away
        fx(bar(t).actionButtons.forall(b => !b.focusable())) shouldBe true
        rows(t) shouldBe before
      }
    }

    "mirror right to left, and sit on the bubble's corner where there is no room beside it" in {
      val entries = Var(talk(6))
      fx(Direction.set(true))
      try {
        val t = transcript(entries, 800.0)
        onScreen(t) {
          settle(t)
          hover(t, "m1")
          eventually(shows(t, "m1")) shouldBe true
          val (bl, _, _, _) = bubble(t, "m1")
          val (al, _, aw, _) = box(t)
          // somebody else's, at the right: the buttons to its left
          al + aw shouldBe (bl - MessageActions.gap +- 0.5)
          hover(t, "m3")
          eventually(shows(t, "m3")) shouldBe true
          val (ol, _, ow, _) = bubble(t, "m3")
          box(t)._1 shouldBe (ol + ow + MessageActions.gap +- 0.5)
        }
      } finally fx(Direction.set(false))
      // a phone's width: a long message's bubble leaves no room beside it, so the buttons sit on its corner, over its
      // padding, not its words
      val narrow = transcript(Var(talk(6)), 360.0)
      onScreen(narrow) {
        settle(narrow)
        val before = rows(narrow)
        hover(narrow, "m4")
        eventually(shows(narrow, "m4")) shouldBe true
        val (bl, bt, bw, _) = bubble(narrow, "m4")
        val (al, at, aw, ah) = box(narrow)
        al + aw shouldBe (bl + bw +- 0.5)
        at + ah shouldBe (bt + Transcript.padY +- 0.5)
        rows(narrow) shouldBe before
      }
    }

    "copy the message as written, with a tick for a moment" in {
      val entries = Var[Vector[ChatEntry]](Vector(
        ChatEntry.message("m0", ada, self = false, "**Bold** and `code`, as written\non two lines")))
      val t = transcript(entries, 800.0)
      onScreen(t) {
        settle(t)
        hover(t, "m0")
        eventually(shows(t, "m0")) shouldBe true
        val copy = bar(t).actionButtons.head
        fx {
          Pointer.consumed = None
          copy.event.click @= everyui.event.PointerEvent(PointerKind.Click, copy.absoluteLeft + 4.0, copy.absoluteTop + 4.0)
        }
        eventually(copied == "**Bold** and `code`, as written\non two lines") shouldBe true
        fx(copy.icon.icon()) shouldBe MaterialIcon.Check
        fx(copy.tooltip()) shouldBe ChatWords.copied
        fx(copy.semantics().label) shouldBe ChatWords.copied
        // and back, a moment later
        Thread.sleep((MessageActions.acknowledged * 1000.0).toLong - 300L)
        eventually(copy.icon.icon() == MaterialIcon.ContentCopy) shouldBe true
        fx(copy.tooltip()) shouldBe ChatWords.copyMessage
      }
    }

    "offer what the app says for each message" in {
      val entries = Var(talk(6))
      val t = transcript(entries, 800.0)
      var quoted = ""
      fx(t.messageActions @= {
        case m: ChatEntry.Message if !m.self => t.actionsFor(m) :+
          MessageAction(MaterialIcon.FormatQuote, "Quote", () => quoted = m.text())
        case m: ChatEntry.Message => List(MessageAction(MaterialIcon.Edit, "Edit", () => ()))
        case _ => Nil
      })
      onScreen(t) {
        settle(t)
        hover(t, "m1")
        eventually(shows(t, "m1")) shouldBe true
        fx(bar(t).actionButtons.map(_.tooltip())) shouldBe Vector(ChatWords.copyMessage, "Quote")
        fx(bar(t).run(1))
        quoted shouldBe "Message 1"
        hover(t, "m3")
        eventually(shows(t, "m3")) shouldBe true
        fx(bar(t).actionButtons.map(_.tooltip())) shouldBe Vector("Edit")
        // none for a message: nothing shows over it
        fx(t.messageActions @= ((_: ChatEntry) => Nil))
        eventually(!bar(t).showing()) shouldBe true
      }
    }

    "be reached from the keys: shown on the message under the cursor, entered by the forward arrow or Tab, left by Escape" in {
      val entries = Var(talk(6))
      val t = transcript(entries, 800.0)
      onScreen(t) {
        settle(t)
        // no pointer used just now: the keys moved the cursor
        fx(Pointer.usedAt = -1.0e9)
        fx(t.focus())
        fx(t.list.current @= Some("m2"))
        eventually(shows(t, "m2")) shouldBe true
        press(KeyCode.RIGHT)
        eventually(Focus.focused().exists(_ eq bar(t).actionButtons.head)) shouldBe true
        press(KeyCode.ENTER)
        eventually(copied == "Message 2") shouldBe true
        fx(bar(t).actionButtons.head.icon.icon()) shouldBe MaterialIcon.Check
        // still beside the message the focus came from
        fx(shows(t, "m2")) shouldBe true
        press(KeyCode.ESCAPE)
        eventually(Focus.focused().exists(_ eq t.list)) shouldBe true
        fx(t.list.current()) shouldBe Some("m2")
        // Tab goes on from the transcript to them, and an arrow out of them back
        fx(Focus.next())
        fx(Focus.focused().exists(_ eq bar(t).actionButtons.head)) shouldBe true
        press(KeyCode.UP)
        eventually(Focus.focused().exists(_ eq t.list)) shouldBe true
        // the cursor moved: they move with it
        press(KeyCode.DOWN)
        eventually(shows(t, "m3")) shouldBe true
        // the focus gone elsewhere: away
        fx(Focus.focused @= None)
        eventually(!bar(t).showing()) shouldBe true
        // a click puts the cursor on a message and leaves the buttons to the pointer: gone with it
        fx(t.focus())
        hover(t, "m1")
        fx(t.list.clickAt(t.list.absoluteLeft + 100.0, t.list.absoluteTop + t.list.top(t.list.indexOf("m1")) -
          t.list.offset() + 10.0, everyui.event.Modifiers(false, false, false, false)))
        fx(t.list.current()) shouldBe Some("m1")
        eventually(shows(t, "m1")) shouldBe true
        leave(t)
        eventually(!bar(t).showing()) shouldBe true
        // and the forward arrow brings them back, the focus onto them
        fx(Pointer.usedAt = -1.0e9)
        press(KeyCode.RIGHT)
        eventually(Focus.focused().exists(_ eq bar(t).actionButtons.head)) shouldBe true
        fx(shows(t, "m1")) shouldBe true
      }
    }

    "show nothing, and make nothing, where there is no clipboard" in {
      val was = fx(Host.args)
      fx(Host.args = was + ("clipboard" -> "false"))
      try {
        val entries = Var(talk(6))
        val t = transcript(entries, 800.0)
        onScreen(t) {
          settle(t)
          hover(t, "m1")
          fx(t.focus())
          fx(t.list.current @= Some("m2"))
          Thread.sleep(200L)
          fx(t.actions) shouldBe None
          fx(t.children().exists(_.isInstanceOf[MessageActions])) shouldBe false
        }
      } finally fx(Host.args = was)
    }

    "follow the rows as they are handed one message after another, as one row of buttons, over 320" in {
      val entries = Var(talk(320))
      val t = transcript(entries, 600.0, 400.0)
      onScreen(t) {
        settle(t)
        fx(t.list.scrollTop(0.0))
        settle(t)
        var step = 0
        while (fx(t.list.offset() < t.list.total - t.list.size.height() - 1.0) && step < 400) {
          val key = fx {
            val i = t.list.indexAt(t.list.offset() + t.list.size.height() / 2.0)
            t.list.keyAt(i)
          }
          hover(t, key)
          eventually(shows(t, key)) shouldBe true
          // beside the bubble of the row that shows it now
          val (bl, _, bw, _) = bubble(t, key)
          val (al, _, aw, _) = box(t)
          if (key.drop(1).toInt % 3 == 0) al + aw shouldBe (bl - MessageActions.gap +- 0.5)
          else al shouldBe (bl + bw + MessageActions.gap +- 0.5)
          fx(t.list.scrollBy(137.0))
          settle(t)
          step += 1
        }
        step should be > 50
        // one row of buttons, and none in the rows; and the rows made, few
        fx(t.children().count(_.isInstanceOf[MessageActions])) shouldBe 1
        fx(t.list.rows.exists(r => Focus.candidates(r).exists(_.isInstanceOf[IconButton]))) shouldBe false
        fx(t.list.rows.size) should be < 40
        // a scroll under a still pointer: the message now under it
        fx(t.list.scrollTop(0.0))
        settle(t)
        val (key, next) = fx {
          val i = t.list.indexAt(t.list.offset() + 20.0)
          (t.list.keyAt(i), t.list.keyAt(i + 3))
        }
        fx {
          Pointer.consumed = None
          t.list.event.pointer.heard(t.list.event.pointer.move, PointerKind.Move, t.list.absoluteLeft + 60.0,
            t.list.absoluteTop + 20.0)
        }
        eventually(shows(t, key)) shouldBe true
        fx(t.list.scrollTop(t.list.top(t.list.indexOf(next)) - 10.0))
        settle(t)
        eventually(shows(t, next)) shouldBe true
      }
    }
  }
}
