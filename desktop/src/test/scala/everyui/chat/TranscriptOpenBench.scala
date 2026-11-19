package everyui.chat

import everyui.focus.Focus
import everyui.{FxSpec, ui}
import reactify.*

/** What opening a long conversation costs the drawing thread: a phone-sized `ChatView` over a hundred Markdown messages
  * as a coding agent's has them (a list and a fenced Scala block each, about 600 characters), made and put on the
  * screen, timed to the end of the turn that made it and to every row in view made and measured, with how many
  * messages' words were made. Run on its own (`desktop/testOnly everyui.chat.TranscriptOpenBench`, `-Dbench.rounds=`
  * to repeat) it prints its numbers; it holds nothing to a time, so a busy machine cannot fail it. */
class TranscriptOpenBench extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val me = Participant("u-me", "Me")

  "Opening a conversation" should {
    "be timed to its first turn and to a full fill" in {
      val rounds = sys.props.get("bench.rounds").flatMap(_.toIntOption).getOrElse(2)
      val results = (1 to rounds).map(_ => open())
      results.foreach { case (first, full, rows, turns, made) =>
        info(f"first turn $first%.1f ms, full fill $full%.1f ms, $rows rows in view, $turns turns, $made made")
        println(f"[bench] transcript-open first=$first%.1f full=$full%.1f rows=$rows turns=$turns made=$made")
      }
      results.forall(_._3 > 0) shouldBe true
    }
  }

  /** A message as a coding agent writes one: a paragraph, a list, a fenced block and a closing line. */
  private def answer(i: Int): String = "The socket drops after **60 s** without traffic, and `DurableSocket` " +
    "reconnects at once, every time (" + i + "). Two changes fix it:\n\n- a heartbeat every 25 s keeps the connection " +
    "alive through the proxy\n- reconnecting waits longer each time, up to 30 s, with some jitter\n- `SocketSpec` " +
    "covers both\n\n```scala\nfinal case class Backoff(base: Double = 0.5, max: Double = 30.0) {\n  def delay(attempt: " +
    "Int): Double =\n    math.min(max, base * math.pow(2.0, attempt.toDouble))\n}\n```\n\nI ran the tests again and " +
    "they pass, " + (400 + i) + " in all. See [the proxy docs](https://example.com/proxy#idle) for the limit."

  private def history(count: Int): Vector[ChatEntry] = (0 until count).toVector.map { i =>
    if (i % 4 == 3) ChatEntry.message("m" + i, me, self = true, "Thanks, keep the fixed delay for the tests (" + i +
      ").", Some(1790000000000L + i * 60000L))
    else ChatEntry.message("m" + i, ada, self = false, answer(i), Some(1790000000000L + i * 60000L))
  }

  private var rendered = 0

  // one open: the FX thread's time for the turn that makes and shows it, its time until every row in view is made and
  // every size read, the rows in view, the turns that took, and the messages' words made
  private def open(): (Double, Double, Int, Int, Int) = {
    rendered = 0
    val entries = Var(history(100))
    var chat: ChatView = null
    fx(ui.children.clear())
    var first = 0.0
    var turns = 1
    val full = fxWorked {
      first = worked {
        chat = new ChatView(entries, render = (text, widest) => {
          rendered += 1
          MessageMarkdown.render(text, widest)
        })
        chat.size.width @= 412.0
        chat.size.height @= 870.0
        ui.children += chat
      }
      while (turns < 600 && !fx(filled(chat))) {
        Thread.sleep(2L)
        turns += 1
      }
    }
    val rows = fx(chat.transcript.list.rows.count(r => r.visible() && r.item().nonEmpty))
    fx {
      ui.children.clear()
      chat.dispose()
      Focus.focused @= None
    }
    (first, full, rows, turns, rendered)
  }

  // every row the view shows made, at the bottom, and the sizes asked for read
  private def filled(chat: ChatView): Boolean = {
    val list = chat.transcript.list
    val shownFirst = list.indexAt(list.offset())
    val shownLast = list.indexAt(list.offset() + list.size.height())
    list.count > 0 && everyui.component.feature.MeasuredFeature.settled &&
      (shownFirst to shownLast).forall(i => list.rowAt(i).exists(_.item().nonEmpty)) &&
      list.offset() >= list.total - list.size.height() - 1.0
  }
}
