package everyui.chat

import everyui.component.Component
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import reactify.*

/** A long transcript opens by making only what shows: the rows near its newest entry, each message's words made once
  * for the entry it shows, none for an entry until it is scrolled near, the rest of the scroll guessed from each entry's
  * length (`Transcript.estimate`), and what shows kept where it is as the guesses above it are found out. */
class TranscriptLazySpec extends FxSpec {
  private val ada = Participant("u-ada", "Ada")
  private val me = Participant("u-me", "Me")

  // an agent's long answer, a short reply, a tool call with a body, in turn
  private def history(n: Int): Vector[ChatEntry] = (0 until n).toVector.map { i =>
    i % 3 match {
      case 0 => ChatEntry.message("e" + i, ada, self = false, "## Step " + i + "\n\nThe socket drops after a minute " +
        "idle, so it now sends a heartbeat every 25 seconds and waits longer each time it reconnects.\n\n```scala\n" +
        "val delay = math.min(max, base * math.pow(2.0, attempt))\nsend(Ping)\n```\n\n| Run | Drops |\n|---|---|\n" +
        "| before | 14 |\n| after | 0 |\n\nI ran the tests again and they pass.")
      case 1 => ChatEntry.message("e" + i, me, self = true, "Thanks, go ahead " + i + ".")
      case _ => ChatEntry.activity("e" + i, "Ran a command", ActivityStatus.Succeeded, None, None,
        ActivityLead.None, "`sbt test` in `core`, " + i)
    }
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

  private def settle(t: Transcript): Unit = (1 to 5).foreach(_ => fx(t.list.refresh()))

  // a transcript over `entries` whose messages' words are counted as they are made, by the text they are made for
  private def counted(entries: Var[Vector[ChatEntry]]): (Transcript, scala.collection.mutable.Map[String, Int]) = {
    val made = scala.collection.mutable.Map.empty[String, Int]
    val t = fx(new Transcript(entries, render = (text: Val[String], widest: Val[Double]) => {
      made(text()) = made.getOrElse(text(), 0) + 1
      MessageMarkdown.render(text, widest)
    }))
    fx {
      t.size.width @= 900.0
      t.size.height @= 700.0
    }
    (t, made)
  }

  private def firstShowing(t: Transcript): (String, Double) = fx {
    val i = t.list.indexAt(t.list.offset())
    (t.list.keyAt(i), t.list.top(i) - t.list.offset())
  }

  "A long transcript" should {
    "make rows only near what shows, and a message's words once for the entry it shows" in {
      val entries = Var(history(500))
      val (t, made) = counted(entries)
      onScreen(t) {
        settle(t)
        fx(t.list.offset() >= t.list.total - t.list.size.height() - 1.0) shouldBe true
        val rows = fx(t.list.rows.filter(r => r.visible() && r.item().nonEmpty))
        rows.size should be < 16
        fx(rows.flatMap(_.item().toList).map(e => t.list.indexOf(e.id)).min) should be > 470
        // no entry far up has a row, or had its words made
        fx((0 until 400).exists(i => t.list.rowAt(i).nonEmpty)) shouldBe false
        made.keys.exists(_.contains("Step 3\n")) shouldBe false
        made.keys.exists(_.contains("Step 99\n")) shouldBe false
        // scrolled up a page at a time and back down, the rows handed the entries arriving: on the way down, over rows
        // drawn before, each message's and tool call's words made once, not once for the place its row had and again for
        // its own (a tool call measures a line of its body by an "X")
        (1 to 6).foreach { _ =>
          fx(t.list.scrollBy(-700.0))
          settle(t)
        }
        made.clear()
        (1 to 5).foreach { _ =>
          fx(t.list.scrollBy(700.0))
          settle(t)
        }
        made.size should be > 6
        made.filter(m => m._1 != "X" && m._2 > 1).keys.map(k => k.takeRight(12)) shouldBe empty
        // scrolled up to the top, the first entries are made there
        fx(t.list.scrollTop(0.0))
        settle(t)
        made.keys.exists(_.startsWith("## Step 0\n")) shouldBe true
        fx(t.list.rowAt(0).flatMap(_.item()).map(_.id)) shouldBe Some("e0")
      }
    }

    "guess the rows not drawn near what they draw, so the scroll is about as long as it will be" in {
      val entries = Var(history(60))
      val (t, _) = counted(entries)
      onScreen(t) {
        settle(t)
        val guessed = fx(t.list.total)
        // every row drawn once, from the top down
        var at = 0.0
        while (fx(t.list.offset()) > 0.0 || at == 0.0) {
          fx(t.list.scrollTop(at))
          settle(t)
          at += 500.0
          if (at > 100000.0) fail("never reached the end")
          if (fx(t.list.offset() >= t.list.total - t.list.size.height() - 1.0)) at = -1.0
        }
        val drawn = fx(t.list.total)
        guessed / drawn should (be > 0.75 and be < 1.35)
        // and each kind's guess near its row's height
        (0 until 3).foreach { k =>
          val (h, e) = fx((t.list.heightAt(t.list.indexOf("e" + (30 + k))), entries()(30 + k)))
          Transcript.estimate(e, 900.0, 3) / h should (be > 0.6 and be < 1.6)
        }
      }
    }

    "keep what shows where it is as the rows above it are drawn at heights other than were guessed" in {
      val entries = Var(history(300))
      val (t, _) = counted(entries)
      onScreen(t) {
        settle(t)
        // somewhere in the middle, then up a little at a time, each step drawing rows above for the first time
        fx(t.list.scrollTop(t.list.top(150)))
        settle(t)
        var refined = 0
        (1 to 12).foreach { _ =>
          val (key, y) = firstShowing(t)
          val total = fx(t.list.total)
          fx(t.list.scrollBy(-250.0))
          settle(t)
          if (fx(t.list.total) != total) refined += 1
          fx(t.list.top(t.list.indexOf(key)) - t.list.offset()) shouldBe (y + 250.0) +- 0.5
        }
        refined should be > 3
        fx(t.pin.pinned()) shouldBe false
      }
    }

    "keep each row on its entry, its words as they were made, as earlier pages come in above" in {
      val entries = Var(history(4))
      var answer: Option[Vector[ChatEntry] => Unit] = None
      val made = scala.collection.mutable.Map.empty[String, Int]
      val older = () => everyui.async.Task.async[Vector[ChatEntry]](done => answer = Some(done))
      val t = fx(new Transcript(entries, Some(older), (text: Val[String], widest: Val[Double]) => {
        made(text()) = made.getOrElse(text(), 0) + 1
        MessageMarkdown.render(text, widest)
      }))
      fx {
        t.size.width @= 900.0
        t.size.height @= 700.0
      }
      onScreen(t) {
        settle(t)
        // what each entry shows, and the component it is drawn in
        def shown(): Map[String, Component] = fx(t.list.rows.filter(r => r.visible() && r.item().nonEmpty)
          .map(r => r.item().get.id -> r.children().head).toMap)
        val before = shown()
        before.keySet shouldBe Set("e0", "e1", "e2", "e3")
        made.clear()
        // three pages put before them while they show: a drag selecting a reply's words held its words' nodes, and the
        // rows handed the entries at their places made them again under it
        (1 to 3).foreach { p =>
          eventually(answer.nonEmpty) shouldBe true
          val reply = answer.get
          answer = None
          fx(reply((0 until 3).toVector.map(i => ChatEntry.message("o" + p + "-" + i, me, self = true, "Earlier " + p))))
          settle(t)
        }
        val after = shown()
        made.keys.filter(_.startsWith("## Step")).map(_.take(10)) shouldBe empty
        before.filter { case (id, view) => !after.get(id).exists(_ eq view) }.keySet shouldBe empty
      }
    }
  }
}
