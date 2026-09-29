package everyui.chat

import everyui.async.Task
import everyui.component.{Button, Component, Container}
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** The transcript on the desktop's real nodes: kept at the bottom while messages arrive and stream, let go on a scroll
  * up with a count of what arrives, taken back down by "Jump to latest", earlier history put before the rest without
  * moving what shows, the app's components kept as they are while scrolled away, and the keys. */
class TranscriptSpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val me = Participant("u-me", "Me")

  private def press(code: KeyCode): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false))
  }

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

  private def settle(t: Transcript): Unit = {
    // a few turns for the rows to be measured and laid out
    (1 to 5).foreach(_ => fx(t.list.refresh()))
  }

  private def atBottom(t: Transcript): Boolean = t.list.offset() >= t.list.total - t.list.size.height() - 1.0

  "A transcript" should {
    "stay at the bottom as messages arrive and one streams, let go when scrolled up, and jump back" in {
      val entries = Var(talk(40))
      val t = fx(new Transcript(entries))
      fx {
        t.size.width @= 400.0
        t.size.height @= 500.0
      }
      onScreen(t) {
        settle(t)
        eventually(atBottom(t)) shouldBe true
        fx(t.pin.pinned()) shouldBe true
        val streaming = fx {
          val m = ChatEntry.message("s", ada, self = false, streaming = true)
          entries @= entries() :+ m
          m
        }
        settle(t)
        (1 to 30).foreach { i =>
          fx(streaming.text @= streaming.text() + "word " * 6 + "\n")
          settle(t)
          fx(atBottom(t)) shouldBe true
        }
        fx(streaming.streaming @= false)
        val tall = fx(t.list.heightAt(t.list.indexOf("s")))
        tall should be > 400.0
        // scrolled up: let go, and what arrives is counted, not scrolled to
        fx(t.list.scrollBy(-900.0))
        fx(t.pin.pinned()) shouldBe false
        val offset = fx(t.list.offset())
        fx(entries @= entries() ++ talk(3, 100))
        settle(t)
        fx(t.list.offset()) shouldBe offset
        fx(t.pin.unseen()) shouldBe 3
        fx(t.children().collectFirst { case b: Button if b.visible() => b.label() }) shouldBe Some("3 new")
        // the jump takes it back down, pinned
        fx(t.toLatest())
        settle(t)
        fx(atBottom(t)) shouldBe true
        fx(t.pin.pinned()) shouldBe true
        fx(t.children().exists { case b: Button => b.visible(); case _ => false }) shouldBe false
        // scrolling down to the bottom pins it again too
        fx(t.list.scrollBy(-300.0))
        fx(t.pin.pinned()) shouldBe false
        fx(t.list.scrollBy(10000.0))
        fx(t.pin.pinned()) shouldBe true
      }
    }

    "put earlier history before the rest without moving what shows" in {
      val entries = Var(talk(30, 1000))
      val left = Var(500)
      val older = () => Task.async[Vector[ChatEntry]] { done =>
        everyui.render.Platform.later(() => {
          val to = left()
          val from = math.max(0, to - 50)
          left @= from
          done(talk(to - from, from))
        })
      }
      val t = fx(new Transcript(entries, Some(older)))
      fx {
        t.dayMarkers @= false
        t.size.width @= 1000.0
        t.size.height @= 600.0
      }
      onScreen(t) {
        settle(t)
        fx(t.pin.pinned()) shouldBe true
        // up at the top: earlier entries are fetched and put before, and what showed at the top stays where it was
        var pages = 0
        while (!fx(t.exhausted()) && pages < 20) {
          val (key, y) = fx {
            t.list.scrollTop(0.0)
            val i = t.list.indexAt(t.list.offset() + 1.0)
            (t.list.keyAt(i), t.list.top(i) - t.list.offset())
          }
          eventually(!t.loading()) shouldBe true
          settle(t)
          if (!fx(t.exhausted())) {
            fx(t.list.top(t.list.indexOf(key)) - t.list.offset()) shouldBe y +- 0.5
            fx(t.list.indexOf(key)) should be >= 50
          }
          pages += 1
        }
        pages shouldBe 11
        fx(entries().size) shouldBe 530
        fx(entries().head.id) shouldBe "m0"
        fx(t.pin.pinned()) shouldBe false
      }
    }

    "keep the header on the entry that led its group when earlier entries by its author come in above it" in {
      val entries = Var[Vector[ChatEntry]]((0 until 30).toVector.map(i => ChatEntry.message("n" + i, ada, self = false, "Now " + i)))
      var answer: Option[Vector[ChatEntry] => Unit] = None
      val older = () => Task.async[Vector[ChatEntry]](done => answer = Some(done))
      val t = fx(new Transcript(entries, Some(older)))
      fx {
        t.dayMarkers @= false
        t.size.width @= 600.0
        t.size.height @= 300.0
      }
      onScreen(t) {
        settle(t)
        fx(t.list.scrollTop(0.0))
        settle(t)
        eventually(answer.nonEmpty) shouldBe true
        val (y, tall) = fx((t.list.top(0) - t.list.offset(), t.list.heightAt(0)))
        fx(t.leads(0, entries().head)) shouldBe true
        // a page by the same author, the last of it straight above what was first
        fx(answer.get((0 until 20).toVector.map(i => ChatEntry.message("o" + i, ada, self = false, "Then " + i))))
        settle(t)
        val first = fx(t.list.indexOf("n0"))
        first shouldBe 20
        fx(t.leads(first, entries()(first))) shouldBe true
        fx(t.leads(first + 1, entries()(first + 1))) shouldBe false
        fx(t.leads(0, entries().head)) shouldBe true
        fx(t.leads(19, entries()(19))) shouldBe false
        // what showed is drawn as it was, where it was
        fx(t.list.heightAt(first)) shouldBe tall
        fx(t.list.top(first) - t.list.offset()) shouldBe y +- 0.001
      }
    }

    "keep the app's own component, and what was done in it, while it is scrolled away" in {
      val entries = Var(talk(10))
      var made = 0
      val card = ChatEntry.custom("card", () => {
        made += 1
        new Button("Approve")
      })
      fx(entries @= entries() :+ card :+ ChatEntry.message("after", ada, self = false, "after"))
      val t = fx(new Transcript(entries))
      fx {
        t.size.width @= 600.0
        t.size.height @= 300.0
      }
      onScreen(t) {
        settle(t)
        val shown = fx(t.list.rows.flatMap(_.children()).collectFirst { case v: CustomView => v.component })
        shown.nonEmpty shouldBe true
        fx(shown.get.asInstanceOf[Button].label @= "Approved")
        // scrolled far away and back, with the rows handed other entries, and more entries arriving
        fx(entries @= talk(200, 500) ++ entries())
        settle(t)
        fx(t.list.scrollTop(0.0))
        settle(t)
        fx(t.list.rows.flatMap(_.children()).collectFirst { case v: CustomView => v }) shouldBe None
        fx(t.toLatest())
        settle(t)
        val again = fx(t.list.rows.flatMap(_.children()).collectFirst { case v: CustomView => v.component })
        again.get shouldBe theSameInstanceAs(shown.get)
        fx(again.get.asInstanceOf[Button].label()) shouldBe "Approved"
        made shouldBe 1
        // gone from the entries, it is taken apart
        fx(entries @= entries().filterNot(_.id == "card"))
        settle(t)
        made shouldBe 1
      }
    }

    "open a tool call with the keys, scroll through a tall message, and go into the app's component" in {
      val entries = Var(talk(3))
      val tool = ChatEntry.activity("tool", "Ran the tests", ActivityStatus.Failed,
        Some(ChatEntry.textDetails("sbt test", "1 failed")))
      val long = ChatEntry.message("long", ada, self = false, (1 to 60).map(i => "line " + i).mkString("\n"))
      val card = ChatEntry.custom("card", () => new Container {
        size.width @= 200.0
        size.height @= 40.0
        children += new Button("Approve")
      })
      fx(entries @= entries() :+ tool :+ long :+ card)
      val t = fx(new Transcript(entries))
      fx {
        t.dayMarkers @= false
        t.size.width @= 800.0
        t.size.height @= 400.0
      }
      onScreen(t) {
        settle(t)
        fx(t.focus())
        fx(t.list.scrollTop(0.0))
        settle(t)
        fx(t.list.current()) shouldBe Some("m0") // arriving, the cursor is on the first
        press(KeyCode.DOWN)
        press(KeyCode.DOWN)
        press(KeyCode.DOWN)
        fx(t.list.current()) shouldBe Some("tool")
        press(KeyCode.ENTER)
        fx(tool.expanded()) shouldBe true
        press(KeyCode.LEFT)
        fx(tool.expanded()) shouldBe false
        press(KeyCode.RIGHT)
        fx(tool.expanded()) shouldBe true
        settle(t)
        // into the tall message, which scrolls a line at a time before the cursor moves on
        press(KeyCode.DOWN)
        fx(t.list.current()) shouldBe Some("long")
        val before = fx(t.list.offset())
        press(KeyCode.DOWN)
        fx(t.list.current()) shouldBe Some("long")
        fx(t.list.offset()) should be > before
        (1 to 40).foreach(_ => press(KeyCode.DOWN))
        fx(t.list.current()) shouldBe Some("card")
        press(KeyCode.ENTER)
        fx(Focus.focused().map(_.getClass.getSimpleName)) shouldBe Some("Button")
        // up out of it goes back to the transcript
        press(KeyCode.UP)
        fx(Focus.focused().exists(_ eq t.list)) shouldBe true
        press(KeyCode.HOME)
        fx(t.list.offset()) shouldBe 0.0
        press(KeyCode.END)
        fx(atBottom(t)) shouldBe true
      }
    }
  }
}
