package everyui.chart

import everyui.access.LiveRegions
import everyui.component.{Button, Component, Container, TextView}
import everyui.event.ScrollPress
import everyui.focus.Focus
import everyui.motion.ReduceMotion
import everyui.{FxSpec, ui}
import javafx.scene.input.{MouseButton, MouseEvent}
import reactify.*

/** A chart's marks that act: chosen by a click let go on the mark it went down on (not a drag, nor a press that
  * scrolled), by Enter and Space on the mark the keys are on, Tab bringing the keys to the chart, and by a row's
  * button in its table; the marks that lead somewhere, which show the hand, are said to a screen reader to, and say what
  * they do in the tip; and the axes' words and the tip, which let the pointer through to the plot. The pointer is the
  * desktop's own mouse events, on the plot's node. */
class ChartActionSpec extends FxSpec {
  override protected def keyed: Boolean = true

  private val days = Vector("Mon", "Tue", "Wed")

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx(ReduceMotion.on @= true)
  }
  override def afterAll(): Unit = {
    fx(ReduceMotion.on @= false)
    super.afterAll()
  }

  /** A bar chart of three days on the screen, after `before` (what has the focus first), for `f`, and taken away. */
  private def shown[T](before: Component = new Container)(f: BarChart => T): T = {
    val chart = fx {
      val c = BarChart(days, Vector(Series("Spend", Vector(4.0, 9.0, 6.0))), ChartFormat.money("$", 0))
      c.size.width @= 400.0
      c.size.height @= 240.0
      c.position.top @= 40.0
      ui.children ++= List(before, c)
      c
    }
    try f(chart)
    finally fx {
      ui.children -= chart
      ui.children -= before
      chart.dispose()
      Focus.focused @= None
    }
  }

  private def all(c: Component): List[Component] = c :: c.childComponents.toList.flatMap(all)

  /** Everything told to `channel` while `f` runs. */
  private def heard[A, T](channel: Channel[A])(f: => T): List[A] = {
    var told = List.empty[A]
    val listening = fx(channel.attach(a => told = told :+ a))
    try {
      f
      fx(told)
    }
    finally fx(channel.reactions -= listening)
  }

  // a mouse event on the plot's node at (`x`, `y`) in the plot
  private def mouse(chart: Chart, kind: javafx.event.EventType[MouseEvent], x: Double, y: Double): Unit = fx {
    val sx = chart.plot.absoluteLeft + x
    val sy = chart.plot.absoluteTop + y
    javafx.event.Event.fireEvent(chart.plot.node.fx, new MouseEvent(kind, sx, sy, sx, sy, MouseButton.PRIMARY, 1,
      false, false, false, false, true, false, false, false, false, true, null))
  }

  private def bar(chart: BarChart, day: Int): ChartMark = fx(chart.bars().find(_._1.category == day).get._1)

  private def key(name: String): Unit = fx {
    Focus.key(name, true)
    Focus.key(name, false)
  }

  "A chart's marks" should {
    "be chosen by a click let go on the mark it went down on, never after a drag, a scroll or onto another" in {
      shown()(chart => {
        val tue = bar(chart, 1)
        val wed = bar(chart, 2)
        val clicked = heard(chart.chosen) {
          mouse(chart, MouseEvent.MOUSE_PRESSED, tue.centerX, tue.centerY)
          mouse(chart, MouseEvent.MOUSE_RELEASED, tue.centerX, tue.centerY)
          mouse(chart, MouseEvent.MOUSE_CLICKED, tue.centerX, tue.centerY)
        }
        clicked.map(p => (p.category, p.pressed)) shouldBe List(("Tue", true))
        // a press told as it goes down still, to `pointed`
        heard(chart.pointed)(mouse(chart, MouseEvent.MOUSE_PRESSED, wed.centerX, wed.centerY))
          .map(p => (p.category, p.pressed)) shouldBe List(("Wed", true))
        val none = heard(chart.chosen) {
          // dragged from Wednesday onto Tuesday
          mouse(chart, MouseEvent.MOUSE_RELEASED, tue.centerX, tue.centerY)
          mouse(chart, MouseEvent.MOUSE_CLICKED, tue.centerX, tue.centerY)
          // dragged along Tuesday's bar further than a tap may
          mouse(chart, MouseEvent.MOUSE_PRESSED, tue.centerX, tue.y + 4.0)
          mouse(chart, MouseEvent.MOUSE_CLICKED, tue.centerX, tue.y + 4.0 + ScrollPress.slop + 4.0)
          // a press that scrolled what the chart is in
          mouse(chart, MouseEvent.MOUSE_PRESSED, tue.centerX, tue.centerY)
          fx(ScrollPress.scrolled())
          mouse(chart, MouseEvent.MOUSE_RELEASED, tue.centerX, tue.centerY)
          mouse(chart, MouseEvent.MOUSE_CLICKED, tue.centerX, tue.centerY)
          // and nowhere near a bar
          mouse(chart, MouseEvent.MOUSE_PRESSED, 1.0, 1.0)
          mouse(chart, MouseEvent.MOUSE_CLICKED, 1.0, 1.0)
        }
        none shouldBe Nil
        // a press a little off where it went down, on the same bar, is still a click
        heard(chart.chosen) {
          mouse(chart, MouseEvent.MOUSE_PRESSED, tue.centerX, tue.centerY)
          mouse(chart, MouseEvent.MOUSE_CLICKED, tue.centerX + 2.0, tue.centerY + 3.0)
        }.map(_.category) shouldBe List("Tue")
      })
    }
    "let the pointer through the axes' words and the tip to the plot" in {
      shown()(chart => {
        val tue = bar(chart, 1)
        mouse(chart, MouseEvent.MOUSE_MOVED, tue.centerX, tue.centerY)
        fx(chart.hovered().map(_.category)) shouldBe Some("Tue")
        // the plot, its words over it, the tip over both: only the plot takes the pointer
        val layers = fx(chart.childComponents.toList.filterNot(_ eq chart.plot)
          .filter(c => c.isInstanceOf[Container] && !c.isInstanceOf[everyui.component.IconButton]))
        layers.size shouldBe 2
        layers.foreach(l => fx(l.node.fx.isMouseTransparent) shouldBe true)
        fx(chart.plot.node.fx.isMouseTransparent) shouldBe false
      })
    }
    "be reached by Tab, moved along by the arrows, each said, and chosen by Enter and Space" in {
      val before = new Button("Before")
      shown(before)(chart => {
        fx(before.focus())
        key("Tab")
        fx(Focus.focused().exists(_ eq chart)) shouldBe true
        val said = heard(LiveRegions.announced) {
          key("ArrowRight")
          key("ArrowRight")
        }
        fx(chart.hovered().map(_.category)) shouldBe Some("Tue")
        said.map(_.words) shouldBe List("Mon, $4", "Tue, $9")
        val chosen = heard(chart.chosen) {
          key("Enter")
          key(" ")
        }
        chosen.map(p => (p.category, p.pressed)) shouldBe List(("Tue", true), ("Tue", true))
        // the focus stays on the chart, and Tab goes on from it to its table's button
        fx(Focus.focused().exists(_ eq chart)) shouldBe true
        key("Tab")
        fx(Focus.focused().exists(c => c.isInstanceOf[everyui.component.IconButton])) shouldBe true
        fx(chart.hovered()) shouldBe None
      })
    }
    "go to the first mark on Enter where the keys are on none, and choose it on the next" in {
      shown()(chart => {
        fx(chart.focus())
        heard(chart.chosen)(key("Enter")) shouldBe Nil
        fx(chart.hovered().map(_.category)) shouldBe Some("Mon")
        heard(chart.chosen)(key("Enter")).map(_.category) shouldBe List("Mon")
      })
    }
    "show the hand over the marks that lead somewhere, and say so to a screen reader" in {
      shown()(chart => {
        val (mon, tue) = (bar(chart, 0), bar(chart, 1))
        fx(chart.pointAt(tue.centerX, tue.centerY, pressed = false))
        fx(chart.plot.cursor()) shouldBe "default"
        fx(chart.semantics().description) shouldBe ""
        fx(chart.actionable @= (p => p.category == "Tue"))
        fx(chart.plot.cursor()) shouldBe "pointer"
        fx(chart.pointAt(mon.centerX, mon.centerY, pressed = false))
        fx(chart.plot.cursor()) shouldBe "default"
        fx(chart.semantics().description) shouldBe "Press a bar to open it."
        fx(chart.spoken(chart.hovered().get)) shouldBe "Mon, $4"
        fx(chart.actionWords @= (p => "Open " + p.category))
        // what choosing does, in the tip and said after the value
        fx(chart.pointAt(tue.centerX, tue.centerY, pressed = false))
        fx(all(chart).collect { case t: TextView => t.content() }) should contain("Open Tue")
        fx(chart.spoken(chart.hovered().get)) shouldBe "Tue, $9, Open Tue"
        fx(chart.actionWords @= Chart.noWords)
        fx(chart.spoken(chart.hovered().get)) shouldBe "Tue, $9, press to open"
      })
    }
    "make the table's rows that lead somewhere buttons that choose" in {
      shown()(chart => {
        fx(chart.actionable @= (p => p.category != "Wed"))
        fx(chart.table @= true)
        def buttons = fx(all(chart).collect { case v: ChartTableView => v }.flatMap(all)
          .filter(c => c.semantics().role == "button"))
        def words(c: Component) = fx(all(c).collect { case t: TextView => t.content() })
        buttons.flatMap(words) shouldBe List("Mon", "Tue")
        val tue = buttons(1)
        heard(chart.chosen)(fx(tue.event.click @= everyui.event.PointerEvent(everyui.event.PointerKind.Click, 0.0, 0.0)))
          .map(_.category) shouldBe List("Tue")
        // by the keys: Enter and Space on the button with the focus
        fx(tue.focus())
        heard(chart.chosen) {
          key("Enter")
          key(" ")
        }.map(_.category) shouldBe List("Tue", "Tue")
        // nothing leads anywhere: no buttons
        fx(chart.actionable @= Chart.nothing)
        buttons shouldBe Nil
      })
    }
  }
}
