package everyui.chat

import everyui.component.{Component, Container, ScrollContainer}
import everyui.event.{Fling, Flick}
import everyui.focus.Focus
import everyui.markdown.CodeBlockView
import everyui.perf.Quality
import everyui.{FxSpec, ui}
import javafx.scene.input.{MouseButton, MouseEvent}
import reactify.*

/** A finger's drag that starts on a part of a transcript that hears the pointer itself, as Android's own nested scrolls
  * have it: on a code block (a rail across the transcript), a drag mostly down scrolls the transcript and leaves the
  * code where it was, and one mostly across scrolls the code and leaves the transcript; on a tool call's row, a drag
  * down scrolls the transcript and opens nothing, while a tap still opens it; and a flick after either drag down glides
  * the transcript on, never the code. Each press is fired at the part it lands on and climbs to the transcript, as
  * JavaFX sends a touch screen's finger (synthesized), all in one turn of the FX thread, timed on `Flick.ticks`, and a
  * glide goes all the way at once (as at Minimal), so the distances are exact. */
class TranscriptDragSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private var now = 0.0

  private def onFx[T](f: => T): T = if (javafx.application.Platform.isFxApplicationThread) f else fx(f)

  private def mouse(on: Component, kind: javafx.event.EventType[MouseEvent], x: Double, y: Double,
                    still: Boolean = true): Unit = onFx {
    javafx.event.Event.fireEvent(on.node.fx, new MouseEvent(kind, x, y, x, y, MouseButton.PRIMARY, 1, false, false,
      false, false, true, false, false, true, false, still, null))
  }

  // a finger pressed at `x`, `y` on `on`, moved `dx`, `dy` in eight moves 16 ms apart, then `still` ms before it lifts
  // and the platform's click comes where it lifted
  private def drag(on: Component, x: Double, y: Double, dx: Double, dy: Double, still: Double): Unit = onFx {
    mouse(on, MouseEvent.MOUSE_PRESSED, x, y)
    (1 to 8).foreach { i =>
      now += 16.0
      mouse(on, MouseEvent.MOUSE_DRAGGED, x + dx * i / 8, y + dy * i / 8, still = false)
    }
    now += still
    mouse(on, MouseEvent.MOUSE_RELEASED, x + dx, y + dy, still = false)
    mouse(on, MouseEvent.MOUSE_CLICKED, x + dx, y + dy, still = false)
  }

  private def tap(on: Component, x: Double, y: Double): Unit = onFx {
    mouse(on, MouseEvent.MOUSE_PRESSED, x, y)
    now += 60.0
    mouse(on, MouseEvent.MOUSE_RELEASED, x, y)
    mouse(on, MouseEvent.MOUSE_CLICKED, x, y)
  }

  private def all(c: Component): List[Component] = c :: (c match {
    case s: ScrollContainer => s.children().toList.flatMap(all) ++ all(s.content)
    case k: Container => k.children().toList.flatMap(all)
    case _ => Nil
  })

  // twenty messages, then one with a code block far wider than the transcript, then a tool call that opens
  private def entries(): (Vector[ChatEntry], ChatEntry.Activity) = {
    val call = ChatEntry.activity("a", "Ran the tests", ActivityStatus.Succeeded,
      Some(() => ActivityView.textDetails("sbt test", "all passed")))
    val code = "```scala\n" + (1 to 6).map(i => "val line" + i + " = \"" + ("x" * 300) + "\"").mkString("\n") + "\n```"
    ((0 until 20).toVector.map(i => ChatEntry.message("m" + i, ada, self = false, "Message " + i + " of the thread.")) :+
      ChatEntry.message("code", ada, self = false, code) :+ call, call)
  }

  private def onScreen[T](test: (Transcript, ChatEntry.Activity) => T): T = {
    val (es, call) = entries()
    val t = fx {
      Flick.ticks = () => now
      Quality.glides = false
      ui.children.clear()
      val t = new Transcript(Var(es))
      t.size.width @= 700.0
      t.size.height @= 700.0
      ui.children += t
      t
    }
    (1 to 5).foreach(_ => fx(t.list.refresh()))
    try test(t, call) finally fx {
      Flick.ticks = () => everyui.render.Platform.ticks()
      Quality.glides = true
      ui.children.clear()
      t.dispose()
      Focus.focused @= None
    }
  }

  private def row(t: Transcript, key: String): Component = onFx(t.list.rowAt(t.list.indexOf(key)).get)
  // the code block's rail, and the words in it a finger lands on
  private def rail(t: Transcript): ScrollContainer = onFx(all(row(t, "code")).collectFirst {
    case c: CodeBlockView => all(c).collectFirst { case s: ScrollContainer if s.horizontal => s }.get
  }.get)
  private def words(t: Transcript): Component = onFx(rail(t).content.children().head)
  // the tool call's row, which a click opens
  private def callBox(t: Transcript): Component = onFx(all(row(t, "a")).collectFirst { case v: ActivityView => v }.get
    .children().head)
  private def middle(c: Component): (Double, Double) =
    onFx((c.absoluteLeft + math.min(c.size.width(), 600.0) / 2.0, c.absoluteTop + c.size.height() / 2.0))

  // where an entry's row is on the screen: the transcript scrolled by a drag moves it as far as the finger went (its
  // offset may move further, where rows above were drawn at heights of their own as they came into view)
  private def top(t: Transcript, key: String): Double = onFx(row(t, key).absoluteTop)

  "A finger's drag on a transcript" should {
    "scroll the transcript, not the code, where it goes down a code block" in onScreen { (t, _) =>
      fx(t.list.offset()) should be > 300.0
      val before = top(t, "code")
      val (x, y) = middle(words(t))
      drag(words(t), x, y, -12.0, 200.0, still = Fling.stopped + 10.0)
      top(t, "code") shouldBe (before + 200.0) +- 0.001
      fx(rail(t).offset()) shouldBe 0.0
    }
    "scroll the code, not the transcript, where it goes across a code block" in onScreen { (t, _) =>
      val before = fx(t.list.offset())
      val (x, y) = middle(words(t))
      drag(words(t), x, y, -200.0, 12.0, still = Fling.stopped + 10.0)
      fx(rail(t).offset()) shouldBe 200.0 +- 0.001
      fx(t.list.offset()) shouldBe before
    }
    "scroll the transcript, and open nothing, where it goes down a tool call's row" in onScreen { (t, call) =>
      val before = top(t, "a")
      val (x, y) = middle(callBox(t))
      drag(callBox(t), x, y, -6.0, 200.0, still = Fling.stopped + 10.0)
      top(t, "a") shouldBe (before + 200.0) +- 0.001
      fx(call.expanded()) shouldBe false
    }
    "still open a tool call's row on a tap" in onScreen { (t, call) =>
      val (x, y) = middle(callBox(t))
      tap(callBox(t), x, y)
      fx(call.expanded()) shouldBe true
    }
    "fling the transcript, never the code, after a flick down either" in onScreen { (t, call) =>
      val offset = fx(t.list.offset())
      val before = top(t, "code")
      val (x, y) = middle(words(t))
      drag(words(t), x, y, -12.0, 160.0, still = 0.0)
      top(t, "code") should be > (before + 161.0)
      fx(rail(t).offset()) shouldBe 0.0
      fx(t.list.scrollTop(offset))
      val callBefore = top(t, "a")
      val (cx, cy) = middle(callBox(t))
      drag(callBox(t), cx, cy, 6.0, 160.0, still = 0.0)
      top(t, "a") should be > (callBefore + 161.0)
      fx(call.expanded()) shouldBe false
    }
  }
}
