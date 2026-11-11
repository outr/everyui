package everyui.chat

import everyui.component.{Component, Container, TextView}
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.theme.Theme
import everyui.tooltip.Tooltip
import everyui.{FxSpec, ui}
import reactify.*

/** A tool call's aside (`ChatEntry.Activity.aside`), on the desktop's real nodes: an empty one takes no room and leaves
  * the line as it was; one that fits sits after the summary on its line, muted, smaller and its feet level with the
  * summary's; where the line has no room it goes under the summary at its start edge, wrapped where even it alone is
  * too long, never cut and never a word to a line; it follows its entry in place, the view and the transcript's other
  * rows left as they were; its hint is a tip as the pointer rests on it and as the keys bring the cursor to the call,
  * and is read out after the summary and the aside; and right to left it follows the summary leftwards. */
class ActivityAsideSpec extends FxSpec {
  // the transcript's cursor as the keys move it, for its tip
  override protected def keyed: Boolean = true

  private val when = "9:42:13 AM · 1.2 s"
  private val hint = "Started at 9:42:13 AM, took 1.2 seconds"

  private def shown[T](c: Component)(test: => T): T = {
    fx {
      Tooltip.delay @= 0.05
      Tooltip.onFocus @= true
      ui.children += c
    }
    (1 to 4).foreach(_ => fx(()))
    try test finally fx {
      Tooltip.hide()
      Tooltip.delay @= 0.6
      ui.children -= c
      c.dispose()
      Focus.focused @= None
    }
  }

  private def view(a: ChatEntry.Activity, width: Double = 1000.0): ActivityView =
    fx(new ActivityView(a, width, () => None))

  private def box(v: ActivityView): Container = v.children().head.asInstanceOf[Container]
  private def line(v: ActivityView): Container = box(v).children().head.asInstanceOf[Container]
  private def words(v: ActivityView): Container = line(v).children()(1).asInstanceOf[Container]
  // a part's left and top in the box
  private def inBox(v: ActivityView, c: Component): (Double, Double) = (line(v).position.left() +
    words(v).position.left() + c.position.left(), line(v).position.top() + words(v).position.top() + c.position.top())
  private def oneLine(points: Double): Double = {
    val t = fx(new TextView {
      content @= "X"
      font.size @= points
    })
    val h = fx(t.size.height())
    fx(t.dispose())
    h
  }
  // every part's place and size, from the box down
  private def layout(c: Component): Vector[(Double, Double, Double, Double)] =
    (c.position.left(), c.position.top(), c.size.width(), c.size.height()) +: (c match {
      case k: Container => k.children().filter(_.visible()).flatMap(layout)
      case _ => Vector.empty
    })

