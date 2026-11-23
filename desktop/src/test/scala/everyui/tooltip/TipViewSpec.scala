package everyui.tooltip

import everyui.component.*
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.render.Nodes
import everyui.theme.Tone
import everyui.{FxSpec, ui}

/** A component's own tip (`Component.tipView`) in the one tip host: made as it opens (after the pointer rests, as the
  * keys bring the focus, at once as a finger is held) and taken apart as it closes (the pointer leaving, Escape, a
  * scroll, its component taken apart), nothing of it alive in between; placed on screen at every edge, under or over,
  * no wider than `Tooltip.maxWidth`; an interactive one kept as the pointer moves into it and closed by its own
  * `TipContext`; and its component's `tooltip` what a screen reader hears, with no platform tip beside it. */
class TipViewSpec extends FxSpec {
  // the focus as the keys move it, which some of these are about
  override protected def keyed: Boolean = true

  private def card(left: Double, top: Double, width: Double = 100.0): Component = {
    val c = new Component(Nodes.container())
    c.focusable @= true
    c.size.width @= width
    c.size.height @= 40.0
    c.position.left @= left
    c.position.top @= top
    c
  }

  // the views made for tips, newest first, and how many
  private var made: List[Component] = Nil

  private def tipped(c: Component, width: Double = 120.0, interactive: Boolean = false): Component = {
    c.tooltip @= "Sigil: 5 unread"
    c.tipInteractive @= interactive
    c.tipView @= Some { _ =>
      val view = new TextView {
        content @= "Sigil"
        size.width @= width
        size.height @= 30.0
      }
      made = view :: made
      view
    }
    c
  }

  private def onScreen[T](cards: Component*)(test: => T): T = {
    fx {
      made = Nil
      Tooltip.delay @= 0.05
      Tooltip.grace @= 0.1
      Tooltip.onFocus @= true
      Tooltip.hide()
      ui.children.clear()
      Focus.focused @= None
      ui.children ++= cards
    }
    try test finally fx {
      Tooltip.hide()
      Tooltip.delay @= 0.6
      Tooltip.grace @= 0.35
      Tooltip.onFocus @= true
      ui.children.clear()
      Focus.focused @= None
    }
  }

  private def enter(c: Component, finger: Boolean = false): Unit =
    c.event.pointer.enter @= PointerEvent(PointerKind.Enter, c.absoluteLeft + 5.0, c.absoluteTop + 5.0, finger = finger)
  private def leave(c: Component): Unit = c.event.pointer.leave @= PointerEvent(PointerKind.Leave, 0.0, 0.0)
  private def shown(c: Component): Boolean = Tooltip.shownFor.exists(_ eq c)
  private def plate: Container = Tooltip.plate.get

