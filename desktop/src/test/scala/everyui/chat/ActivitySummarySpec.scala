package everyui.chat

import everyui.component.{Component, Container, TextView}
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.markdown.MarkdownLine
import everyui.text.TextRun
import everyui.tooltip.Tooltip
import everyui.{FxSpec, ui}
import reactify.*

/** A tool call's summary as inline Markdown on one line (`MarkdownLine`): its marks parsed into runs (code, bold,
  * slanted, a link as its words, a block's marks as the words they are), said without them, a summary that fits a pill
  * as wide as it is, and one too long cut to the line with an ellipsis, a code span cut short keeping its wash with the
  * ellipsis inside it, the card as wide as it may be, and its words shown whole in a tip, left to right and right to
  * left, at a desktop's width and a phone's. */
class ActivitySummarySpec extends FxSpec {
  override protected def keyed: Boolean = true

  private val long = "Ran `sed -i 's/^import everyui.old.{Thing, Other}/import everyui.{Thing, Other}/' " +
    "shared/src/main/scala/everyui/chat/*.scala` and 2 more"
  private val longWords = "Ran sed -i 's/^import everyui.old.{Thing, Other}/import everyui.{Thing, Other}/' " +
    "shared/src/main/scala/everyui/chat/*.scala and 2 more"

  private def shown[T](c: Component)(test: => T): T = {
    fx {
      Tooltip.delay @= 0.05
      Tooltip.onFocus @= true
      ui.children += c
    }
    (1 to 6).foreach(_ => fx(()))
    try test finally fx {
      Tooltip.hide()
      Tooltip.delay @= 0.6
      ui.children -= c
      c.dispose()
      Focus.focused @= None
    }
  }

  private def box(v: ActivityView): Container = v.children().head.asInstanceOf[Container]
  private def oneLine(): Double = {
    val t = fx(new TextView {
      content @= "X"
      font.size @= 13.0
    })
    val h = fx(t.size.height())
    fx(t.dispose())
    h
  }
  private def all(c: Component): Vector[Component] = c +: (c match {
    case k: Container => k.children().flatMap(all)
    case _ => Vector.empty
  })
  private def code(r: TextRun): Boolean = r.background.nonEmpty && r.family.toLowerCase.contains("mono")

