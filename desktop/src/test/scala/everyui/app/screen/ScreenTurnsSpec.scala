package everyui.app.screen

import everyui.async.Task
import everyui.component.{Button, Column, Component, Container}
import everyui.focus.Focus
import everyui.keymap.Keymap
import everyui.net.URLPath
import everyui.render.Platform
import everyui.task.Frames
import everyui.{FxSpec, ui}
import reactify.*

/** A screen change spread over turns, as a Roku needs of a long build (one turn holding it all ran past the device's
  * limit, and the screen never showed): the screen being left is deactivated on a turn of its own, a form's card and
  * what the screen puts on it each on another, then its load, then its arrival, every one of them in the one layout
  * pass, laid out once it has arrived and before the focus moves, the screen in the host only from its arrival. A
  * change asked for while another is under way waits for it; a screen rebuilt in sight is rebuilt and arrives in one
  * turn, so no frame shows it half made. */
class ScreenTurnsSpec extends FxSpec {
  /** Each stage as it ran, and whether it ran on the same turn as the stage before it. */
  private var stages: Vector[(String, Boolean)] = Vector.empty
  private var sameTurn: Boolean = false

  private def stage(name: String): Unit = {
    stages = stages :+ (name -> sameTurn)
    sameTurn = true
    // cleared by the next turn: a stage that still finds it set ran on this one
    Platform.later(() => sameTurn = false)
  }

  private class Form(val manager: ScreenManager, name: String) extends FormScreen {
    override def heading: Val[String] = Val(name)
    override def path: URLPath = URLPath("/" + name)
    val go = new Button("Go")
    val rows: Container = Column(8.0)()
    var hostHeldIt: Boolean = false
    var laidOutAtArrival: Boolean = true

    override def defaultFocus(): Component = go

    override protected def header(): Component = {
      stage(name + " card")
      super.header()
    }

    override protected def build(): Unit = {
      stage(name + " build")
      hostHeldIt = manager.host.children().exists(_ eq container)
      rows.children ++= List(new Button("One"), new Button("Two"), new Button("Three"))
      fields.children += rows
      actions.children += go
    }

    override protected def load(): Task[Unit] = Task(stage(name + " load"))

    override protected def activate(): Task[Unit] = super.activate().map { _ =>
      stage(name + " activate")
      // the rows are not laid out until the whole change is: the batch held across every turn of it
      laidOutAtArrival = rows.childComponents.last.position.top() > 0.0
    }

    override protected def deactivate(): Task[Unit] = super.deactivate().map(_ => stage(name + " deactivate"))
  }

  private object Manager extends ScreenManager
  private lazy val home = new Form(Manager, "home")

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx {
      ui.children.clear()
      Focus.focused @= None
      Platform.pushPath("/home")
      Manager.run(home)
    }
    eventually(Manager.active().exists(_ eq home)) shouldBe true
  }

  override def afterAll(): Unit = {
    fx {
      Frames.drive(_ => (), () => false, () => (), _ => ())
      ui.children.clear()
      Focus.focused @= None
      Focus.home @= (() => None)
      Focus.page @= (() => None)
      Keymap.shown @= (() => None)
      ScreenManager.running = None
    }
    super.afterAll()
  }

  "A screen's first showing" should {
    "take a turn for each stage, held in one layout pass, laid out before the focus moves" in {
      val next = fx(new Form(Manager, "next"))
      val focusAtShown = fx {
        stages = Vector.empty
        var at: Option[Component] = None
        Manager.show(next).map(_ => at = Focus.focused()).startUnit()
        () => at
      }
      eventually(Manager.active().exists(_ eq next)) shouldBe true
      fx(stages) shouldBe Vector("home deactivate" -> false, "next card" -> false, "next build" -> false,
        "next load" -> false, "next activate" -> false)
      fx(next.hostHeldIt) shouldBe false
      fx(next.laidOutAtArrival) shouldBe false
      fx(next.rows.childComponents.last.position.top()) should be > 0.0
      eventually(focusAtShown().exists(_ eq next.go)) shouldBe true
    }
    "start a change asked for meanwhile once the one under way is done" in {
      val a = fx(new Form(Manager, "a"))
      val b = fx(new Form(Manager, "b"))
      fx {
        stages = Vector.empty
        Manager.show(a).startUnit()
        Manager.show(b).startUnit()
      }
      eventually(Manager.active().exists(_ eq b)) shouldBe true
      val names = fx(stages.map(_._1))
      names.indexOf("a activate") should be < names.indexOf("a deactivate")
      names.indexOf("a deactivate") should be < names.indexOf("b card")
      fx(Focus.focused().exists(_ eq b.go)) shouldBe true
    }
  }

  "A screen rebuilt in sight" should {
    "be rebuilt and arrive on one turn" in {
      val shown = fx(Manager.active().get.asInstanceOf[Form])
      fx {
        stages = Vector.empty
        Manager.rebuild(shown).startUnit()
      }
      eventually(stages.exists(_._1.endsWith("activate"))) shouldBe true
      val names = fx(stages)
      names.map(_._1).filterNot(_.endsWith("deactivate")).map(_.split(' ').last) shouldBe
        Vector("card", "build", "activate")
      names.dropWhile(_._1.endsWith("deactivate")).tail.forall(_._2) shouldBe true
    }
  }
}
