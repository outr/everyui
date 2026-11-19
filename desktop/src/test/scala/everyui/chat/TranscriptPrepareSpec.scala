package everyui.chat

import everyui.component.Component
import everyui.focus.Focus
import everyui.render.Platform
import everyui.{FxSpec, ui}
import reactify.*

import scala.concurrent.duration.*

/** A conversation made ahead of being shown (`ChatView.prepare`): its first screen made off the screen, nothing made
  * again as it is shown; never shown, its rows let go after `Transcript.evictAfter`, and nothing left once it is
  * disposed of. One set aside keeps its rows for `evictAfter`, lets them go after it, and makes them again as it is
  * put back, at its bottom. Run on a clock and a timer the spec keeps (`Transcript.clock`, `Transcript.after`). */
class TranscriptPrepareSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  @volatile private var time = 1790000000000L
  @volatile private var waits: Vector[(Long, () => Unit)] = Vector.empty

  private def timed[T](test: => T): T = {
    time = 1790000000000L
    waits = Vector.empty
    fx {
      Transcript.clock = () => time
      Transcript.after = (s, f) => waits = waits :+ ((time + math.round(s * 1000.0), f))
    }
    try test finally fx {
      Transcript.clock = () => everyui.util.Clock.real()
      Transcript.after = (s, f) => Platform.after(s, f)
      ui.children.clear()
      Focus.focused @= None
    }
  }

  // `ms` passed, and each wait due by then run, as its timer would
  private def pass(ms: Long): Unit = fx {
    time += ms
    var due = waits.filter(_._1 <= time)
    while (due.nonEmpty) {
      waits = waits.filterNot(w => due.exists(_ eq w))
      due.foreach(_._2())
      due = waits.filter(_._1 <= time)
    }
  }

  private def history(n: Int): Vector[ChatEntry] = (0 until n).toVector.map { i =>
    ChatEntry.message("m" + i, ada, self = false, "Step " + i + ": the socket drops after a minute idle, so it now " +
      "sends a heartbeat every 25 seconds.\n\n- one\n- two")
  }

  private def counting(made: scala.collection.mutable.ArrayBuffer[String]): (Val[String], Val[Double]) => Component =
    (text, widest) => {
      made += text()
      MessageMarkdown.render(text, widest)
    }

  private def handed(chat: ChatView): Int = chat.transcript.list.rows.count(r => r.visible() && r.item().nonEmpty)

  "A conversation made ahead" should {
    "make its first screen off the screen, and nothing again as it is shown" in timed {
      val made = scala.collection.mutable.ArrayBuffer.empty[String]
      val chat = fx {
        ui.children.clear()
        val c = new ChatView(Var(history(40)), render = counting(made))
        c.prepare(412.0, 870.0)
        c
      }
      try {
        (1 to 3).foreach(_ => fx(()))
        fx(handed(chat)) should be > 2
        fx(chat.transcript.list.rowAt(39).exists(_.item().exists(_.id == "m39"))) shouldBe true
        val before = made.size
        val live = fx(Component.liveNow)
        fx {
          chat.size.width @= 412.0
          chat.size.height @= 870.0
          ui.children += chat
        }
        (1 to 3).foreach(_ => fx(()))
        made.size shouldBe before
        fx(Component.liveNow) shouldBe live
        val list = chat.transcript.list
        fx(list.offset() >= list.total - list.size.height() - 1.0) shouldBe true
      } finally fx {
        ui.children.clear()
        chat.dispose()
      }
    }
    "let its rows go if it is not shown within evictAfter, and leave nothing once disposed of" in timed {
      val made = scala.collection.mutable.ArrayBuffer.empty[String]
      val base = fx(Component.liveNow)
      val chat = fx {
        ui.children.clear()
        val c = new ChatView(Var(history(40)), render = counting(made))
        c.prepare(412.0, 870.0)
        c
      }
      (1 to 3).foreach(_ => fx(()))
      fx(chat.transcript.list.rowsMade) should be > 2
      val full = fx(Component.liveNow)
      // not before its time
      pass(119000L)
      fx(chat.transcript.list.rowsMade) should be > 2
      pass(2000L)
      fx(chat.transcript.list.rowsMade) shouldBe 0
      fx(Component.liveNow) should be < full
      (1 to 3).foreach(_ => fx(()))
      // nothing made again while it is off the screen
      fx(chat.transcript.list.rowsMade) shouldBe 0
      fx(chat.dispose())
      (1 to 3).foreach(_ => fx(()))
      fx(Component.liveNow) shouldBe base
    }
  }

  "A conversation set aside" should {
    "keep its rows for evictAfter, let them go after it, and make them again at its bottom as it is put back" in timed {
      val made = scala.collection.mutable.ArrayBuffer.empty[String]
      val chat = fx {
        ui.children.clear()
        val c = Component.heldAside(new ChatView(Var(history(40)), render = counting(made)))
        c.size.width @= 412.0
        c.size.height @= 870.0
        ui.children += c
        c
      }
      try {
        (1 to 3).foreach(_ => fx(()))
        val shown = fx(handed(chat))
        shown should be > 2
        fx(ui.children -= chat)
        pass(60000L)
        // kept a while: put back, nothing is made again
        val before = made.size
        fx(ui.children += chat)
        (1 to 3).foreach(_ => fx(()))
        made.size shouldBe before
        fx(ui.children -= chat)
        pass(121000L)
        fx(chat.transcript.list.rowsMade) shouldBe 0
        // put back: made again, at the bottom
        fx(ui.children += chat)
        (1 to 3).foreach(_ => fx(()))
        fx(handed(chat)) shouldBe shown
        made.size should be > before
        val list = chat.transcript.list
        fx(list.offset() >= list.total - list.size.height() - 1.0) shouldBe true
        fx(list.rowAt(39).map(r => list.size.height() - r.position.top() - r.size.height()).getOrElse(-1.0)) shouldBe
          0.0 +- 1.0
      } finally fx {
        ui.children.clear()
        chat.dispose()
      }
    }
    "keep them for good while evictAfter is unset" in timed {
      val chat = fx {
        ui.children.clear()
        val c = Component.heldAside(new ChatView(Var(history(20))))
        c.transcript.evictAfter @= None
        c.size.width @= 412.0
        c.size.height @= 870.0
        ui.children += c
        c
      }
      try {
        (1 to 3).foreach(_ => fx(()))
        fx(ui.children -= chat)
        pass(3600000L)
        fx(chat.transcript.list.rowsMade) should be > 2
      } finally fx {
        ui.children.clear()
        chat.dispose()
      }
    }
  }
}