  "A tool call's summary" should {
    "be parsed into runs: code, bold, slanted, a link as its words, and a block's marks as words" in {
      val runs = fx(MarkdownLine.runs("Ran `ls -la` and **two** *more*, see [the log](https://x.io/1)", 13.0))
      runs.map(_.text) shouldBe List("Ran ", "ls -la", " and ", "two", " ", "more", ", see ", "the log")
      code(runs(1)) shouldBe true
      runs(1).size shouldBe 13.0 * 0.88 +- 0.01
      runs(3).weight shouldBe 700
      runs(5).italic shouldBe true
      // a link is its words, drawn as the line's: no address, colour or underline
      runs(7) shouldBe TextRun("the log")
      runs(0) shouldBe TextRun("Ran ")
      fx(MarkdownLine.runs("# Heading", 13.0)).map(_.text).mkString shouldBe "# Heading"
      fx(MarkdownLine.runs("- an item", 13.0)).map(_.text).mkString shouldBe "- an item"
      fx(MarkdownLine.runs("one\ntwo", 13.0)).map(_.text).mkString shouldBe "one two"
      fx(MarkdownLine.runs("", 13.0)) shouldBe Nil
    }

    "be said and drawn without its marks" in {
      val a = ChatEntry.activity("t", "Read `src/A.scala`, **twice**", ActivityStatus.Succeeded, aside = "*0.2 s*")
      val t = fx(new Transcript(Var(Vector[ChatEntry](a))))
      fx(t.said(a)) shouldBe "Read src/A.scala, twice, 0.2 s, " + ChatWords.succeeded
      val v = fx(new ActivityView(a, 1000.0, () => None))
      shown(v) {
        fx(MarkdownLine.plain(v.summaryView.runs())) shouldBe "Read src/A.scala, twice"
        fx(MarkdownLine.plain(v.asideView.runs())) shouldBe "0.2 s"
        fx(v.asideView.runs()).head.italic shouldBe true
      }
      fx(t.dispose())
    }

    "cut a code span with the ellipsis inside it" in {
      val runs = fx(MarkdownLine.runs("Ran `sed -i x` and more", 13.0))
      // in the code
      val inCode = MarkdownLine.cut(runs, 7)
      inCode.map(_.text) shouldBe List("Ran ", "sed…")
      code(inCode.last) shouldBe true
      // spaces before the ellipsis go
      MarkdownLine.cut(runs, 8).map(_.text) shouldBe List("Ran ", "sed…")
      // right at the code's start, the ellipsis is the code's
      val atCode = MarkdownLine.cut(runs, 4)
      atCode.map(_.text) shouldBe List("Ran ", "…")
      code(atCode.last) shouldBe true
      // past it, the words after it
      val after = MarkdownLine.cut(runs, 16)
      after.map(_.text) shouldBe List("Ran ", "sed -i x", " and…")
      after.last.background shouldBe ""
      // whole, as they were
      MarkdownLine.cut(runs, 100) shouldBe runs
      // a letter and its pair are never parted
      MarkdownLine.cut(List(TextRun("a😀b")), 2).map(_.text) shouldBe List("a…")
    }

    for (width <- List(1000.0, 390.0); rtl <- List(false, true)) {
      s"be one line cut with an ellipsis inside the code, the card as wide as it may be ($width, rtl $rtl)" in {
        fx(Direction.set(rtl))
        try {
          val v = fx(new ActivityView(ChatEntry.activity("long", long, ActivityStatus.Succeeded), width, () => None))
          shown(v) {
            val widest = Transcript.widest(width)
            // (right to left the row of its line books no gap before its empty chevron, as left to right it does)
            fx(box(v).size.width()) should be > widest - 9.0
            fx(box(v).size.width()) should be <= widest + 0.5
            val s = v.summaryView
            fx(s.size.height()) shouldBe oneLine() +- 0.5
            val drawn = fx(s.runs())
            drawn.last.text should endWith("…")
            // the cut falls in the long code span, which keeps its wash, the ellipsis inside it
            code(drawn.last) shouldBe true
            drawn.map(_.text).mkString should not include "`"
            // as many letters as fit: drawn within its box, and one more would not be
            fx(s.node.getWidth()) should be <= fx(s.size.width()) + 0.5
            fx(s.size.width()) should be > widest * 0.6
          }
        } finally fx(Direction.set(false))
      }
    }

    "be a pill as wide as its summary where it fits, drawn whole" in {
      val v = fx(new ActivityView(ChatEntry.activity("short", "Ran `sbt test`", ActivityStatus.Succeeded), 1000.0,
        () => None))
      shown(v) {
        fx(box(v).size.width()) should be < 300.0
        fx(v.summaryView.size.height()) shouldBe oneLine() +- 0.5
        fx(MarkdownLine.plain(v.summaryView.runs())) shouldBe "Ran sbt test"
        // no tip where nothing is cut
        fx(v.summaryView.event.pointer.enter @= PointerEvent(PointerKind.Enter, 0.0, 0.0))
        Thread.sleep(300L)
        fx(Tooltip.shownFor) shouldBe None
      }
      // right-to-left words with code and bold in them, on one line too
      val ar = fx(new ActivityView(ChatEntry.activity("ar", "قرأت `src/A.scala` و**عدّلت** سطرًا واحدًا",
        ActivityStatus.Succeeded), 1000.0, () => None))
      shown(ar) {
        fx(ar.summaryView.size.height()) should be < oneLine() * 1.5
        fx(ar.summaryView.runs().last.text) should not endWith "…"
      }
    }

    "show its words whole in a tip where it is cut, as the pointer rests on it and as the keys bring the cursor" in {
      val a = ChatEntry.activity("long", long, ActivityStatus.Succeeded)
      val v = fx(new ActivityView(a, 600.0, () => None))
      shown(v) {
        fx(v.summaryView.runs().last.text) should endWith("…")
        fx(v.summaryView.event.pointer.enter @= PointerEvent(PointerKind.Enter, 0.0, 0.0))
        eventually(Tooltip.shownFor.exists(_ eq v.summaryView)) shouldBe true
        val tip = fx(ui.children().last)
        fx(all(tip).collect { case w: TextView => w.content().replace("\u200B", "") }) should contain(longWords)
        fx(v.summaryView.event.pointer.leave @= PointerEvent(PointerKind.Leave, 0.0, 0.0))
        fx(Tooltip.shownFor) shouldBe None
      }
      val t = fx(new Transcript(Var(Vector[ChatEntry](a))))
      fx {
        t.size.width @= 600.0
        t.size.height @= 400.0
      }
      shown(t) {
        (1 to 5).foreach(_ => fx(t.list.refresh()))
        fx(t.said(a)) shouldBe longWords + ", " + ChatWords.succeeded
        fx(t.focus())
        fx(t.list.current @= Some("long"))
        def summary: Option[Component] = all(t.list).collectFirst { case v: ActivityView => v.summaryView }
        eventually(summary.exists(x => Tooltip.shownFor.exists(_ eq x))) shouldBe true
        fx(t.list.current @= None)
        eventually(Tooltip.shownFor.isEmpty) shouldBe true
      }
    }
  }
}
