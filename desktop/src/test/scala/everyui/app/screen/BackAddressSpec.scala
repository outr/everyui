package everyui.app.screen

import everyui.async.Task
import everyui.component.{Button, Component}
import everyui.focus.Focus
import everyui.keymap.Keymap
import everyui.net.URLPath
import everyui.render.Platform
import everyui.task.Frames
import everyui.{FxSpec, ui}

/** The browser's Back returns to the screen left, as it was left, where the app makes a new screen for each showing of
  * a path (a title's page): the same one, with the focus it had, and not the screen being left put on the history. */
class BackAddressSpec extends FxSpec {
  private class Page(val manager: ScreenManager, val id: Int) extends ScrollScreen {
    override def title: String = "Page " + id
    override def path: URLPath = URLPath("/page/" + id)
    override def oneShowing: Boolean = true
    val first = new Button("First")
    val second = new Button("Second")

    override def defaultFocus(): Component = first

    override def createUI(): Task[Unit] = Task {
      body.children ++= List(first, second)
    }
  }

  private object Manager extends ScreenManager {
    // a new page for each showing, as an app making its title pages does
    override def at(path: URLPath): Option[Screen] =
      if (path.value.startsWith("/page/")) Some(new Page(this, path.value.stripPrefix("/page/").toInt))
      else super.at(path)
  }

  private lazy val home = new Page(Manager, 0)

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx {
      ui.children.clear()
      Focus.focused @= None
      // the focus moved by the keys: where a pointer is used, Back puts it back only then (Focus.restores)
      Focus.visible @= true
      Platform.pushPath("/")
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
      Focus.visible @= false
      Keymap.shown @= (() => None)
      ScreenManager.running = None
    }
    super.afterAll()
  }

  "The browser's Back" should {
    "return to the page left, with its focus" in {
      val one = fx(new Page(Manager, 1))
      fx(Manager.show(one).startUnit())
      eventually(Manager.active().exists(_ eq one)) shouldBe true
      eventually(Focus.focused().exists(_ eq one.first)) shouldBe true
      fx(one.second.focus())
      val two = fx(new Page(Manager, 2))
      fx(Manager.show(two).startUnit())
      eventually(Manager.active().exists(_ eq two)) shouldBe true
      // what a browser does: the address first, then popstate
      fx {
        Platform.pushPath("/page/1")
        Manager.arrived("/page/1")
      }
      eventually(Manager.active().exists(_ eq one)) shouldBe true
      eventually(Focus.focused().exists(_ eq one.second)) shouldBe true
      // and Back again goes on back, not forward to the page just left
      fx {
        Platform.pushPath("/")
        Manager.arrived("/")
      }
      eventually(Manager.active().exists(_ eq home)) shouldBe true
    }
    "show a new page for an address it was not at" in {
      fx {
        Platform.pushPath("/page/7")
        Manager.arrived("/page/7")
      }
      eventually(Manager.active().exists {
        case p: Page => p.id == 7
        case _ => false
      }) shouldBe true
    }
  }
}
