package everyui.app.screen

import everyui.async.Task
import everyui.component.{ImageView, TextView}
import everyui.focus.Focus
import everyui.image.{Image, ImageBudget}
import everyui.keymap.Keymap
import everyui.net.*
import everyui.task.Frames
import everyui.{FxSpec, ui}

/** A screen left behind (on the back stack, or kept to be shown again) lets its pictures go, kept while they fit in the
  * picture budget and let go past it, and takes them up again as it is shown: two title pages' posters are not held
  * while a third shows. */
class ScreenPicturesSpec extends FxSpec {
  private object Manager extends ScreenManager

  /** A screen of a poster and a line under it. */
  private def page(name: String): UIScreen = new UIScreen {
    override def manager: ScreenManager = Manager
    override def title: String = name
    override def path: URLPath = URLPath("/" + name)
    private val words = {
      val t = TextView(name)
      t.focusable @= true
      t
    }
    override def defaultFocus(): TextView = words
    override def createUI(): Task[Unit] = Task {
      val poster = new ImageView
      poster.image @= new Image("/images/" + name + ".png", 100.0, 150.0)
      container.children ++= List(poster, words)
    }
  }

  private lazy val home = page("home")
  private lazy val title = page("title")

  private def poster(s: UIScreen): String = fx(s.screenRoot.childComponents.collectFirst {
    case i: ImageView => i.drawnSource
  }.getOrElse("none"))

  private def show(s: UIScreen): Unit = {
    fx(Manager.show(s).startUnit())
    eventually(Manager.active().exists(_ eq s)) shouldBe true
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx {
      ui.children.clear()
      Focus.focused @= None
      ImageBudget.seenMs = 0.0
      ImageBudget.megabytes @= 0.0
      Manager.run(home, title)
    }
    eventually(Manager.active().exists(_ eq home)) shouldBe true
  }

  override def afterAll(): Unit = {
    fx {
      // no history left for a later spec's Alt and Left to go back through
      Manager.clearHistory()
      Frames.drive(_ => (), () => false, () => (), _ => ())
      ui.children.clear()
      Focus.focused @= None
      Focus.home @= (() => None)
      Focus.page @= (() => None)
      Keymap.shown @= (() => None)
      ScreenManager.running = None
      ImageBudget.seenMs = 100.0
      ImageBudget.megabytes @= ImageBudget.byDevice()
    }
    super.afterAll()
  }

  "A screen left behind" should {
    "let its pictures go, and take them up again as it is shown" in {
      poster(home) should endWith("home.png")
      show(title)
      poster(title) should endWith("title.png")
      poster(home) shouldBe ""
      show(home)
      poster(home) should endWith("home.png")
      poster(title) shouldBe ""
    }
    "keep them while they fit in the budget" in {
      fx(ImageBudget.megabytes @= 1.0)
      show(title)
      poster(home) should endWith("home.png")
      fx(ImageBudget.keptCount) shouldBe 1
      show(home)
      fx(ImageBudget.keptCount) shouldBe 1
      fx(ImageBudget.trim())
      poster(title) shouldBe ""
      fx(ImageBudget.megabytes @= 0.0)
    }
  }
}
