package everyui.chat

import everyui.async.Task
import everyui.component.Component
import everyui.component.types.Semantics
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.motion.ReduceMotion
import everyui.task.Frames
import everyui.tooltip.Tooltip
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** `Transcript.turnHeader`: the header over the top edge names the turn the topmost entry in view belongs to while the
  * entry that starts it is out of sight above, at the bottom the newest's, and is hidden while that entry shows and
  * above the first turn; its spinner follows the turn's `busy`; a press, or Enter on it, takes the transcript to the
  * entry, at once where less motion is asked for and gliding where not; it is a link to a screen reader, its words cut
  * to one line and whole in its tip; and nothing of the transcript moves as it comes, as earlier turns are paged in
  * above, or as the entry starting a turn takes another id. */
class TurnHeaderSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val me = Participant("u-me", "Me")

  private val long = "The socket closes when nothing crosses it for sixty seconds, which the staging proxy enforces " +
    "and a local run never meets. A heartbeat every 25 seconds keeps it open. "

  // turn `i`: my question, then Ada's long answer, taller than the view
  private def turn(i: Int): Vector[ChatEntry] = Vector(
    ChatEntry.message("q" + i, me, self = true, "Question " + i + ": why does the socket drop after a minute idle on " +
      "the staging server, when it never does locally?"),
    ChatEntry.message("a" + i, ada, self = false, "Answer " + i + ". " + long * 8))

  // an opening note before any turn, then turns `from` until `to`
  private def talk(from: Int, to: Int, opening: Boolean = true): Vector[ChatEntry] =
    (if (opening) Vector(ChatEntry.marker("start", "Conversation started")) else Vector.empty) ++
      (from until to).toVector.flatMap(turn)

  private def press(code: KeyCode): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += c
    }
    try test finally fx {
      ui.children.clear()
      c.dispose()
      Focus.focused @= None
      Tooltip.hide()
    }
  }

  private def settle(t: Transcript): Unit = (1 to 5).foreach(_ => fx(t.list.refresh()))

  // the newest turn's busy, and whether the header shows
  private val busy = Var(true)

  private def transcript(entries: Var[Vector[ChatEntry]],
                         older: Option[() => Task[Vector[ChatEntry]]] = None): Transcript = {
    val t = fx(new Transcript(entries, older))
    fx {
      t.dayMarkers @= false
      t.size.width @= 400.0
      t.size.height @= 500.0
    }
    t
  }

  private def headed(t: Transcript): Unit = fx {
    t.turnHeader @= Some((e: ChatEntry) => e match {
      case m: ChatEntry.Message if m.self => Some(TurnHeader(m.text, busy = if (m.id == "q7") busy else Val(false)))
      case _ => None
    })
  }

  // entry `id` the topmost in view, `dy` down into it
  private def into(t: Transcript, id: String, dy: Double): Unit = {
    (1 to 2).foreach { _ =>
      fx(t.list.scrollTop(t.list.top(t.list.indexOf(id)) + dy))
      settle(t)
    }
  }

  private def named(t: Transcript): Option[String] = fx(t.turn().map(_.id))
  private def below(t: Transcript, id: String): Double = fx(t.list.top(t.list.indexOf(id)) - t.list.offset())
  private def showing(t: Transcript): Boolean = fx(t.header.visible())

  private def click(c: Component): Unit = fx(c.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))

  "A transcript's turn header" should {
    "name the turn of the topmost entry in view as it scrolls, the newest's at the bottom" in {
      val entries = Var(talk(0, 8))
      val t = transcript(entries)
      headed(t)
      onScreen(t) {
        settle(t)
        eventually(t.list.offset() >= t.list.total - t.list.size.height() - 1.0) shouldBe true
        // at the bottom, the newest turn's question is out of sight above its long answer
        named(t) shouldBe Some("q7")
        showing(t) shouldBe true
        fx(t.header.header().map(_.line)) shouldBe Some(fx(entries()(15).asInstanceOf[ChatEntry.Message].text()))
        // in the middle of turn 3's answer, turn 3
        into(t, "a3", 120.0)
        named(t) shouldBe Some("q3")
        showing(t) shouldBe true
        // turn 5's question at the top, partly in view: it shows, so the header does not
        into(t, "q5", 10.0)
        named(t) shouldBe None
        showing(t) shouldBe false
        // its last pixel gone over the top, and the header names it
        fx(t.list.scrollTop(t.list.top(t.list.indexOf("a5")) + 0.5))
        named(t) shouldBe Some("q5")
        // back up to the opening note, above every turn: none
        fx(t.list.scrollTop(0.0))
        settle(t)
        named(t) shouldBe None
        showing(t) shouldBe false
        // and down again, step by step: always the turn above the topmost
        (1 to 6).foreach { _ =>
          fx(t.list.scrollBy(700.0))
          settle(t)
          val expected = fx {
            val top = t.list.indexAt(t.list.offset())
            val start = (0 to top).reverse.find(i => t.list(i).id.startsWith("q"))
            start.filter(_ < top).map(i => t.list(i).id)
          }
          named(t) shouldBe expected
        }
      }
    }

    "show its spinner while the turn is busy, and say so" in {
      fx(busy @= true)
      val entries = Var(talk(0, 8))
      val t = transcript(entries)
      headed(t)
      onScreen(t) {
        settle(t)
        eventually(t.turn().map(_.id).contains("q7")) shouldBe true
        fx(t.header.spinner.visible()) shouldBe true
        fx(t.header.spinner.busy()) shouldBe true
        fx(t.header.semantics().label.endsWith(", " + ChatWords.running)) shouldBe true
        fx(busy @= false)
        fx(t.header.spinner.visible()) shouldBe false
        fx(t.header.spinner.busy()) shouldBe false
        fx(t.header.semantics().label.endsWith(ChatWords.running)) shouldBe false
        fx(busy @= true)
        fx(t.header.spinner.visible()) shouldBe true
        // another turn's, never busy, has none
        into(t, "a2", 40.0)
        named(t) shouldBe Some("q2")
        fx(t.header.spinner.visible()) shouldBe false
      }
    }

    "be a link to a screen reader, one line cut short, its words whole in its tip, a file's name after them" in {
      val entries = Var(talk(0, 8))
      val t = transcript(entries)
      fx {
        // (as CONSUMING.md writes it)
        t.turnHeader @= Some {
          case m: ChatEntry.Message if m.self =>
            Some(TurnHeader.of(m, named = true, attachment = if (m.id == "q3") Some("socket.log") else None))
          case _ => None
        }
      }
      onScreen(t) {
        settle(t)
        into(t, "a3", 120.0)
        named(t) shouldBe Some("q3")
        val words = "Me: Question 3: why does the socket drop after a minute idle on the staging server, when it " +
          "never does locally?"
        fx(t.header.semantics()) shouldBe Semantics("link", label = ChatWords.jumpToMessage(words + ", socket.log"))
        fx(t.header.semantics().label.startsWith("Jump to message: Me: Question 3")) shouldBe true
        fx(t.header.tooltip()) shouldBe words + ", socket.log"
        fx(t.header.size.height()) shouldBe TurnHeader.height
        fx(t.header.size.width()) shouldBe 400.0 - TurnHeader.sideInset * 2.0
        // one line, cut short, the file's name after it within the header
        fx(t.header.words.elided()) shouldBe true
        fx(t.header.words.size.height()) should be < 24.0
        fx(t.header.file.visible()) shouldBe true
        fx(t.header.file.position.left() + t.header.file.size.width()) should be <= fx(t.header.size.width())
        fx(t.header.words.position.left() + t.header.words.size.width()) should be <= fx(t.header.file.position.left())
        // a finger held on it shows the words whole
        fx(t.header.event.pointer.menu @= PointerEvent(PointerKind.Menu, 0.0, 0.0, finger = true))
        fx(Tooltip.shownFor.exists(_ eq t.header)) shouldBe true
        fx(Tooltip.hide())
        // another turn: no file
        into(t, "a4", 120.0)
        fx(t.header.file.visible()) shouldBe false
      }
    }

    "take the transcript to the turn's start on a press or Enter, at once where less motion is asked for" in {
      val entries = Var(talk(0, 8))
      val t = transcript(entries)
      headed(t)
      onScreen(t) {
        settle(t)
        eventually(t.turn().map(_.id).contains("q7")) shouldBe true
        fx(ReduceMotion.on @= true)
        try {
          click(t.header)
          settle(t)
          below(t, "q7") shouldBe 0.0 +- 0.5
          fx(t.list.current()) shouldBe Some("q7")
          named(t) shouldBe None
          showing(t) shouldBe false
          // Enter with the focus on it, which then goes to the rows as it hides
          into(t, "a4", 200.0)
          named(t) shouldBe Some("q4")
          fx(t.header.focus())
          fx(t.header.focused()) shouldBe true
          press(KeyCode.ENTER)
          settle(t)
          below(t, "q4") shouldBe 0.0 +- 0.5
          fx(t.list.current()) shouldBe Some("q4")
          showing(t) shouldBe false
          fx(t.list.focused()) shouldBe true
        } finally fx(ReduceMotion.on @= false)
        // where motion may be but no frame loop runs (an app without a ScreenManager), at once all the same
        into(t, "a3", 300.0)
        named(t) shouldBe Some("q3")
        // (a loop whose frames come at once and want no more: idle)
        fx(Frames.drive(_ => (), () => false, () => (), f => f()))
        fx(Frames.running) shouldBe false
        click(t.header)
        below(t, "q3") shouldBe 0.0 +- 0.5
        showing(t) shouldBe false
        // and with one, it glides there over a few frames, the frame loop driven here a frame at a time
        var asked = Vector.empty[() => Unit]
        def frames(n: Int): Unit = (1 to n).foreach { _ =>
          fx {
            val now = asked
            asked = Vector.empty
            now.foreach(f => f())
          }
        }
        fx(Frames.drive(_ => ui.update(1.0 / 60.0), () => ui.needsFrames, () => (), f => asked = asked :+ f))
        try {
          into(t, "a2", 300.0)
          named(t) shouldBe Some("q2")
          val from = fx(t.list.offset())
          click(t.header)
          // (not there at once)
          fx(t.list.offset()) shouldBe from
          frames(4)
          fx(t.list.offset()) should (be < from and be > fx(t.list.top(t.list.indexOf("q2"))))
          frames(30)
          settle(t)
          below(t, "q2") shouldBe 0.0 +- 0.5
          fx(t.list.current()) shouldBe Some("q2")
          showing(t) shouldBe false
        }
        finally fx(Frames.drive(_ => (), () => false, () => (), _ => ()))
      }
    }

    "move nothing of the transcript as it comes and goes" in {
      val entries = Var(talk(0, 8))
      val t = transcript(entries)
      onScreen(t) {
        settle(t)
        into(t, "a3", 120.0)
        def layout(): (Double, Double, Double, Double, Vector[Double]) = fx {
          (t.list.offset(), t.list.position.top(), t.list.size.height(), t.list.size.width(),
            t.list.rows.filter(_.visible()).map(_.position.top()).toVector.sorted)
        }
        val before = layout()
        showing(t) shouldBe false
        headed(t)
        settle(t)
        showing(t) shouldBe true
        layout() shouldBe before
        fx(t.turnHeader @= None)
        settle(t)
        showing(t) shouldBe false
        layout() shouldBe before
      }
    }

    "keep naming the right turn as earlier turns are paged in above, and as a turn's start takes another id" in {
      // turns 5 to 7 at first, the newest's question under the id it was sent with; 0 to 4 come when asked
      val sent = ChatEntry.message("local-7", me, self = true, "Question 7, as sent")
      val entries = Var(talk(5, 7, opening = false) ++ Vector(sent, turn(7)(1)))
      var answers: List[Vector[ChatEntry] => Unit] = Nil
      val older = () => Task.async[Vector[ChatEntry]](done => answers = answers :+ done)
      val t = transcript(entries, Some(older))
      fx {
        t.turnHeader @= Some((e: ChatEntry) => e match {
          case m: ChatEntry.Message if m.self => Some(TurnHeader(m.text))
          case _ => None
        })
      }
      onScreen(t) {
        settle(t)
        eventually(t.turn().nonEmpty) shouldBe true
        named(t) shouldBe Some("local-7")
        // in turn 5's answer, near the top: earlier turns are asked for
        into(t, "a5", 60.0)
        named(t) shouldBe Some("q5")
        eventually(answers.nonEmpty) shouldBe true
        val was = below(t, "a5")
        fx {
          val done = answers.head
          answers = answers.tail
          done(talk(0, 5))
        }
        settle(t)
        // what shows stays where it was, and so does the header
        below(t, "a5") shouldBe was +- 0.5
        named(t) shouldBe Some("q5")
        fx(t.header.header().map(_.line)) shouldBe Some(fx(entries().find(_.id == "q5").get
          .asInstanceOf[ChatEntry.Message].text()))
        // scrolled up into the earlier turns, theirs
        into(t, "a2", 40.0)
        named(t) shouldBe Some("q2")
        // nothing earlier: the header stays right
        fx(answers.foreach(_(Vector.empty)))
        settle(t)
        fx(t.exhausted() || answers.isEmpty) shouldBe true
        named(t) shouldBe Some("q2")
        // to the bottom, where the newest is named under its first id, which the server then replaces
        fx(t.toLatest())
        settle(t)
        named(t) shouldBe Some("local-7")
        val kept = ChatEntry.message("q7", me, self = true, "Question 7, as the server keeps it")
        fx(entries @= entries().map(e => if (e eq sent) kept else e))
        settle(t)
        named(t) shouldBe Some("q7")
        fx(t.turn().exists(_ eq kept)) shouldBe true
        fx(t.header.header().map(_.line)) shouldBe Some("Question 7, as the server keeps it")
        // its words changed in place, the header follows
        fx(kept.text @= "Question 7, edited")
        fx(t.header.words.content()) shouldBe "Question 7, edited"
      }
    }
  }
}