  "A tool call's aside" should {
    "take no room where it is empty, the line as it was" in {
      val bare = view(ChatEntry.activity("bare", "Ran the tests", ActivityStatus.Succeeded))
      val a = ChatEntry.activity("cleared", "Ran the tests", ActivityStatus.Succeeded, aside = when)
      val v = view(a)
      shown(bare) {
        shown(v) {
          fx(v.asideBeside) shouldBe true
          fx(a.aside @= "")
          (1 to 3).foreach(_ => fx(()))
          fx(v.asideView.visible()) shouldBe false
          fx(words(v).size.width()) shouldBe fx(v.summaryView.size.width())
          fx(words(v).size.height()) shouldBe fx(v.summaryView.size.height())
          fx(layout(box(v))) shouldBe fx(layout(box(bare)))
          fx(v.size.height()) shouldBe fx(bare.size.height())
        }
      }
    }

    "sit after the summary on its line where there is room, muted, smaller and its feet level" in {
      val bare = view(ChatEntry.activity("bare", "Ran the tests", ActivityStatus.Succeeded))
      val v = view(ChatEntry.activity("t", "Ran the tests", ActivityStatus.Succeeded, aside = when))
      shown(bare) {
        shown(v) {
          fx(v.asideBeside) shouldBe true
          val s = v.summaryView
          val aside = v.asideView
          fx(aside.content()) shouldBe when
          fx(aside.color()) shouldBe fx(Theme.muted())
          fx(aside.font.size()) should be < fx(s.font.size())
          fx(aside.elided()) shouldBe false
          val (sl, st) = fx(inBox(v, s))
          val (al, at) = fx(inBox(v, aside))
          al shouldBe (sl + fx(s.size.width()) + ActivityView.asideGap +- 0.5)
          (at + fx(aside.size.height())) shouldBe (st + fx(s.size.height()) +- 0.5)
          // one line still, the box as tall as the bare one's and wider by the aside
          fx(s.size.height()) shouldBe oneLine(13.0) +- 0.5
          fx(box(v).size.height()) shouldBe fx(box(bare).size.height())
          fx(box(v).size.width()) shouldBe
            (fx(box(bare).size.width()) + ActivityView.asideGap + fx(aside.size.width()) +- 0.5)
        }
      }
    }

    "go under the summary at its start edge where the line is narrow, wrapped and never cut" in {
      val a = ChatEntry.activity("narrow", "Searched the repository for open TODOs in every module",
        ActivityStatus.Succeeded, aside = when)
      val v = view(a, 300.0)
      shown(v) {
        fx(v.asideBeside) shouldBe false
        val s = v.summaryView
        val aside = v.asideView
        val (sl, st) = fx(inBox(v, s))
        val (al, at) = fx(inBox(v, aside))
        al shouldBe (sl +- 0.5)
        at should be >= st + fx(s.size.height())
        fx(aside.size.height()) shouldBe oneLine(ActivityView.asideSize) +- 0.5
        fx(aside.elided()) shouldBe false
        // inside the box, which is as tall as the summary and the aside need
        (al + fx(aside.size.width())) should be <= fx(box(v).size.width()) - Transcript.padX + 0.5
        (at + fx(aside.size.height())) should be <= fx(box(v).size.height()) - 5.5
        // an aside too long for the line alone wraps within it, at its words
        val long = "9:42:13 AM · 1.2 s · 2,431 input tokens · 318 output tokens · $0.0124 · cache hit 87%"
        fx(a.aside @= long)
        (1 to 3).foreach(_ => fx(()))
        val whole = fx(new TextView {
          content @= long
          font.size @= ActivityView.asideSize
          textOverflow @= everyui.component.types.TextOverflow.Visible
        })
        val wanted = math.ceil(fx(whole.size.width()) / fx(aside.size.width()))
        fx(whole.dispose())
        fx(aside.elided()) shouldBe false
        val lines = fx(aside.size.height()) / oneLine(ActivityView.asideSize)
        lines should be > 1.5
        lines should be <= wanted + 1.5
        val (al2, at2) = fx(inBox(v, aside))
        (al2 + fx(aside.size.width())) should be <= fx(box(v).size.width()) - Transcript.padX + 0.5
        (at2 + fx(aside.size.height())) should be <= fx(box(v).size.height()) - 5.5
      }
    }

    "follow its entry in place, the view and the transcript's other rows as they were" in {
      val ada = Participant("u-ada", "Ada")
      val call = ChatEntry.activity("call", "Ran the tests", aside = "9:42:13 AM · 0 s")
      val entries: Var[Vector[ChatEntry]] = Var((0 until 12).toVector.map(i =>
        ChatEntry.message("m" + i, ada, self = false, "Message " + i)) :+ call)
      val t = fx(new Transcript(entries))
      fx {
        t.dayMarkers @= false
        t.size.width @= 700.0
        t.size.height @= 900.0
      }
      shown(t) {
        (1 to 5).foreach(_ => fx(t.list.refresh()))
        def rows: Vector[ChatRow] = all(t.list).collect { case r: ChatRow if r.item().nonEmpty => r }
        def ofCall: ActivityView = rows.flatMap(_.shown).collectFirst { case v: ActivityView => v }.get
        val view = fx(ofCall)
        val before = fx(rows.map(r => (r.item().get.id, r.shown.get, r.position.top(), r.size.height())))
        before.size should be > 5
        (1 to 5).foreach { s =>
          fx(call.aside @= "9:42:13 AM · " + s + " s")
          (1 to 2).foreach(_ => fx(()))
          fx(view.asideView.content()) shouldBe "9:42:13 AM · " + s + " s"
          fx(ofCall eq view) shouldBe true
          val after = fx(rows.map(r => (r.item().get.id, r.shown.get, r.position.top(), r.size.height())))
          after.map(_._1) shouldBe before.map(_._1)
          after.zip(before).foreach { case (n, o) =>
            (n._2 eq o._2) shouldBe true
            n._3 shouldBe o._3
            n._4 shouldBe o._4
          }
        }
      }
    }

    "show its hint as a tip as the pointer rests on it, and say it after the summary and the aside" in {
      val a = ChatEntry.activity("t", "Ran the tests", ActivityStatus.Succeeded, aside = when, asideHint = hint)
      val v = view(a)
      shown(v) {
        fx(v.asideView.event.pointer.enter @= PointerEvent(PointerKind.Enter, 0.0, 0.0))
        eventually(Tooltip.shownFor.exists(_ eq v.asideView)) shouldBe true
        val tip = fx(ui.children().last)
        fx(all(tip).collect { case w: TextView => w.content() }) should contain(hint)
        fx(v.asideView.event.pointer.leave @= PointerEvent(PointerKind.Leave, 0.0, 0.0))
        fx(Tooltip.shownFor) shouldBe None
        // none where there is no hint
        fx(a.asideHint @= "")
        fx(v.asideView.event.pointer.enter @= PointerEvent(PointerKind.Enter, 0.0, 0.0))
        Thread.sleep(300L)
        fx(Tooltip.shownFor) shouldBe None
      }
      val t = fx(new Transcript(Var(Vector[ChatEntry](a))))
      fx {
        a.asideHint @= hint
        t.size.width @= 700.0
        t.size.height @= 400.0
      }
      shown(t) {
        (1 to 5).foreach(_ => fx(t.list.refresh()))
        val said = "Ran the tests, " + when + ", " + hint + ", " + ChatWords.succeeded
        fx(t.said(a)) shouldBe said
        eventually(all(t.list).exists(c => c.isInstanceOf[ChatRow] && c.semantics().label == said)) shouldBe true
        // with a body, the body after them
        fx(a.body @= "Hello")
        fx(t.said(a)) shouldBe "Ran the tests, " + when + ", " + hint + ": Hello, " + ChatWords.succeeded
        fx(a.body @= "")
        // the keys bringing the cursor to it show the tip
        fx(t.focus())
        fx(t.list.current @= Some("t"))
        def aside: Option[TextView] = all(t.list).collectFirst { case v: ActivityView => v.asideView }
        eventually(aside.exists(x => Tooltip.shownFor.exists(_ eq x))) shouldBe true
        fx(t.list.current @= None)
        eventually(Tooltip.shownFor.isEmpty) shouldBe true
      }
    }

    "right to left, follow the summary leftwards, and go under it at its right edge" in {
      fx(Direction.set(true))
      try {
        val v = view(ChatEntry.activity("t", "Ran the tests", ActivityStatus.Succeeded, aside = when))
        shown(v) {
          fx(v.asideBeside) shouldBe true
          val (sl, _) = fx(inBox(v, v.summaryView))
          val (al, _) = fx(inBox(v, v.asideView))
          (al + fx(v.asideView.size.width()) + ActivityView.asideGap) shouldBe (sl +- 0.5)
        }
        val n = view(ChatEntry.activity("n", "Searched the repository for open TODOs in every module",
          ActivityStatus.Succeeded, aside = when), 300.0)
        shown(n) {
          fx(n.asideBeside) shouldBe false
          val (sl, st) = fx(inBox(n, n.summaryView))
          val (al, at) = fx(inBox(n, n.asideView))
          (al + fx(n.asideView.size.width())) shouldBe (sl + fx(n.summaryView.size.width()) +- 0.5)
          at should be >= st + fx(n.summaryView.size.height())
        }
      } finally fx(Direction.set(false))
    }
  }

  private def all(c: Component): Vector[Component] = c +: (c match {
    case k: Container => k.children().flatMap(all)
    case _ => Vector.empty
  })
}
