package everyui.chat

import everyui.component.{Component, Container, ScrollContainer}
import everyui.focus.Focus
import everyui.render.Platform
import everyui.{FxSpec, ui}
import reactify.*

import scala.concurrent.duration.*

/** What a transcript keeps for an entry (the app's own component, a tool call's details once opened) let go a while
  * after its row is scrolled away (`Transcript.evictAfter`), after the time and not before, and made again as it next
  * shows, the call still open and each scroll in it where it was; kept for good while `evictAfter` is unset. Run on a
  * clock and a timer the spec keeps (`Transcript.clock`, `Transcript.after`), so every wait is one the spec runs out. */
class TranscriptEvictSpec extends FxSpec {
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

  /** `ms` passed, and each wait due by then run, as its timer would. */
  private def pass(ms: Long): Unit = fx {
    time += ms
    var due = waits.filter(_._1 <= time)
    while (due.nonEmpty) {
      waits = waits.filterNot(w => due.exists(_ eq w))
      due.foreach(_._2())
      due = waits.filter(_._1 <= time)
    }
  }

  /** What the app's component and the call's details made, each a scroll of tall content, in turn. */
  private class Made {
    var made: List[(String, ScrollContainer)] = Nil
    var disposed: List[String] = Nil
    def make(k: String): Component = {
      val s = new ScrollContainer
      s.size.width @= 300.0
      s.size.height @= 100.0
      s.content.children += new Container {
        size.width @= 300.0
        size.height @= 1000.0
      }
      made = made :+ (k -> s)
      s.onDispose(() => disposed = disposed :+ k)
      s
    }
    def count(k: String): Int = made.count(_._1 == k)
    def last(k: String): ScrollContainer = made.filter(_._1 == k).last._2
  }

  // the app's component first, an opened call with details second, then enough messages to scroll them away
  private def transcript(m: Made): (Transcript, ChatEntry.Activity) = {
    val call = ChatEntry.activity("call", "Read a file", ActivityStatus.Succeeded, Some(() => m.make("call")))
    call.expanded @= true
    val entries = Var(Vector[ChatEntry](ChatEntry.custom("custom", () => m.make("custom")), call) ++
      (0 until 40).map(i => ChatEntry.message("m" + i, ada, self = false, "Message " + i + " of the conversation.")))
    val t = new Transcript(entries)
    t.dayMarkers @= false
    t.size.width @= 700.0
    t.size.height @= 400.0
    (t, call)
  }

  private def settle(t: Transcript): Unit = (1 to 5).foreach(_ => fx(t.list.refresh()))
  private def toTop(t: Transcript): Unit = {
    fx(t.list.scrollTop(0.0))
    settle(t)
  }
  private def toBottom(t: Transcript): Unit = {
    fx(t.list.scrollTop(t.list.total))
    settle(t)
  }

  "What a transcript keeps for an entry" should {
    "be let go a while after it is scrolled away, and made again with its scrolls and the call open as it shows" in timed {
      val m = new Made
      val (t, call) = fx(transcript(m))
      fx(t.evictAfter @= Some(10.seconds))
      fx(ui.children += t)
      toTop(t)
      eventually(m.count("custom") == 1 && m.count("call") == 1) shouldBe true
      fx {
        m.last("custom").scrollTo(120.0)
        m.last("call").scrollTo(60.0)
      }
      fx(m.last("custom").offset()) shouldBe 120.0
      toBottom(t)
      fx(t.keptNow.keySet) shouldBe Set("custom", "call")
      // not before the time
      pass(9000L)
      m.disposed shouldBe Nil
      fx(t.keptNow.size) shouldBe 2
      pass(1000L)
      m.disposed.sorted shouldBe List("call", "custom")
      fx(t.keptNow.isEmpty) shouldBe true
      // made again as each shows: once, the call still open, the scrolls where they were
      toTop(t)
      eventually(m.count("custom") == 2 && m.count("call") == 2) shouldBe true
      fx(call.expanded()) shouldBe true
      fx(m.last("call").parent().nonEmpty) shouldBe true
      eventually((m.last("custom").offset()) == 120.0 && (m.last("call").offset()) == 60.0) shouldBe true
      // shown, they are not let go however long it is
      pass(60000L)
      m.disposed.size shouldBe 2
      fx(t.dispose())
      m.disposed.size shouldBe 4
    }

    "be kept for good while evictAfter is unset" in timed {
      val m = new Made
      val (t, _) = fx(transcript(m))
      // (on by default: turned off, as an app does)
      fx(t.evictAfter @= None)
      fx(ui.children += t)
      toTop(t)
      eventually(m.count("custom") == 1 && m.count("call") == 1) shouldBe true
      toBottom(t)
      pass(3600000L)
      m.disposed shouldBe Nil
      waits shouldBe Vector.empty
      // set, what has been out of sight that long goes at once
      fx(t.evictAfter @= Some(1.minute))
      m.disposed.sorted shouldBe List("call", "custom")
      toTop(t)
      eventually(m.count("custom") == 2 && m.count("call") == 2) shouldBe true
      // unset again, nothing more goes
      fx(t.evictAfter @= None)
      toBottom(t)
      pass(3600000L)
      m.disposed.size shouldBe 2
      fx(t.dispose())
      m.disposed.size shouldBe 4
    }
  }
}
