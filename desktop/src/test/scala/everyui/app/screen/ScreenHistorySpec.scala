package everyui.app.screen

import everyui.async.Task
import everyui.component.{Button, Component}
import everyui.focus.Focus
import everyui.keymap.Keymap
import everyui.net.URLPath
import everyui.render.{Host, Platform}
import everyui.task.Frames
import everyui.{FxSpec, ui}

/** The screen manager off the web goes back through its own history and never the platform's path history
  * (everyui.app.PathHistory): it does not listen to the path, and Back does not step it, so a Back nothing took at its
  * first screen still leaves the app (NaboTV's Home). */
class ScreenHistorySpec extends FxSpec {
  private class Page(val manager: ScreenManager, val id: Int) extends ScrollScreen {
    override def title: String = "Page " + id
    override def path: URLPath = URLPath("/page/" + id)
    val first = new Button("First")

    override def defaultFocus(): Component = first

    override def createUI(): Task[Unit] = Task {
      body.children += first
    }
  }

  private object Manager extends ScreenManager

  private lazy val home = new Page(Manager, 0)
  private var was: (String, everyui.app.PathHistory) = ("", null)

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx {
      was = (Host.path, Host.history)
      Host.path = "/page/0"
      Host.history = null
      ui.children.clear()
      Focus.focused @= None
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
      Host.path = was._1
      Host.history = was._2
    }
    super.afterAll()
  }

  "The screen manager on the desktop" should {
    "push its screens' paths, but neither listen to them nor step back through them" in {
      val one = fx(new Page(Manager, 1))
      fx(Manager.show(one).startUnit())
      eventually(Manager.active().exists(_ eq one)) shouldBe true
      val two = fx(new Page(Manager, 2))
      fx(Manager.show(two).startUnit())
      eventually(Manager.active().exists(_ eq two)) shouldBe true
      fx(Host.paths.entries) shouldBe List("/page/0", "/page/1", "/page/2")
      fx(Host.paths.listened) shouldBe false
      // Back is its own: the page before, with the platform's history where it was
      fx(Manager.back()) shouldBe true
      eventually(Manager.active().exists(_ eq one)) shouldBe true
      fx(Host.paths.index) shouldBe 2
      fx(Manager.back()) shouldBe true
      eventually(Manager.active().exists(_ eq home)) shouldBe true
      fx(Host.paths.index) shouldBe 2
    }
    "leave Back at its first screen to the platform, which does not go back a path" in {
      fx(Manager.back()) shouldBe false
      fx(Platform.untaken("BrowserBack")) shouldBe false
      fx(Host.paths.index) shouldBe 2
    }
  }
}
