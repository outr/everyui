package everyui.chat

import everyui.Color
import everyui.component.{Component, Container}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.{FxSpec, ui}
import reactify.*

/** An entry's rail on the desktop's real nodes: a line down its row's start edge only where the entry has one (and no
  * component for it where it has none), the row's whole height so that rows of the same colour one after another draw
  * one unbroken line, mirrored right to left; the room kept for the rails where the transcript keeps some, so the rows
  * with and without one line up, and none where it does not; a rail set after the entry shows, and changed, and taken
  * away; the place kept as the room comes; and a message's buttons beside its bubble still. */
class TranscriptRailSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val me = Participant("u-me", "Me")
  private val red = Color.fromHex("#e5484d")
  private val blue = Color.fromHex("#0090ff")
  private val day = 86400000L

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

  private def settle(t: Transcript): Unit = (1 to 5).foreach(_ => fx(t.list.refresh()))

  private def transcript(entries: Var[Vector[ChatEntry]], width: Double = 700.0, height: Double = 900.0,
                         rails: Boolean = false): Transcript = {
    val t = fx(new Transcript(entries))
    fx {
      t.rails @= rails
      t.size.width @= width
      t.size.height @= height
    }
    t
  }

  // (on the FX thread, or from it)
  private def onFx[T](f: => T): T = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)

  private def row(t: Transcript, key: String): ChatRow =
    onFx(t.list.rowAt(t.list.indexOf(key)).get.asInstanceOf[ChatRow])

  // a little of every kind: a thread of messages, a tool call and reasoning in red, then a marker, a card and a message
  // in blue, and one message in none; the transcript puts a day's separator before the reasoning (dated the first day),
  // the last blue message (the second) and the last message (the third)
  private def topics(): Vector[ChatEntry] = Vector(
    ChatEntry.message("m0", ada, self = false, "Where are we with the release?", rail = Some(red)),
    ChatEntry.message("m1", ada, self = false, "I need the notes by noon, and the test runs too, so that the summary " +
      "says what changed since the last one.", rail = Some(red)),
    ChatEntry.message("m2", me, self = true, "Close.", rail = Some(red)),
    ChatEntry.activity("a3", "Ran the test suite", ActivityStatus.Succeeded, body = "`sbt test`", rail = Some(red)),
    ChatEntry.thinking("t4", 0L, done = Some(4000L), rail = Some(red)),
    ChatEntry.marker("k5", "Topic: Release 2.0", rail = Some(blue)),
    ChatEntry.custom("c6", () => new Container {
      size.width @= 240.0
      size.height @= 60.0
    }, rail = Some(blue)),
    ChatEntry.message("m7", ada, self = false, "Thursday, then.", Some(day * 2L), rail = Some(blue)),
    ChatEntry.message("m8", me, self = true, "No topic for this one.", Some(day * 3L)))

  private val keys = Vector("m0", "m1", "m2", "a3", "t4", "k5", "c6", "m7", "m8")

  // the bubble's left in the row, where the row shows a message
  private def bubbleLeft(r: ChatRow): Double = onFx(r.shown.get.position.left() + r.message.get.bubble.position.left())

  "An entry's rail" should {
    "be drawn only where it is set, the row's whole height, unbroken from one row to the next of the same colour" in {
      val t = transcript(Var(topics()), rails = true)
      onScreen(t) {
        settle(t)
        keys.foreach { k =>
          val r = row(t, k)
          val entry = fx(r.item().get)
          if (k == "m8") {
            fx(r.rail) shouldBe None
            fx(r.children().size) shouldBe 1
          } else {
            val line = fx(r.rail.get)
            fx(line.visible()) shouldBe true
            fx(line.backgroundColor()) shouldBe fx(entry.rail().get)
            fx(line.position.left()) shouldBe Transcript.railInset
            fx(line.position.top()) shouldBe 0.0
            fx(line.size.width()) shouldBe Transcript.railWidth
            fx(line.size.height()) shouldBe fx(r.size.height())
            fx(r.size.height()) should be > 0.0
          }
        }
        // each row starts where the one before it ends, the day's separators the transcript puts in too: the space
        // above a group is in the row's own height, so the line runs on through it
        val count = fx(t.list.count)
        count shouldBe keys.size + 3
        (0 until count - 1).foreach { i =>
          val (a, b) = fx((t.list.rowAt(i).get, t.list.rowAt(i + 1).get))
          fx(a.position.top() + a.size.height()) shouldBe (fx(b.position.top()) +- 0.001)
        }
        // a separator between two entries of one thread carries its rail on; one between two threads, or a thread and
        // none, has none
        def before(key: String): ChatRow = onFx(t.list.rowAt(t.list.indexOf(key) - 1).get.asInstanceOf[ChatRow])
        fx(Grouping.isDay(before("t4").item().get)) shouldBe true
        fx(before("t4").rail.map(_.backgroundColor())) shouldBe Some(red)
        fx(before("t4").rail.get.size.height()) shouldBe fx(before("t4").size.height())
        fx(before("m7").rail.map(_.backgroundColor())) shouldBe Some(blue)
        fx(Grouping.isDay(before("m8").item().get)) shouldBe true
        fx(before("m8").rail.forall(!_.visible())) shouldBe true
      }
    }

    "sit at the start edge, the right right to left, with the rows after the room kept for it" in {
      fx(Direction.set(true))
      try {
        val t = transcript(Var(topics()), rails = true)
        onScreen(t) {
          settle(t)
          val w = fx(t.list.size.width())
          keys.foreach { k =>
            val r = row(t, k)
            val v = fx(r.shown.get)
            fx(r.size.width()) shouldBe w
            fx(v.size.width()) shouldBe w - Transcript.railGutter
            fx(v.position.left()) shouldBe 0.0
            fx(r.rail.map(_.position.left())) shouldBe (if (k == "m8") None
              else Some(w - Transcript.railInset - Transcript.railWidth))
          }
          // everybody else's bubbles at the right, past the avatar and the room for the rail
          List("m0", "m7").foreach { k =>
            val r = row(t, k)
            bubbleLeft(r) + fx(r.message.get.bubble.size.width()) shouldBe
              (w - Transcript.railGutter - Transcript.side - Transcript.lead +- 0.001)
          }
        }
      } finally fx(Direction.set(false))
    }

    "keep the rows in line, with a rail and without, where the transcript keeps room, and none where it does not" in {
      val entries = Var(topics())
      val t = transcript(entries, rails = true)
      onScreen(t) {
        settle(t)
        val w = fx(t.list.size.width())
        // somebody else's messages, one with a rail and one without, start at the same place
        entries @= entries().map {
          case m: ChatEntry.Message if m.id == "m8" => ChatEntry.message("m8", ada, self = false, "None here.")
          case e => e
        }
        settle(t)
        bubbleLeft(row(t, "m7")) shouldBe bubbleLeft(row(t, "m8"))
        bubbleLeft(row(t, "m8")) shouldBe Transcript.railGutter + Transcript.side + Transcript.lead
        keys.foreach { k =>
          fx(row(t, k).shown.get.position.left()) shouldBe Transcript.railGutter
          fx(row(t, k).shown.get.size.width()) shouldBe w - Transcript.railGutter
        }
      }
      // none kept: every view the row's width from its edge, as without rails, the rails in the margin the rows keep
      val plain = transcript(Var(topics()))
      onScreen(plain) {
        settle(plain)
        val w = fx(plain.list.size.width())
        keys.foreach { k =>
          fx(row(plain, k).shown.get.position.left()) shouldBe 0.0
          fx(row(plain, k).shown.get.size.width()) shouldBe w
        }
        bubbleLeft(row(plain, "m7")) shouldBe Transcript.side + Transcript.lead
        Transcript.railInset + Transcript.railWidth should be < Transcript.side
      }
    }

    "make nothing for a transcript with no rails, and draw one set after the entry shows" in {
      val entries = Var[Vector[ChatEntry]]((0 until 30).toVector.map(i =>
        ChatEntry.message("n" + i, if (i % 3 == 0) me else ada, self = i % 3 == 0, "Message " + i)))
      val t = transcript(entries, height = 500.0)
      onScreen(t) {
        settle(t)
        val rows = fx(t.list.rows.filter(r => r.visible() && r.item().nonEmpty).map(_.asInstanceOf[ChatRow]))
        rows.size should be > 5
        rows.foreach { r =>
          fx(r.children().size) shouldBe 1
          fx(r.rail) shouldBe None
          fx(r.shown.get.position.left()) shouldBe 0.0
        }
        // a message's thread known after it arrives: its line made then, and only on its row
        val last = fx(entries().last)
        val r = row(t, last.id)
        val height = fx(r.size.height())
        fx(last.rail @= Some(red))
        fx(r.rail.map(_.backgroundColor())) shouldBe Some(red)
        fx(r.rail.get.size.height()) shouldBe height
        fx(r.size.height()) shouldBe height
        fx(rows.count(_.rail.nonEmpty)) shouldBe 1
        // changed, and taken away
        fx(last.rail @= Some(blue))
        fx(r.rail.get.backgroundColor()) shouldBe blue
        fx(last.rail @= None)
        fx(r.rail.get.visible()) shouldBe false
        // and a row handed another entry follows that entry's
        fx(last.rail @= Some(red))
        entries @= entries().dropRight(1)
        settle(t)
        fx(t.list.rows.collect { case c: ChatRow if c.visible() && c.item().nonEmpty => c }
          .forall(c => c.rail.forall(l => !l.visible()))) shouldBe true
      }
    }

    "keep what shows where it is as the room for the rails comes, and say the rail's label" in {
      val entries = Var[Vector[ChatEntry]]((0 until 200).toVector.map(i =>
        ChatEntry.message("n" + i, if (i % 3 == 0) me else ada, self = i % 3 == 0, "Message " + i +
          (if (i % 4 == 0) ": long enough to wrap onto a second line in a transcript this narrow." else ""),
          rail = Some(if (i / 7 % 2 == 0) red else blue))))
      val t = transcript(entries, width = 390.0, height = 600.0)
      onScreen(t) {
        settle(t)
        fx(t.list.scrollBy(-2000.0))
        settle(t)
        fx(t.pin.pinned()) shouldBe false
        val (key, dy) = fx {
          val i = t.list.indexAt(t.list.offset())
          (t.list.keyAt(i), t.list.top(i) - t.list.offset())
        }
        fx(t.rails @= true)
        settle(t)
        fx(t.list.top(t.list.indexOf(key)) - t.list.offset()) shouldBe (dy +- 0.5)
        fx(row(t, key).shown.get.position.left()) shouldBe Transcript.railGutter
      }
      val m = ChatEntry.message("s", ada, self = false, "Hello", rail = Some(red))
      fx(t.said(m)) shouldBe "Ada: Hello"
      fx(m.railLabel @= Some("Release planning"))
      fx(t.said(m)) shouldBe "Ada: Hello, Release planning"
    }

    "leave a message's buttons beside its bubble" in {
      val t = transcript(Var(topics()), rails = true)
      onScreen(t) {
        settle(t)
        fx {
          // no pointer used just now (the window's under the pointer the suite's earlier specs moved): the keys moved
          // the cursor, so the buttons show beside the message under it
          everyui.event.Pointer.usedAt = -1.0e9
          t.list.focus()
          t.list.current @= Some("m7")
        }
        eventually(t.actions.exists(b => b.showing() && b.message().exists(_.id == "m7"))) shouldBe true
        val r = row(t, "m7")
        val b = fx(r.message.get.bubble)
        fx(t.actions.get.position.left()) shouldBe
          (bubbleLeft(r) + fx(b.size.width()) + MessageActions.gap +- 0.5)
      }
    }
  }
}