  "A tip view" should {
    "be made only as it opens, after the pointer rests, and taken apart as it closes" in {
      val a = fx(tipped(card(200.0, 100.0)))
      onScreen(a) {
        val before = fx(Component.liveNow)
        fx(enter(a))
        fx(made) shouldBe Nil
        eventually(shown(a)) shouldBe true
        fx(made.size) shouldBe 1
        fx(Component.liveNow) should be > before
        val view = fx(made.head)
        val tip = fx(plate)
        fx(leave(a))
        fx(Tooltip.shownFor) shouldBe None
        fx(view.takenApart && tip.takenApart) shouldBe true
        fx(Component.liveNow) shouldBe before
        // and made afresh each time it opens
        fx(enter(a))
        eventually(shown(a)) shouldBe true
        fx(made.size) shouldBe 2
      }
    }
    "leave nothing undisposed as it opens and closes" in {
      val a = fx(tipped(card(200.0, 100.0)))
      onScreen(a) {
        var warned = List.empty[String]
        val forwarded = fx(everyui.scribe.forward)
        val before = fx {
          everyui.scribe.forward = (level, message) => {
            if (level == "warn" && message.startsWith("undisposed:")) warned = message :: warned
            forwarded(level, message)
          }
          Component.warnUndisposed @= true
          Component.liveNow
        }
        try {
          (1 to 5).foreach { _ =>
            fx(enter(a))
            eventually(shown(a)) shouldBe true
            fx(leave(a))
          }
          fx(())
          fx(())
          fx(Component.liveNow) shouldBe before
          fx(made.forall(_.takenApart)) shouldBe true
          fx(made.size) shouldBe 5
        } finally fx {
          Component.warnUndisposed @= false
          everyui.scribe.forward = forwarded
        }
        warned shouldBe Nil
      }
    }
    "open as the keys bring the focus, keeping the focus where it is" in {
      val (a, b) = fx((tipped(card(100.0, 100.0)), card(300.0, 100.0)))
      onScreen(a, b) {
        fx(a.focus())
        eventually(shown(a)) shouldBe true
        fx(Focus.focused().exists(_ eq a)) shouldBe true
        fx(b.focus())
        fx(Tooltip.shownFor) shouldBe None
      }
    }
    "close on Escape" in {
      val a = fx(tipped(card(100.0, 100.0)))
      onScreen(a) {
        fx(enter(a))
        eventually(shown(a)) shouldBe true
        fx(Focus.key("Escape", true))
        fx(Focus.key("Escape", false))
        fx(Tooltip.shownFor) shouldBe None
        fx(made.head.takenApart) shouldBe true
      }
    }
    "open as a finger is held, and never for a tap" in {
      val a = fx(tipped(card(100.0, 100.0)))
      onScreen(a) {
        // a finger coming onto it, as a tap does, raises nothing
        fx(enter(a, finger = true))
        Thread.sleep(200L)
        fx(Tooltip.shownFor) shouldBe None
        fx(Tooltip.pendingFor) shouldBe None
        fx(a.event.pointer.menu @= PointerEvent(PointerKind.Menu, 110.0, 110.0, finger = true))
        eventually(shown(a)) shouldBe true
        // the finger lifted (which leaves it, on the web) keeps it
        fx(a.event.pointer.leave @= PointerEvent(PointerKind.Leave, 0.0, 0.0, finger = true))
        fx(shown(a)) shouldBe true
      }
    }
    "not open for a held finger that put a menu up" in {
      val a = fx(tipped(card(100.0, 100.0)))
      onScreen(a) {
        fx(a.event.pointer.menu.attach(e => e.consume()))
        fx(a.event.pointer.menu @= PointerEvent(PointerKind.Menu, 110.0, 110.0, finger = true))
        Thread.sleep(200L)
        fx(Tooltip.shownFor) shouldBe None
        fx(made) shouldBe Nil
      }
    }
    "close as what it is in scrolls" in {
      val a = fx(tipped(card(10.0, 10.0)))
      val scroll = fx {
        val s = new ScrollContainer
        s.size.width @= 400.0
        s.size.height @= 200.0
        s.position.left @= 100.0
        s.position.top @= 100.0
        s.content.children += a
        s.content.children += card(10.0, 600.0)
        s
      }
      onScreen(scroll) {
        fx(Tooltip.open(a))
        fx(shown(a)) shouldBe true
        fx(scroll.offset @= 40.0)
        fx(Tooltip.shownFor) shouldBe None
        fx(made.head.takenApart) shouldBe true
      }
    }
    "close as what it belongs to is taken apart" in {
      val a = fx(tipped(card(100.0, 100.0)))
      onScreen(a) {
        fx(Tooltip.open(a))
        fx(shown(a)) shouldBe true
        fx(a.dispose())
        fx(Tooltip.shownFor) shouldBe None
        fx(made.head.takenApart) shouldBe true
      }
    }
    "stay on the screen at each edge, under where there is room and over where there is not" in {
      val places = List((0.0, 0.0), (1820.0, 0.0), (0.0, 1040.0), (1820.0, 1040.0), (900.0, 520.0))
      places.foreach { case (left, top) =>
        val a = fx(tipped(card(left, top), width = 300.0))
        onScreen(a) {
          val sides = new java.util.concurrent.atomic.AtomicReference[TipSide](null)
          fx(a.tipView @= Some { tip =>
            val view = new TextView {
              content @= "Wide"
              size.width @= 300.0
              size.height @= 30.0
            }
            tip.side.attachAndFire(s => sides.set(s))
            view
          })
          fx(Tooltip.open(a))
          fx(shown(a)) shouldBe true
          val (x, y, w, h) = fx((plate.position.left(), plate.position.top(), plate.size.width(), plate.size.height()))
          withClue(s"card at ($left, $top): tip at ($x, $y) $w by $h: ") {
            x should be >= 8.0
            x + w should be <= 1912.0
            y should be >= 8.0
            y + h should be <= 1072.0
            if (top > 1000.0) {
              sides.get shouldBe TipSide.Above
              y + h shouldBe (top - 6.0 +- 0.5)
            }
            else {
              sides.get shouldBe TipSide.Below
              y shouldBe (top + 46.0 +- 0.5)
            }
          }
        }
      }
    }
    "be no wider than maxWidth" in {
      val a = fx(tipped(card(100.0, 100.0), width = 900.0))
      onScreen(a) {
        fx(Tooltip.open(a))
        fx(plate.size.width()) shouldBe (fx(Tooltip.maxWidth()) + 20.0 +- 0.5)
      }
    }
    "stay open as the pointer moves into an interactive one, and close itself through its context" in {
      val a = fx(card(100.0, 100.0))
      var context: Option[TipContext] = None
      fx {
        a.tooltip @= "Sigil"
        a.tipInteractive @= true
        a.tipView @= Some { tip =>
          context = Some(tip)
          val view = new TextView {
            content @= "Open"
            size.width @= 120.0
            size.height @= 30.0
          }
          made = view :: made
          view
        }
      }
      onScreen(a) {
        fx(enter(a))
        eventually(shown(a)) shouldBe true
        // off the card and onto the tip within the grace
        fx(leave(a))
        fx(plate.event.pointer.enter @= PointerEvent(PointerKind.Enter, 150.0, 160.0))
        Thread.sleep(400L)
        fx(shown(a)) shouldBe true
        // its own action done, it closes itself
        val first = fx(context.get)
        fx(first.close())
        fx(Tooltip.shownFor) shouldBe None
        fx(made.head.takenApart) shouldBe true
        // a context of one closed closes nothing that opened since
        fx(Tooltip.open(a))
        fx(shown(a)) shouldBe true
        fx(first.close())
        fx(shown(a)) shouldBe true
        // and off both, it closes after the grace
        fx(leave(a))
        eventually(Tooltip.shownFor.isEmpty) shouldBe true
        fx(made.forall(_.takenApart)) shouldBe true
      }
    }
    "close a plain tip as the pointer leaves, though it moves onto the tip" in {
      val a = fx(tipped(card(100.0, 100.0)))
      onScreen(a) {
        fx(enter(a))
        eventually(shown(a)) shouldBe true
        fx(leave(a))
        fx(Tooltip.shownFor) shouldBe None
      }
    }
    "be heard by a screen reader as its tooltip's words, with no platform tip beside it" in {
      val a = fx(card(100.0, 100.0))
      onScreen(a) {
        fx(a.tooltip @= "Sigil: 5 unread")
        fx(a.node.fx.getProperties.get("javafx.scene.control.Tooltip")) should not be null
        fx(tipped(a))
        fx(a.node.fx.getProperties.get("javafx.scene.control.Tooltip")) shouldBe null
        fx(a.node.fx.getAccessibleHelp) shouldBe "Sigil: 5 unread"
        fx(a.tooltip @= "Sigil: 6 unread")
        fx(a.node.fx.getAccessibleHelp) shouldBe "Sigil: 6 unread"
        // without its view, the platform's own tip again
        fx(a.tipView @= None)
        fx(a.node.fx.getProperties.get("javafx.scene.control.Tooltip")) should not be null
        fx(Option(a.node.fx.getAccessibleHelp).getOrElse("")) shouldBe ""
      }
    }
  }

