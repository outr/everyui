package everyui.app

import everyui.FxSpec
import everyui.render.{BusyDisplay, Host}
import javafx.stage.Stage

/** The app at work on the desktop: the window's title takes the prefix only when asked to, and has the app's own back
  * exactly once idle; and what the taskbar offers on this machine, printed. The stage is never shown. */
class BusyTitleSpec extends FxSpec {
  private var stage: Stage = null

  override def beforeAll(): Unit = {
    super.beforeAll()
    fx {
      stage = new Stage()
      stage.setScene(Host.scene)
      stage.setTitle("Workflows")
    }
  }

  override def afterAll(): Unit = {
    fx {
      AppBusy.state @= None
      AppBusy.inTitle @= false
      stage.setScene(null)
    }
    super.afterAll()
  }

  "The desktop" should {
    "say what its taskbar offers" in {
      val said = fx(BusyDisplay.describe())
      info(said)
      said should startWith("taskbar supported=")
      fx(AppBusy.supported) shouldBe said.contains("progress=true") || said.contains("badge=true")
    }
    "leave the title alone unless asked" in {
      fx(AppBusy.state @= Some(Busy(Some(0.42), Some("deliver"))))
      fx(stage.getTitle) shouldBe "Workflows"
      fx(AppBusy.state @= None)
    }
    "put the progress before the title while busy, and take it off exactly once idle" in {
      fx(AppBusy.inTitle @= true)
      fx(AppBusy.state @= Some(Busy()))
      fx(stage.getTitle) shouldBe "(…) Workflows"
      fx(AppBusy.state @= Some(Busy(Some(0.42), Some("deliver"))))
      fx(stage.getTitle) shouldBe "(42% deliver) Workflows"
      fx(stage.setTitle("Runs"))   // the app retitles while busy
      fx(AppBusy.state @= Some(Busy(Some(0.5), Some("deliver"))))
      fx(stage.getTitle) shouldBe "(50% deliver) Runs"
      fx(AppBusy.state @= None)
      fx(stage.getTitle) shouldBe "Runs"
    }
  }
}
