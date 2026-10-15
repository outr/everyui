package everyui.chat

import everyui.component.{Component, Container}
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.render.Platform
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** Who is working shown in the transcript (`ChatView.workingInline`) on the desktop's real nodes: a row each after the
  * newest entry, laid out as everybody else's messages are (wide, on a phone, right to left), with their avatar, name,
  * a live elapsed time and the cancel control; what they are doing wrapped and cut to three lines with Show more and
  * Show less; the status bar saying only the connection; kept at the bottom as rows come, grow and go, and still when
  * scrolled up, nothing counted as new; reached with the keys; and with the option off, everything as it was. Run on a
  * clock the spec keeps (`Ticker.clock`, `Ticker.after`), so every tick is one the spec runs. */
class WorkingInlineSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val me = Participant("u-me", "Me")
  private val helix = Participant("u-helix", "Helix")
  private val sam = Participant("u-sam", "Sam")
  private val start = 1790000000000L
  private val long = "consult · modelId: helper-large, systemPrompt: You are an expert in release engineering who " +
    "reads the whole history of a project before answering, weighs every open pull request, and writes the notes " +
    "for a release in the voice of the team, plainly, with the risks first and the thanks last, as the team asked."

  @volatile private var time = start
  @volatile private var waiting: Vector[() => Unit] = Vector.empty

  private def timed[T](test: => T): T = {
    time = start
    waiting = Vector.empty
    fx {
      Ticker.clock = () => time
      Ticker.after = (_, f) => waiting = waiting :+ f
    }
    try test finally fx {
      Ticker.clock = () => everyui.util.Clock.real()
      Ticker.after = (s, f) => Platform.after(s, f)
    }
  }

  // `ms` passed, and the ticks waiting run
  private def pass(ms: Long): Unit = fx {
    time += ms
    val now = waiting
    waiting = Vector.empty
    now.foreach(_())
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
    }
  }

  private def press(code: KeyCode): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }

  private def click(c: Component): Unit = fx(c.event.click @= PointerEvent(PointerKind.Click, c.absoluteLeft + 4.0,
    c.absoluteTop + 4.0))

  private def under[T](c: Component, pick: PartialFunction[Component, T]): List[T] = {
    val here = pick.lift(c).toList
    here ++ (c match {
      case k: Container => k.children().toList.flatMap(under(_, pick))
      case _ => Nil
    })
  }

  private def talk(n: Int, from: Int = 0): Vector[ChatEntry] = (from until from + n).toVector.map { i =>
    ChatEntry.message("m" + i, if (i % 3 == 0) me else ada, self = i % 3 == 0,
      "Message " + i + (if (i % 4 == 0) ": long enough to wrap onto a second line in a transcript this narrow." else ""))
  }

  private def chat(entries: Var[Vector[ChatEntry]], working: Var[Vector[Working]], width: Double = 800.0,
                   height: Double = 600.0, inline: Boolean = true,
                   connection: Var[ConnectionState] = Var(ConnectionState.Connected)): ChatView = {
    val c = fx(new ChatView(entries, working = working, connection = connection))
    fx {
      c.transcript.dayMarkers @= false
      c.workingInline @= inline
      c.size.width @= width
      c.size.height @= height
    }
    c
  }

  private def settle(t: Transcript): Unit = (1 to 5).foreach(_ => fx(t.list.refresh()))

  private def atBottom(t: Transcript): Boolean = t.list.offset() >= t.list.total - t.list.size.height() - 1.0

  private def keys(t: Transcript): Vector[String] = fx((0 until t.list.count).toVector.map(t.list.keyAt))

  // (on the FX thread, or from it)
  private def onFx[T](f: => T): T = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)

  private def view(t: Transcript, id: String): WorkingView = onFx {
    val r = t.list.rowAt(t.list.indexOf(Grouping.workKey(id))).get.asInstanceOf[ChatRow]
    r.shown.get.asInstanceOf[WorkingView]
  }

  // the box around what they are doing: the words' parent
  private def box(v: WorkingView): Container = onFx(v.words.parent().get.asInstanceOf[Container])

  "Who is working, in the transcript" should {
    "be a row each after the newest entry, with the avatar, the name, a live time and the cancel" in timed {
      var cancelled = 0
      val working = Var(Vector(
        Working("w-helix", helix, "thinking", start - 5000L, Some(() => cancelled += 1)),
        Working("w-sam", sam, "reviewing", start - 75000L)))
      val c = chat(Var(talk(3)), working)
      onScreen(c) {
        val t = c.transcript
        settle(t)
        keys(t) shouldBe Vector("m0", "m1", "m2", Grouping.workKey("w-helix"), Grouping.workKey("w-sam"))
        val v = view(t, "w-helix")
        val face = fx(v.avatar.get)
        fx(face.size.width()) shouldBe Transcript.avatar
        fx(face.position.left()) shouldBe Transcript.side
        fx(face.busy()) shouldBe true
        val texts = fx(under(v, { case x: everyui.component.TextView if x.visible() => x.content() }))
        texts should contain("Helix")
        texts should contain("5s")
        texts should contain("thinking")
        // the time moves on with the transcript's ticker
        pass(1000L)
        fx(under(v, { case x: everyui.component.TextView if x.visible() => x.content() })) should contain("6s")
        fx(under(view(t, "w-sam"), { case x: everyui.component.TextView if x.visible() => x.content() })) should
          contain(ChatTime.span(76000L))
        // the cancel, where the work can be stopped: named as in the status bar, and doing what it did there
        fx(v.cancel.visible()) shouldBe true
        fx(v.cancel.semantics().label) shouldBe ChatWords.cancel
        fx(v.cancel.tooltip()) shouldBe ChatWords.cancel
        click(v.cancel)
        cancelled shouldBe 1
        fx(view(t, "w-sam").cancel.visible()) shouldBe false
        // after the box, level with its first line, in the row
        val b = box(v)
        fx(v.cancel.position.left()) shouldBe (b.position.left() + b.size.width() + WorkingView.gap)
        fx(v.cancel.position.top()) should be >= b.position.top() - 8.0
        fx(v.cancel.position.left() + v.cancel.size.width()) should be <= 800.0 - Transcript.side
        // read out as who is busy and with what
        fx(t.list.rowAt(t.list.indexOf(Grouping.workKey("w-sam"))).get.semantics().label) shouldBe
          "Sam, " + ChatWords.busy + ", reviewing"
        // what they do changed in place, and gone
        fx(working @= working().map(w => if (w.id == "w-sam") w.copy(what = "reviewing the socket") else w))
        settle(t)
        fx(under(view(t, "w-sam"), { case x: everyui.component.TextView if x.visible() => x.content() })) should
          contain("reviewing the socket")
        fx(working @= working().filter(_.id == "w-sam"))
        settle(t)
        keys(t) shouldBe Vector("m0", "m1", "m2", Grouping.workKey("w-sam"))
        fx(working @= Vector.empty)
        settle(t)
        keys(t) shouldBe Vector("m0", "m1", "m2")
      }
    }

    "wrap what they are doing, cut to three lines with Show more, and Show less" in timed {
      val working = Var(Vector(Working("w-helix", helix, long, start), Working("w-sam", sam, "reviewing", start)))
      val c = chat(Var(talk(2)), working, width = 700.0)
      onScreen(c) {
        val t = c.transcript
        settle(t)
        val v = view(t, "w-helix")
        val line = fx(view(t, "w-sam").words.size.height())
        // wrapped to the box, no wider than a bubble may be, cut to three lines
        fx(v.words.size.width()) should be <= (t.widest() - Transcript.padX * 2.0)
        fx(v.words.elided()) shouldBe true
        fx(v.words.size.height()) should be > line * 2.0
        fx(v.words.size.height()) should be <= line * 3.5
        fx(v.more.visible()) shouldBe true
        fx(v.more.label()) shouldBe ChatWords.showMore
        // the box holds the words and the button
        val b = box(v)
        fx(v.more.position.top() + v.more.size.height()) should be <= b.size.height()
        fx(b.position.left() + b.size.width()) should be <= fx(v.cancel.position.left() + 0.5)
        val before = fx(t.list.heightAt(t.list.indexOf(Grouping.workKey("w-helix"))))
        click(v.more)
        settle(t)
        val opened = view(t, "w-helix")
        fx(opened.words.elided()) shouldBe false
        fx(opened.words.content()) shouldBe long
        fx(opened.more.label()) shouldBe ChatWords.showLess
        fx(t.list.heightAt(t.list.indexOf(Grouping.workKey("w-helix")))) should be > before
        click(opened.more)
        settle(t)
        fx(view(t, "w-helix").words.elided()) shouldBe true
        fx(t.list.heightAt(t.list.indexOf(Grouping.workKey("w-helix")))) shouldBe before
        // a short one: one line, a narrow box, no Show more
        val short = view(t, "w-sam")
        fx(short.more.visible()) shouldBe false
        fx(short.words.elided()) shouldBe false
        fx(box(short).size.width()) should be < 200.0
        // the words are not the transcript's to select or copy
        fx(v.words.selectable()) shouldBe false
      }
    }

    "be reached with the keys: Enter goes in, forward and back open and close" in timed {
      val working = Var(Vector(Working("w-helix", helix, long, start, Some(() => ()))))
      val c = chat(Var(talk(2)), working)
      onScreen(c) {
        val t = c.transcript
        settle(t)
        fx(t.focus())
        press(KeyCode.END)
        settle(t)
        fx(t.list.current()) shouldBe Some(Grouping.workKey("w-helix"))
        press(KeyCode.RIGHT)
        settle(t)
        fx(view(t, "w-helix").words.elided()) shouldBe false
        press(KeyCode.LEFT)
        settle(t)
        fx(view(t, "w-helix").words.elided()) shouldBe true
        press(KeyCode.ENTER)
        val v = view(t, "w-helix")
        fx(Focus.focused().exists(f => (f eq v.more) || (f eq v.cancel))) shouldBe true
        // and back out with an arrow
        press(KeyCode.UP)
        fx(Focus.focused().exists(_ eq t.list)) shouldBe true
        // no menu of the transcript's on it
        fx(t.menu()(t.list.currentItem.get)) shouldBe Nil
      }
    }

    "leave the status bar the connection alone, and nothing when connected" in timed {
      val working = Var(Vector(Working("w-helix", helix, "thinking", start, Some(() => ()))))
      val connection: Var[ConnectionState] = Var(ConnectionState.Connected)
      val c = chat(Var(talk(2)), working, connection = connection)
      onScreen(c) {
        settle(c.transcript)
        fx(c.status.size.height()) shouldBe 0.0
        fx(under(c.status, { case a: Avatar => a })) shouldBe Nil
        fx(connection @= ConnectionState.Offline(Some("no network"), Some(() => ())))
        fx(c.status.size.height()) should be > 0.0
        fx(under(c.status, { case a: Avatar => a })) shouldBe Nil
        fx(under(c.status, { case x: everyui.component.TextView => x.content() }).exists(_.contains("no network"))) shouldBe
          true
        fx(connection @= ConnectionState.Connected)
        fx(c.status.size.height()) shouldBe 0.0
        // turned off: back over the composer, out of the transcript
        fx(c.workingInline @= false)
        settle(c.transcript)
        fx(under(c.status, { case a: Avatar => a }).size) shouldBe 1
        keys(c.transcript) shouldBe Vector("m0", "m1")
      }
    }

    "keep a transcript at the bottom as rows come, grow, open and go" in timed {
      val working = Var(Vector.empty[Working])
      val c = chat(Var(talk(40)), working, width = 500.0, height = 500.0)
      onScreen(c) {
        val t = c.transcript
        settle(t)
        eventually(atBottom(t)) shouldBe true
        fx(working @= Vector(Working("w-helix", helix, "thinking", start)))
        settle(t)
        fx(atBottom(t)) shouldBe true
        fx(t.list.keyAt(t.list.indexAt(t.list.offset() + t.list.size.height() - 2.0))) shouldBe Grouping.workKey("w-helix")
        fx(working @= Vector(Working("w-helix", helix, long, start), Working("w-sam", sam, "reviewing", start)))
        settle(t)
        fx(atBottom(t)) shouldBe true
        click(view(t, "w-helix").more)
        settle(t)
        fx(atBottom(t)) shouldBe true
        fx(t.pin.pinned()) shouldBe true
        fx(working @= Vector.empty)
        settle(t)
        fx(atBottom(t)) shouldBe true
        fx(t.pin.pinned()) shouldBe true
        fx(t.list.keyAt(t.list.count - 1)) shouldBe "m39"
      }
    }

    "count none of them as new, and leave a transcript scrolled up where it is" in timed {
      val entries = Var(talk(40))
      val working = Var(Vector.empty[Working])
      val c = chat(entries, working, width = 500.0, height = 500.0)
      onScreen(c) {
        val t = c.transcript
        settle(t)
        fx(t.list.scrollBy(-900.0))
        fx(t.pin.pinned()) shouldBe false
        val offset = fx(t.list.offset())
        fx(working @= Vector(Working("w-helix", helix, long, start), Working("w-sam", sam, "reviewing", start)))
        settle(t)
        fx(t.list.offset()) shouldBe offset
        fx(t.pin.unseen()) shouldBe 0
        fx(t.jump.label()) shouldBe ChatWords.jumpToLatest
        // entries arriving go before them, and are counted
        fx(entries @= entries() ++ talk(2, 100))
        settle(t)
        fx(t.pin.unseen()) shouldBe 2
        fx(t.list.offset()) shouldBe offset
        keys(t).takeRight(4) shouldBe Vector("m100", "m101", Grouping.workKey("w-helix"), Grouping.workKey("w-sam"))
        fx(working @= Vector.empty)
        settle(t)
        fx(t.list.offset()) shouldBe offset
        fx(t.pin.unseen()) shouldBe 2
        fx(t.toLatest())
        settle(t)
        fx(t.pin.unseen()) shouldBe 0
      }
    }

    "mirror right to left" in timed {
      fx(Direction.set(true))
      try {
        val working = Var(Vector(Working("w-helix", helix, long, start, Some(() => ()))))
        val c = chat(Var(talk(2)), working)
        onScreen(c) {
          val t = c.transcript
          settle(t)
          val v = view(t, "w-helix")
          val b = box(v)
          fx(v.avatar.get.position.left()) shouldBe 800.0 - Transcript.side - Transcript.avatar
          fx(b.position.left() + b.size.width()) shouldBe (800.0 - Transcript.indent(800.0))
          fx(v.cancel.position.left() + v.cancel.size.width()) shouldBe (b.position.left() - WorkingView.gap)
          fx(v.cancel.position.left()) should be >= Transcript.side
          // the button's words at the box's start edge, the right
          fx(v.more.position.left() + v.more.size.width()) shouldBe
            (b.size.width() - Transcript.padX + WorkingView.moreInset)
        }
      } finally fx(Direction.set(false))
    }

    "draw as a phone's when narrow: a small avatar beside the name, the box from the side margin" in timed {
      val working = Var(Vector(Working("w-helix", helix, long, start, Some(() => ()))))
      val c = chat(Var(talk(2)), working, width = 390.0)
      onScreen(c) {
        val t = c.transcript
        settle(t)
        val v = view(t, "w-helix")
        val face = fx(v.avatar.get)
        fx(face.size.width()) shouldBe Transcript.smallAvatar
        fx(face.position.left()) shouldBe Transcript.side
        val name = fx(under(v, { case x: everyui.component.TextView if x.content() == "Helix" => x }).head)
        fx(name.parent().get.position.left()) shouldBe Transcript.side + Transcript.smallAvatar + Transcript.smallGap
        val b = box(v)
        fx(b.position.left()) shouldBe Transcript.side
        fx(b.position.top()) should be >= fx(face.position.top() + face.size.height())
        fx(v.cancel.position.left() + v.cancel.size.width()) should be <= 390.0 - Transcript.side
        fx(v.words.elided()) shouldBe true
      }
    }
  }

  "A chat with who is working in the status bar (the default)" should {
    "be as it was: a line each over the composer, nothing in the transcript" in timed {
      val working = Var(Vector(Working("w-helix", helix, "thinking", start, Some(() => ()))))
      val c = fx(new ChatView(Var(talk(2)), working = working))
      fx {
        c.transcript.dayMarkers @= false
        c.size.width @= 600.0
        c.size.height @= 400.0
      }
      onScreen(c) {
        settle(c.transcript)
        fx(c.workingInline()) shouldBe false
        fx(c.transcript.working()) shouldBe Vector.empty
        keys(c.transcript) shouldBe Vector("m0", "m1")
        fx(c.status.showsWorking()) shouldBe true
        fx(c.status.size.height()) should be > 0.0
        fx(under(c.status, { case a: Avatar => a }).size) shouldBe 1
        fx(under(c.status, { case x: everyui.component.TextView => x.content() })) should contain("Helix · thinking")
      }
    }
  }
}