  "A RichTip" should {
    "lay out its parts within its width, each made and taken apart with the tip" in {
      val a = fx(card(100.0, 100.0))
      onScreen(a) {
        fx(a.tipView @= Some(_ => RichTip("Sigil", lines = List(RichTipLine(MaterialIcon.Mail, "5 unread"),
          RichTipLine(MaterialIcon.Terminal, "mostly sbt in 'Sigil', and a line long enough to wrap in the tip", Some(Tone.Success))),
          meters = List(RichTipMeter("CPU", 0.68, Tone.Warning, "21.8 cores"), RichTipMeter("Memory", 0.23, words = "14.9 GB")),
          footer = Some("Updated a moment ago"))))
        val before = fx(Component.liveNow)
        fx(Tooltip.open(a))
        val tip = fx(plate.children().head.asInstanceOf[RichTip])
        eventually(tip.size.height() > 100.0) shouldBe true
        fx(tip.size.width()) shouldBe 280.0
        fx(tip.children().forall(c => c.position.left() >= 0.0 && c.position.left() + c.size.width() <= 280.5)) shouldBe true
        fx(plate.size.height()) shouldBe (fx(tip.size.height()) + 12.0 +- 0.5)
        fx(Tooltip.hide())
        fx(tip.takenApart) shouldBe true
        fx(Component.liveNow) shouldBe before
      }
    }
  }
}
