package everyui.chat

import everyui.component.{Component, Container, RichText, TextView}
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.markdown.MarkdownLine
import everyui.tooltip.Tooltip
import everyui.{FxSpec, ui}

/** A tool call's card whose summary does not fit on a line (words wrap by default): as wide as the transcript lets it
  * be, its summary wrapped within it, as few lines as the words need and no room left under them, at a desktop's width
  * and a phone's, left to right and right to left; its inline Markdown drawn whole as it wraps (code on its wash, bold),
  * with no ellipsis and no tip; a summary that fits still a pill as wide as it is, on one line. */
class ActivityWrapSpec extends FxSpec {
  private val long = "update_page · pageId: f8lV, html: <main class=\"wrap\"><h1>Vellora</h1><p class=\"tag\">A Late " +
    "Cretaceous armored herbivore that browsed the floodplains, css: body{font-family:system-ui,sans-serif;" +
    "background:#101010} and more of the same for a while longer, " * 2
  private val marked = "**Ran** `sed -i 's/^import everyui.old.{Thing, Other}/import everyui.{Thing, Other}/' " +
    "shared/src/main/scala/everyui/chat/*.scala` and *2 more*, then `sbt desktop/testFull` with a fresh cache"

  private def shown[T](c: Component)(test: => T): T = {
    fx(ui.children += c)
    (1 to 6).foreach(_ => fx(()))
    try test finally fx {
      ui.children -= c
      c.dispose()
      Focus.focused @= None
    }
  }

  private def box(v: ActivityView): Container = v.children().head.asInstanceOf[Container]
  private def summary(v: ActivityView): RichText = v.summaryView
  private def oneLine(): Double = {
    val t = fx(new TextView {
      content @= "X"
      font.size @= 13.0
    })
    val h = fx(t.size.height())
    fx(t.dispose())
    h
  }
  // how many lines `words` take at `width`, drawn as the summary is
  private def wanted(words: String, width: Double): Double = {
    val whole = fx(new RichText {
      whiteSpace @= everyui.component.types.WhiteSpace.NoWrap
      font.size @= 13.0
      runs @= MarkdownLine.runs(words, 13.0)
    })
    fx(ui.children += whole)
    fx(())
    val w = fx(whole.size.width())
    fx {
      ui.children -= whole
      whole.dispose()
    }
    math.ceil(w / width)
  }

  "A tool call's card" should {
    for (width <- List(1000.0, 390.0); (words, rtl) <- List((long, false), (marked, false), (marked, true))) {
      s"be as wide as it may be with a summary too long for a line, wrapped in as few lines as it needs ($width, " +
        s"${if (words eq long) "plain" else "Markdown"}, rtl $rtl)" in {
        fx(Direction.set(rtl))
        try {
          val v = fx(new ActivityView(ChatEntry.activity("long", words, ActivityStatus.Succeeded), width, () => None))
          shown(v) {
            val widest = Transcript.widest(width)
            // (right to left the row of its line books no gap before its empty chevron, as left to right it does)
            fx(box(v).size.width()) should be > widest - 9.0
            fx(box(v).size.width()) should be <= widest + 0.5
            val s = summary(v)
            // drawn whole: its runs as they were, no ellipsis
            fx(s.runs()) shouldBe fx(MarkdownLine.runs(words, 13.0))
            fx(s.runs()).exists(_.text.endsWith("…")) shouldBe false
            fx(s.size.width()) should be > widest * 0.6
            val lines = fx(s.size.height()) / oneLine()
            lines should be > 1.5
            // its words wrapped at the room, not a word to a line
            lines should be <= wanted(words, fx(s.size.width())) + 1.5
            // no room left under them
            fx(box(v).size.height()) should be <= fx(s.size.height()) + 30.0
            // no tip: nothing is cut
            fx(s.event.pointer.enter @= PointerEvent(PointerKind.Enter, 0.0, 0.0))
            Thread.sleep(300L)
            fx(Tooltip.shownFor) shouldBe None
          }
        } finally fx(Direction.set(false))
      }
    }
    "be a pill as wide as its summary where it fits" in {
      val v = fx(new ActivityView(ChatEntry.activity("short", "Ran the tests", ActivityStatus.Succeeded), 1000.0, () => None))
      shown(v) {
        fx(box(v).size.width()) should be < 300.0
        fx(summary(v).size.height()) shouldBe oneLine() +- 0.5
      }
    }
    "wrap in a transcript unless it says to cut to one line" in {
      val a = ChatEntry.activity("long", marked, ActivityStatus.Succeeded)
      val t = fx(new Transcript(reactify.Var(Vector[ChatEntry](a))))
      fx {
        t.size.width @= 600.0
        t.size.height @= 400.0
      }
      shown(t) {
        val one = oneLine()
        (1 to 5).foreach(_ => fx(t.list.refresh()))
        def view: Option[ActivityView] = all(t.list).collectFirst { case v: ActivityView => v }
        eventually(view.exists(v => v.summaryView.size.height() > one * 1.5)) shouldBe true
        fx(t.oneLineSummaries @= true)
        (1 to 5).foreach(_ => fx(t.list.refresh()))
        eventually(view.exists(v => v.summaryView.runs().last.text.endsWith("…") &&
          v.summaryView.size.height() < one * 1.5)) shouldBe true
        fx(t.oneLineSummaries @= false)
        (1 to 5).foreach(_ => fx(t.list.refresh()))
        eventually(view.exists(v => !v.summaryView.runs().exists(_.text.endsWith("…")) &&
          v.summaryView.size.height() > one * 1.5)) shouldBe true
      }
    }
  }

  private def all(c: Component): Vector[Component] = c +: (c match {
    case k: Container => k.children().flatMap(all)
    case _ => Vector.empty
  })
}
