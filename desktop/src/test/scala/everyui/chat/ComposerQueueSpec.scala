package everyui.chat

import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.i18n.{Language, Strings}
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** A composer that queues what is sent while a reply comes (`queueWhileBusy`), on the desktop backend's real nodes
  * with real JavaFX key events: messages queued, edited back into the field and queued again into their place, taken
  * off, moved by the keys and by a drag, sent one per idle through `onSend` (never two for a flicker of `busy`, nor
  * before the app has started the last one's reply), paused by Stop or drained after it, the paused line's Send next,
  * Clear queue and the app's actions (in a ⋮ menu where narrow, by the keys), said to a screen reader, and a composer
  * with it off as it always was. */
class ComposerQueueSpec extends FxSpec {
  private var sent: List[String] = Nil
  private var stops = 0
  // the composer made last: what one made before it sends, on a timer still running, is not this one's
  private var made: Option[Composer] = None

  private def make(queueing: Boolean = true): Composer = fx {
    sent = Nil
    stops = 0
    made.foreach(_.dispose())
    var c: Composer = null
    c = new Composer(d => if (made.exists(_ eq c)) sent = sent :+ d.text, onStop = () => stops += 1, touch = false)
    made = Some(c)
    c.queueWhileBusy @= queueing
    // quicker than an app's, so the specs need not wait long; what is checked is the order of things, not the times
    c.queueSettle @= 0.05
    c.queueStartWait @= 5.0
    ui.children.clear()
    Focus.focused @= None
    ui.children += c
    c.focusField()
    c
  }

  private def press(code: KeyCode, text: String = "", shift: Boolean = false, alt: Boolean = false): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, text, text, code, shift, false, alt, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, text, text, code, shift, false, alt, false))
  }

  // `words` written and sent with Enter
  private def write(c: Composer, words: String): Unit = {
    fx(c.text @= words)
    press(KeyCode.ENTER)
  }

  private def texts(c: Composer): List[String] = fx(c.queue().map(_.text))
  // (read on the FX thread: inside `fx`, or wrapped in one)
  private def chips(c: Composer): List[QueueChip] = under[DraftQueue](c).headOption.map(_.all).getOrElse(Nil)
  private def focusedChip(c: Composer): Int = fx(chips(c).indexWhere(ch => Focus.focused().exists(_ eq ch)))

  // every `T` under `c`, in tree order
  private def under[T](c: everyui.component.Component)(using t: scala.reflect.ClassTag[T]): Vector[T] = {
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

  // every part under `c`: what it is, where, and how large
  private def tree(c: everyui.component.Component): Vector[String] = under[everyui.component.Component](c).map(p =>
    p.kind + " " + p.position.left() + "," + p.position.top() + " " + p.size.width() + "x" + p.size.height())

  private def click(c: everyui.component.Component): Unit = fx(c.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))

  // the app's reply started and finished, as `busy` says
  private def reply(c: Composer): Unit = {
    fx(c.busy @= true)
    fx(c.busy @= false)
  }

  // a queue of A, B and C written while a reply comes
  private def three(): Composer = {
    val c = make()
    fx(c.busy @= true)
    List("A", "B", "C").foreach(w => write(c, w))
    c
  }

  // A, B and C queued, then paused by Stop, the reply over
  private def paused(): Composer = {
    val c = three()
    fx(c.stop())
    fx(c.busy @= false)
    c
  }

  // (read on the FX thread, as `chips` is)
  private def line(c: Composer): DraftQueue = under[DraftQueue](c).head

  // the words on one of the line's actions (a button's), or the ⋮'s name
  private def labelOf(c: everyui.component.Component): String = c match {
    case b: everyui.component.Button => b.label()
    case other => other.semantics().label
  }

  "A message sent while a reply comes" should {
    "be queued, the field empty for the next, and nothing sent" in {
      val c = three()
      sent shouldBe Nil
      texts(c) shouldBe List("A", "B", "C")
      fx(c.text()) shouldBe ""
      fx(chips(c).length) shouldBe 3
      fx(chips(c).map(_.index())) shouldBe List(0, 1, 2)
      fx(Focus.focused()) shouldBe Some(c.field)
    }
    "queue nothing for an empty draft" in {
      val c = make()
      fx(c.busy @= true)
      write(c, "")
      write(c, "   ")
      texts(c) shouldBe Nil
      fx(under[DraftQueue](c)).isEmpty shouldBe true
    }
    "be queued by the Send beside Stop, Stop still there" in {
      val c = make()
      fx(c.busy @= true)
      val buttons = fx(under[SendButton](c))
      buttons.length shouldBe 2
      fx(buttons.map(_.stopping())) shouldBe Vector(false, true)
      fx(c.text @= "By the button")
      click(buttons.head)
      texts(c) shouldBe List("By the button")
      click(buttons(1))
      stops shouldBe 1
      fx(c.busy @= false)
      fx(under[SendButton](c).length) shouldBe 1
    }
    "go with its files, their count on its chip" in {
      val c = make()
      fx(c.busy @= true)
      fx(c.attachments @= List(Attachment.pasted("a log\nline two"), Attachment.pasted("another")))
      fx(c.text @= "See these\nand more")
      press(KeyCode.ENTER)
      fx(c.queue().head.attachments.length) shouldBe 2
      fx(c.attachments()) shouldBe Nil
      QueueChip.firstLine(fx(c.queue().head)) shouldBe "See these"
      QueueChip.said(fx(c.queue().head)) shouldBe "See these\nand more, 2 files"
    }
  }

  "Draining" should {
    "send the queue in order, one each time the composer is idle" in {
      val c = three()
      fx(c.busy @= false)
      eventually(sent == List("A")) shouldBe true
      texts(c) shouldBe List("B", "C")
      // its reply not yet started: nothing more goes
      Thread.sleep(200L)
      sent shouldBe List("A")
      reply(c)
      eventually(sent == List("A", "B")) shouldBe true
      reply(c)
      eventually(sent == List("A", "B", "C")) shouldBe true
      texts(c) shouldBe Nil
      fx(under[DraftQueue](c)).isEmpty shouldBe true
    }
    "send nothing for a flicker of busy within one reply" in {
      val c = three()
      // a settle long enough that the two turns below come well within it
      fx(c.queueSettle @= 3.0)
      fx {
        c.busy @= false
        c.busy @= true
      }
      Thread.sleep(200L)
      sent shouldBe Nil
      // down for less than the settle, then up again: still one reply
      fx(c.busy @= false)
      fx(c.busy @= true)
      Thread.sleep(200L)
      sent shouldBe Nil
      fx(c.busy @= false)
      eventually(sent == List("A")) shouldBe true
      Thread.sleep(150L)
      sent shouldBe List("A")
    }
    "go on when a message starts no reply, after the wait for one" in {
      val c = three()
      fx(c.queueStartWait @= 0.3)
      fx(c.busy @= false)
      eventually(sent == List("A")) shouldBe true
      // busy never rises for A: after `queueStartWait` it is taken as done, and B goes
      eventually(sent == List("A", "B")) shouldBe true
    }
    "queue what is written after a message sent at rest, until its reply has come and gone" in {
      val c = make()
      write(c, "First")
      sent shouldBe List("First")
      write(c, "Second")
      sent shouldBe List("First")
      texts(c) shouldBe List("Second")
      reply(c)
      eventually(sent == List("First", "Second")) shouldBe true
    }
  }

  "Editing" should {
    "take a message back into the field, off the queue, and queue it again in its place" in {
      val c = three()
      val b = fx(c.queue()(1))
      click(fx(chips(c)(1).editButton))
      fx(c.text()) shouldBe "B"
      texts(c) shouldBe List("A", "C")
      fx(Focus.focused()) shouldBe Some(c.field)
      fx(c.text @= "B, better")
      press(KeyCode.ENTER)
      texts(c) shouldBe List("A", "B, better", "C")
      // the chips of A and C are the ones they had
      fx(chips(c).length) shouldBe 3
      b.text shouldBe "B"
    }
    "put what the field held in its place rather than lose it" in {
      val c = three()
      fx(c.text @= "X")
      fx(c.edit(c.queue()(1)))
      fx(c.text()) shouldBe "B"
      texts(c) shouldBe List("A", "X", "C")
      press(KeyCode.ENTER)
      texts(c) shouldBe List("A", "B", "X", "C")
    }
    "go back into its place though the queue has moved on" in {
      val c = three()
      fx(c.edit(c.queue()(1)))
      fx(c.busy @= false)
      eventually(sent == List("A")) shouldBe true
      fx(c.busy @= true)
      press(KeyCode.ENTER)
      texts(c) shouldBe List("B", "C")
    }
    "go to the end once the field was emptied and written again" in {
      val c = three()
      fx(c.edit(c.queue()(0)))
      fx(c.text @= "")
      write(c, "New")
      texts(c) shouldBe List("B", "C", "New")
    }
  }

  "Removing" should {
    "take a message off by its button, and by Delete on its chip" in {
      val c = three()
      click(fx(chips(c)(0).removeButton))
      texts(c) shouldBe List("B", "C")
      fx(chips(c)(0).focus())
      press(KeyCode.DELETE)
      texts(c) shouldBe List("C")
      focusedChip(c) shouldBe 0
      press(KeyCode.BACK_SPACE)
      texts(c) shouldBe Nil
      fx(Focus.focused()) shouldBe Some(c.field)
      sent shouldBe Nil
    }
  }

  "Reordering" should {
    "move the focused chip with Alt+Up and Alt+Down, the focus with it" in {
      val c = three()
      fx(chips(c)(0).focus())
      press(KeyCode.DOWN, alt = true)
      texts(c) shouldBe List("B", "A", "C")
      focusedChip(c) shouldBe 1
      press(KeyCode.DOWN, alt = true)
      texts(c) shouldBe List("B", "C", "A")
      press(KeyCode.DOWN, alt = true)
      texts(c) shouldBe List("B", "C", "A")
      press(KeyCode.UP, alt = true)
      press(KeyCode.UP, alt = true)
      texts(c) shouldBe List("A", "B", "C")
      focusedChip(c) shouldBe 0
    }
    "move a chip dragged by its handle to where it is let go" in {
      val c = three()
      fx(c.size.width @= 600.0)
      val first = fx(chips(c).head)
      val (x, y) = fx((first.handle.absoluteLeft + 8.0, first.handle.absoluteTop + 10.0))
      val pitch = QueueChip.height + QueueChip.gap
      def pointer(kind: PointerKind, at: Double): PointerEvent = PointerEvent(kind, x, at)
      fx(first.handle.event.pointer.down @= pointer(PointerKind.Down, y))
      fx(first.handle.event.pointer.move @= pointer(PointerKind.Move, y + pitch * 0.6))
      // the one it passes makes room, before it is let go
      fx(chips(c)(1).position.top()) shouldBe 0.0
      fx(first.handle.event.pointer.move @= pointer(PointerKind.Move, y + pitch * 2.0))
      fx(first.handle.event.pointer.up @= pointer(PointerKind.Up, y + pitch * 2.0))
      texts(c) shouldBe List("B", "C", "A")
      fx(chips(c).map(_.position.top())) shouldBe List(0.0, pitch, pitch * 2.0)
      // and a press on the handle edits nothing
      fx(c.text()) shouldBe ""
    }
  }

  "Stop" should {
    "pause the queue by default, which waits until resumed" in {
      val c = three()
      fx(c.queueAfterStop()) shouldBe QueueAfterStop.Pause
      click(fx(under[SendButton](c)(1)))
      stops shouldBe 1
      fx(c.queuePaused()) shouldBe true
      fx(c.busy @= false)
      Thread.sleep(300L)
      sent shouldBe Nil
      fx(under[DraftQueue](c).head.resumeButton.parent().nonEmpty) shouldBe true
      click(fx(under[DraftQueue](c).head.resumeButton))
      eventually(sent == List("A")) shouldBe true
      fx(c.queuePaused()) shouldBe false
    }
    "send a new message first while paused, the queue going on after it" in {
      val c = three()
      fx(c.stop())
      fx(c.busy @= false)
      write(c, "Now")
      sent shouldBe List("Now")
      fx(c.queuePaused()) shouldBe false
      texts(c) shouldBe List("A", "B", "C")
      reply(c)
      eventually(sent == List("Now", "A")) shouldBe true
    }
    "drain after Stop where it is asked to" in {
      val c = three()
      fx(c.queueAfterStop @= QueueAfterStop.Drain)
      fx(c.stop())
      fx(c.queuePaused()) shouldBe false
      fx(c.busy @= false)
      eventually(sent == List("A")) shouldBe true
    }
  }

  "A paused queue's line" should {
    "send exactly one with Send next, and stay paused" in {
      val c = paused()
      click(fx(line(c).sendNextButton))
      sent shouldBe List("A")
      texts(c) shouldBe List("B", "C")
      fx(c.queuePaused()) shouldBe true
      // nothing more until that message's reply has come and gone: the button is out of use, and a press does nothing
      fx(line(c).sendNextButton.enabled()) shouldBe false
      click(fx(line(c).sendNextButton))
      fx(c.sendNext())
      Thread.sleep(300L)
      sent shouldBe List("A")
      fx(c.announced.head) shouldBe "Paused, 2 messages queued"
    }
    "send them one by one with Send next pressed after each reply, the focus kept and then back in the field" in {
      val c = paused()
      fx(line(c).sendNextButton.focus())
      List("A", "B", "C").foreach { w =>
        fx(line(c).sendNextButton.enabled()) shouldBe true
        press(KeyCode.ENTER)
        sent.last shouldBe w
        fx(c.queuePaused()) shouldBe (w != "C")
        if (w != "C") fx(Focus.focused()) shouldBe Some(line(c).sendNextButton)
        reply(c)
        Thread.sleep(150L)
      }
      sent shouldBe List("A", "B", "C")
      texts(c) shouldBe Nil
      // the queue gone, and the line with it: the focus back where the person writes
      fx(under[DraftQueue](c)).isEmpty shouldBe true
      fx(Focus.focused()) shouldBe Some(c.field)
    }
    "take every message off with Clear queue, at once and sending none, the focus back in the field" in {
      val c = paused()
      fx(line(c).clearButton.focus())
      press(KeyCode.ENTER)
      texts(c) shouldBe Nil
      fx(c.queuePaused()) shouldBe false
      fx(under[DraftQueue](c)).isEmpty shouldBe true
      fx(Focus.focused()) shouldBe Some(c.field)
      fx(c.announced.head) shouldBe "No messages queued"
      Thread.sleep(300L)
      sent shouldBe Nil
    }
    "show the app's actions after its own, and do them" in {
      val c = paused()
      fx(c.size.width @= 900.0)
      var done: List[String] = Nil
      fx(c.queueActions @= List(QueueAction("Stop tools", () => done = done :+ "tools"),
        QueueAction("Later", () => done = done :+ "later", Some(everyui.font.MaterialIcon.Schedule))))
      val shown = fx(line(c).shown.map(labelOf))
      shown shouldBe List("Resume", "Send next", "Clear queue", "Stop tools", "Later")
      click(fx(line(c).shown(3)))
      click(fx(line(c).shown(4)))
      done shouldBe List("tools", "later")
      // changed, they are made again
      fx(c.queueActions @= List(QueueAction("Only", () => done = done :+ "only")))
      fx(line(c).shown.map(labelOf)) shouldBe List("Resume", "Send next", "Clear queue", "Only")
      texts(c) shouldBe List("A", "B", "C")
    }
    "fit on one line, Resume kept and the rest in order in a ⋮ menu where narrow" in {
      val c = paused()
      var done: List[String] = Nil
      fx(c.queueActions @= List(QueueAction("Stop running commands", () => done = done :+ "stop")))
      fx(c.size.width @= 360.0)
      val q = fx(line(c))
      val shown = fx(q.shown)
      shown.head shouldBe q.resumeButton
      shown.last shouldBe q.moreButton
      val kept = shown.drop(1).dropRight(1)
      val all = fx(q.rest())
      fx(kept) shouldBe all.take(kept.length)
      kept.length should be < all.length
      // every part within the line, on it, and none over the words
      fx {
        val bar = q.resumeButton.parent().get
        bar.position.left() + bar.size.width() should be <= (q.size.width() + 0.5)
        shown.map(s => s.position.left() + s.size.width()).max should be <= (bar.size.width() + 0.5)
        val words = under[everyui.component.TextView](q).find(_.content().startsWith("Paused")).get
        words.position.left() + words.size.width() should be <= bar.position.left()
        shown.foreach(s => s.size.height() should be <= 30.0)
      }
      // the menu has the rest, in order; the last chosen does it
      click(fx(q.moreButton))
      val menu = fx(everyui.component.Modal.top.collect { case m: everyui.menu.Menu => m }).get
      fx(menu.rows.map(_.item.label)) shouldBe fx(all.drop(kept.length).map(labelOf))
      fx(menu.rows.last.chosen @= (()))
      done shouldBe List("stop")
      fx(everyui.component.Modal.showing) shouldBe false
      // wide again, every action on the line, and no ⋮
      fx(c.size.width @= 900.0)
      fx(q.shown.map(labelOf)) shouldBe List("Resume", "Send next", "Clear queue", "Stop running commands")
      // narrow again, Clear queue from the menu: the line gone with the queue, the focus back in the field
      fx(c.size.width @= 360.0)
      fx(q.moreButton.focus())
      click(fx(q.moreButton))
      val again = fx(everyui.component.Modal.top.collect { case m: everyui.menu.Menu => m }).get
      fx(again.rows.find(_.item.label == "Clear queue").get.chosen @= (()))
      texts(c) shouldBe Nil
      fx(Focus.focused()) shouldBe Some(c.field)
    }
    "be reached by the keys: Up from the first chip to Resume, along by Left and Right, and Down to the chip" in {
      val c = paused()
      fx(c.queueActions @= List(QueueAction("Stop tools", () => ())))
      fx(chips(c).head.focus())
      press(KeyCode.UP)
      fx(Focus.focused()) shouldBe Some(line(c).resumeButton)
      val along = (1 to 3).map { _ =>
        press(KeyCode.RIGHT)
        fx(Focus.focused().map(labelOf).getOrElse(""))
      }
      along shouldBe Vector("Send next", "Clear queue", "Stop tools")
      press(KeyCode.LEFT)
      fx(Focus.focused().map(labelOf)) shouldBe Some("Clear queue")
      press(KeyCode.DOWN)
      focusedChip(c) shouldBe 0
      press(KeyCode.DOWN)
      press(KeyCode.DOWN)
      press(KeyCode.DOWN)
      fx(Focus.focused()) shouldBe Some(c.field)
      // and in the tree's order (Tab), the line before the chips
      val stops = fx(Focus.candidates(line(c)).toList)
      stops.take(4).map(s => fx(labelOf(s))) shouldBe List("Resume", "Send next", "Clear queue", "Stop tools")
      stops.drop(4) shouldBe fx(chips(c))
    }
    "be named to a screen reader, the ⋮ too, in the language in use" in {
      val c = paused()
      fx(c.size.width @= 300.0)
      fx(line(c).moreButton.semantics().label) shouldBe "More queue actions"
      fx(line(c).moreButton.parent().nonEmpty) shouldBe true
      val was = fx(Strings.language())
      try {
        fx(Strings.language @= Language("de", "Deutsch", "German"))
        ChatWords.sendNext shouldBe "Nächste senden"
        ChatWords.clearQueue shouldBe "Warteschlange leeren"
        fx(Strings.language @= Language("ar", "العربية", "Arabic"))
        ChatWords.clearQueue shouldBe "مسح قائمة الانتظار"
      }
      finally fx(Strings.language @= was)
    }
    "show nothing while the queue is not paused" in {
      val c = three()
      fx(c.queueActions @= List(QueueAction("Stop tools", () => ())))
      val q = fx(line(c))
      fx(List(q.resumeButton, q.sendNextButton, q.clearButton, q.moreButton).map(b => Focus.showing(b))) shouldBe
        List(false, false, false, false)
      fx(Focus.candidates(q).toList) shouldBe fx(chips(c))
    }
  }

  "A screen reader" should {
    "hear how many are queued as it changes, and that it is paused" in {
      val c = three()
      fx(c.announced.reverse) shouldBe List("1 message queued", "2 messages queued", "3 messages queued")
      fx(c.unqueue(c.queue().head))
      fx(c.announced.head) shouldBe "2 messages queued"
      fx(c.stop())
      fx(c.announced.head) shouldBe "Paused, 2 messages queued"
      fx(chips(c).head.semantics().label) should startWith("Queued message 1 of 2: B")
    }
    "hear it in the language in use" in {
      val was = fx(Strings.language())
      try {
        fx(Strings.language @= Language("de", "Deutsch", "German"))
        ChatWords.queued(2) shouldBe "2 Nachrichten in der Warteschlange"
        ChatWords.queued(1) shouldBe "1 Nachricht in der Warteschlange"
        fx(Strings.language @= Language("zh_Hant", "繁體中文", "Chinese (Traditional)"))
        ChatWords.queued(3) shouldBe "3 則訊息正在排隊"
      }
      finally fx(Strings.language @= was)
    }
  }

  "The keys" should {
    "go from the start of the field to the last chip, through the chips, and back" in {
      val c = three()
      press(KeyCode.UP)
      focusedChip(c) shouldBe 2
      press(KeyCode.UP)
      focusedChip(c) shouldBe 1
      press(KeyCode.DOWN)
      press(KeyCode.DOWN)
      fx(Focus.focused()) shouldBe Some(c.field)
      press(KeyCode.UP)
      press(KeyCode.ESCAPE)
      fx(Focus.focused()) shouldBe Some(c.field)
    }
    "leave Up to the field while there is text before the caret" in {
      val c = three()
      fx(c.text @= "Some words")
      fx(c.field.field.get.moveCaret(4))
      eventually(c.field.field.get.caret == 4) shouldBe true
      press(KeyCode.UP)
      fx(Focus.focused()) shouldBe Some(c.field)
    }
    "edit the focused chip with Enter or E" in {
      val c = three()
      fx(chips(c)(2).focus())
      press(KeyCode.ENTER)
      fx(c.text()) shouldBe "C"
      texts(c) shouldBe List("A", "B")
      fx(chips(c)(0).focus())
      press(KeyCode.E, "e")
      fx(c.text()) shouldBe "A"
      texts(c) shouldBe List("C", "B")
    }
  }

  "With it off" should {
    "hold Enter while a reply comes, and show and send no queue the app sets" in {
      val c = make(queueing = false)
      fx(c.busy @= true)
      write(c, "Later")
      sent shouldBe Nil
      fx(c.text()) shouldBe "Later"
      texts(c) shouldBe Nil
      fx(under[SendButton](c).length) shouldBe 1
      fx(c.queue @= List(Draft("Seeded", Nil)))
      fx(under[DraftQueue](c)).isEmpty shouldBe true
      fx(c.busy @= false)
      Thread.sleep(200L)
      sent shouldBe Nil
      fx(c.announced) shouldBe Nil
    }
    "lay out exactly as before" in {
      val c = make(queueing = false)
      fx(c.size.width @= 600.0)
      fx((c.size.height(), c.field.position.top(), c.children().length)) shouldBe ((57.0, 8.0, 1))
      val off = fx(tree(c))
      fx(c.queueActions @= List(QueueAction("Stop tools", () => ())))
      fx(tree(c)) shouldBe off
      fx(c.queueWhileBusy @= true)
      fx(tree(c)) shouldBe off
    }
  }
}
