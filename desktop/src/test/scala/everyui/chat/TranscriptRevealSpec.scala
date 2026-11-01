package everyui.chat

import everyui.async.Task
import everyui.component.Component
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import reactify.*

/** `Transcript.reveal`: an entry brought into view by its id, at the top or in the middle, the cursor on it and the
  * transcript let go of the bottom (unless that is where the entry lies), one not there yet looked for in earlier
  * entries fetched until it comes, one that is nowhere not found, and a reveal taken over by the next. */
class TranscriptRevealSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val me = Participant("u-me", "Me")

  private def talk(n: Int, from: Int = 0): Vector[ChatEntry] = (from until from + n).toVector.map { i =>
    ChatEntry.message("m" + i, if (i % 3 == 0) me else ada, self = i % 3 == 0,
      "Message " + i + (if (i % 4 == 0) ": long enough to wrap onto a second line in a transcript this narrow, and so a row " +
        "of a height of its own." else ""))
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

  // a few turns for the rows to be measured and laid out
  private def settle(t: Transcript): Unit = (1 to 5).foreach(_ => fx(t.list.refresh()))

  private def atBottom(t: Transcript): Boolean = t.list.offset() >= t.list.total - t.list.size.height() - 1.0
  // how far below the top of the view the entry starts
  private def below(t: Transcript, id: String): Double = t.list.top(t.list.indexOf(id)) - t.list.offset()

  private def sized(t: Transcript): Unit = fx {
    t.dayMarkers @= false
    t.size.width @= 400.0
    t.size.height @= 500.0
  }

  /** Earlier entries, fifty a page, the next turn, down to `m0`; and how many pages were asked for. */
  private class History(var left: Int) {
    var pages = 0
    val older: () => Task[Vector[ChatEntry]] = () => Task.async[Vector[ChatEntry]] { done =>
      pages += 1
      everyui.render.Platform.later(() => {
        val to = left
        val from = math.max(0, to - 50)
        left = from
        done(talk(to - from, from))
      })
    }
  }

  "Revealing an entry" should {
    "bring it to the top or the middle, the cursor on it, let go of the bottom until scrolled back down" in {
      val entries = Var(talk(20) ++ Vector(ChatEntry.marker("unread", "12 new since you left")) ++ talk(60, 20))
      val t = fx(new Transcript(entries))
      sized(t)
      onScreen(t) {
        settle(t)
        eventually(atBottom(t)) shouldBe true
        var found: Option[Boolean] = None
        fx(t.reveal("unread", found = f => found = Some(f)))
        found shouldBe Some(true)
        settle(t)
        fx(below(t, "unread")) shouldBe 0.0 +- 0.5
        fx(t.list.current()) shouldBe Some("unread")
        fx(t.pin.pinned()) shouldBe false
        fx(t.jump.visible()) shouldBe true
        // what arrives meanwhile is counted, not scrolled to
        fx(entries @= entries() ++ talk(2, 200))
        settle(t)
        fx(below(t, "unread")) shouldBe 0.0 +- 0.5
        fx(t.pin.unseen()) shouldBe 2
        // in the middle, a tall one and a short one
        List("m40", "m41").foreach { id =>
          fx(t.reveal(id, align = 0.5))
          settle(t)
          fx {
            val i = t.list.indexOf(id)
            t.list.top(i) + t.list.heightAt(i) / 2.0 - t.list.offset()
          } shouldBe 250.0 +- 1.0
          fx(t.list.current()) shouldBe Some(id)
        }
        // scrolled back down, it is kept at the bottom again
        fx(t.list.scrollBy(100000.0))
        fx(t.pin.pinned()) shouldBe true
        fx(t.jump.visible()) shouldBe false
        // one that lies at the bottom leaves it kept there
        fx(t.reveal("m201"))
        settle(t)
        fx(t.pin.pinned()) shouldBe true
        fx(atBottom(t)) shouldBe true
        fx(t.list.current()) shouldBe Some("m201")
      }
    }

    "fetch earlier entries until the one asked for is there, and say when one is nowhere" in {
      val entries = Var(talk(30, 1000))
      val history = new History(500)
      val t = fx(new Transcript(entries, Some(history.older)))
      sized(t)
      onScreen(t) {
        settle(t)
        eventually(!t.loading()) shouldBe true
        // m120 is four pages of fifty back from the 500 not yet fetched
        @volatile var found: Option[Boolean] = None
        fx(t.reveal("m120", found = f => found = Some(f)))
        eventually(found.nonEmpty) shouldBe true
        found shouldBe Some(true)
        fx(entries().exists(_.id == "m120")) shouldBe true
        fx(t.exhausted()) shouldBe false
        settle(t)
        fx(below(t, "m120")) shouldBe 0.0 +- 0.5
        fx(t.list.current()) shouldBe Some("m120")
        fx(t.pin.pinned()) shouldBe false
        // one that is nowhere: everything fetched, and not found
        @volatile var missing: Option[Boolean] = None
        fx(t.reveal("gone", found = f => missing = Some(f)))
        eventually(missing.nonEmpty) shouldBe true
        missing shouldBe Some(false)
        fx(t.exhausted()) shouldBe true
        fx(entries().head.id) shouldBe "m0"
      }
      // and with nothing to fetch, at once
      val plain = fx(new Transcript(Var(talk(5))))
      onScreen(plain) {
        var found: Option[Boolean] = None
        fx(plain.reveal("gone", found = f => found = Some(f)))
        found shouldBe Some(false)
      }
    }

    "be taken over by another asked for while it waits for earlier entries" in {
      val entries = Var(talk(30, 1000))
      // the pages come only when let through
      var answers: List[Vector[ChatEntry] => Unit] = Nil
      var left = 500
      val older = () => Task.async[Vector[ChatEntry]](done => answers = answers :+ done)
      def answer(): Unit = fx {
        val done = answers.head
        answers = answers.tail
        val from = math.max(0, left - 50)
        val page = talk(left - from, from)
        left = from
        done(page)
      }
      val t = fx(new Transcript(entries, Some(older)))
      sized(t)
      onScreen(t) {
        settle(t)
        // (opened at the bottom, far enough from the top that nothing earlier is asked for)
        fx(answers) shouldBe Nil
        var first: Option[Boolean] = None
        var second: Option[Boolean] = None
        fx(t.reveal("m10", found = f => first = Some(f)))
        eventually(answers.nonEmpty) shouldBe true
        fx(t.reveal("m1010", found = f => second = Some(f)))
        // the one already there is brought into view at once; the first hears it was taken over once its page comes
        second shouldBe Some(true)
        fx(t.list.current()) shouldBe Some("m1010")
        first shouldBe None
        answer()
        eventually(first.nonEmpty) shouldBe true
        first shouldBe Some(false)
        settle(t)
        fx(below(t, "m1010")) shouldBe 0.0 +- 0.5
        fx(t.list.current()) shouldBe Some("m1010")
      }
    }
  }
}
