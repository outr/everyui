package everyui.chat

import everyui.component.Component
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import reactify.*

/** What a transcript brings into view lands clear of what it draws over its rows (`Transcript.coveredAt`): a highlight
  * the words under an asking message jump to, on a phone and on a desktop, below the turn header and inside the view,
  * centred in what is left where it fits, its top shown where it is taller, and the last as near as the rows go (the
  * transcript as tall as a phone leaves it under an app bar and over the composer, 360 by 390, and a short one on a
  * desktop);
  * and a message revealed (a search's hit, a link) with its top below the header. */
class RevealClearSpec extends FxSpec {
  private val me = Participant("u-me", "Me")
  private val helper = Participant("agent-helper", "Helper")
  private val ask = "Please write up the audit grouping."
  private val reply: String = "I grouped the failures from the audit. Here is how they fall out, with what each " +
    "needs before any of it can start.\n\nGroup 1 is the flaky reconnect tests. They need a retry wrapper and a " +
    "longer timeout. Shall I start with group 1? The rest can wait until it passes.\n\n" +
    "Group 2 needs a schema change. Should I email the team about the schema change first?\n\n" +
    "Group 3 fails on CI only, and only on the slow runners.\n\nThe grouped analysis is in ~/.cache/p6-audit/" +
    "MERGED.md, with one report per failure in p6-audit/, and the flaky ones listed with their last ten runs, so it " +
    "is worth a read before any of this starts in earnest: it will take most of the week, and some of it depends on " +
    "the schema change landing."
  private val questions = Vector("Shall I start with group 1?",
    "Should I email the team about the schema change first?").map(MessageHighlight(_, label = "Question for you"))

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

  // a conversation of earlier turns, then the person's ask and the reply asking two questions, `width` by `height`
  private def conversation(width: Double, height: Double): (Transcript, ChatEntry.Message) = {
    val earlier = (0 until 6).toVector.flatMap { i =>
      Vector[ChatEntry](ChatEntry.message("a" + i, me, self = true, "Earlier ask " + i),
        ChatEntry.message("r" + i, helper, self = false, "An earlier answer, " + i + ", long enough to wrap " +
          "onto a second line on a phone."))
    }
    val m = ChatEntry.message("q", helper, self = false, reply)
    fx {
      m.attention @= Some(Attention.asking)
      m.highlights @= questions
    }
    val t = fx {
      val made = new Transcript(Var(earlier ++ Vector[ChatEntry](ChatEntry.message("ask", me, self = true, ask), m)))
      made.dayMarkers @= false
      made.turnHeader @= Some((e: ChatEntry) => e match {
        case x: ChatEntry.Message if x.self => Some(TurnHeader.of(x))
        case _ => None
      })
      made.size.width @= width
      made.size.height @= height
      made
    }
    (t, m)
  }
  private def view(t: Transcript): MessageView =
    t.list.rows.find(_.item().exists(_.id == "q")).collect { case r: ChatRow => r }.flatMap(_.message).get

  // where the highlight `n` (of `revealed`) lies from the transcript's top, and the header's foot, where it shows
  private def placed(t: Transcript, c: Component): (Double, Double, Double) = fx {
    val top = c.absoluteTop - t.absoluteTop
    (top, top + c.size.height(), if (t.header.visible()) t.header.absoluteTop - t.absoluteTop + t.header.size.height()
      else 0.0)
  }

  private def jumps(width: Double, height: Double): Unit = {
    val (t, _) = conversation(width, height)
    onScreen(t) {
      settle(t)
      eventually(t.list.rows.exists(_.item().exists(_.id == "q")) && view(t).jumper.nonEmpty) shouldBe true
      // each jump: the line holding the highlight below the header, where it shows, and inside the view
      (0 until 2).foreach { k =>
        fx(view(t).jump())
        settle(t)
        val unit = fx(view(t).bubble.childComponents.collectFirst { case w: MessageMarkdown => w.view.passageAt(k) }.flatten.get)
        val (top, bottom, covered) = placed(t, unit)
        withClue(s"highlight $k at ${width}x$height: top $top, bottom $bottom, header's foot $covered: ") {
          top should be >= covered + 1.0
          bottom should be <= fx(t.list.size.height())
        }
      }
    }
  }

  "A highlight brought into view" should {
    "land below the turn header and inside the view on a phone" in jumps(390.0, 360.0)
    "land below the turn header and inside the view on a desktop" in jumps(900.0, 220.0)
    "show its top, just below the turn header, where it is taller than what the header leaves" in {
      val (t, m) = conversation(390.0, 200.0)
      fx(m.highlights @= Vector(MessageHighlight("The grouped analysis is in"), questions(0)))
      onScreen(t) {
        settle(t)
        eventually(t.list.rows.exists(_.item().exists(_.id == "q")) && view(t).jumper.nonEmpty) shouldBe true
        // to the long last paragraph, from the bottom; then to the question and round to it again, from above
        List(1, 2).foreach { n =>
          (1 to n).foreach(_ => fx(view(t).jump()))
          settle(t)
          val unit = fx(view(t).bubble.childComponents.collectFirst { case w: MessageMarkdown => w.view.passageAt(0) }
            .flatten.get)
          val (top, _, covered) = placed(t, unit)
          fx(unit.size.height()) should be > fx(t.list.size.height()) - covered - JumpButton.diameter
          top should be >= covered + 1.0
          top should be <= covered + Transcript.revealRoom + 1.0
        }
      }
    }
  }

  "A message revealed" should {
    "have its top below the turn header" in {
      val (t, _) = conversation(390.0, 360.0)
      onScreen(t) {
        settle(t)
        fx(t.reveal("r3"))
        settle(t)
        val row = fx(t.list.rows.find(_.item().exists(_.id == "r3")).get)
        val top = fx(row.absoluteTop - t.absoluteTop)
        top should be >= placed(t, row)._3
      }
    }
  }
}
