package everyui.render

import everyui.FxSpec
import org.scalatest.BeforeAndAfterEach

/** The desktop's path history (everyui.app.PathHistory) through Platform, and a BrowserBack or BrowserForward nothing
  * in the app took (a mouse's side buttons, Alt+Left): back or forward a path only where the app listens to its path. */
class PathHistoryBackSpec extends FxSpec with BeforeAndAfterEach {
  private var was: (String, everyui.app.PathHistory) = ("", null)

  override def beforeEach(): Unit = {
    was = (Host.path, Host.history)
    Host.path = "/launch"
    Host.history = null
  }

  override def afterEach(): Unit = {
    Host.path = was._1
    Host.history = was._2
  }

  "The desktop's paths" should {
    "start at the launch path, and step back and forward through what was pushed, telling the app" in {
      var heard = List.empty[String]
      Platform.onPath(p => heard = heard :+ p)
      Platform.pushPath("/a")
      Platform.pushPath("/b")
      Platform.historyBack() shouldBe true
      Platform.path() shouldBe "/a"
      Platform.historyBack() shouldBe true
      Platform.path() shouldBe "/launch"
      Platform.historyBack() shouldBe false
      Platform.historyForward() shouldBe true
      Platform.path() shouldBe "/a"
      heard shouldBe List("/a", "/launch", "/a")
    }
    "replace the launch path, which Back then returns to" in {
      Platform.onPath(_ => ())
      Platform.replacePath("/landing")
      Platform.pushPath("/next")
      Platform.historyBack() shouldBe true
      Platform.path() shouldBe "/landing"
      Platform.historyBack() shouldBe false
    }
  }

  "A BrowserBack nothing took" should {
    "go back a path where the app listens to its path" in {
      var heard = List.empty[String]
      Platform.onPath(p => heard = heard :+ p)
      Platform.pushPath("/a")
      Platform.untaken("BrowserBack") shouldBe true
      Platform.path() shouldBe "/launch"
      heard shouldBe List("/launch")
      // at the first path it is left as it was
      Platform.untaken("BrowserBack") shouldBe false
      Platform.untaken("BrowserForward") shouldBe true
      Platform.path() shouldBe "/a"
      Platform.untaken("Escape") shouldBe false
    }
    "do nothing where the app does not listen, whatever it pushed" in {
      Platform.pushPath("/a")
      Platform.pushPath("/b")
      Platform.untaken("BrowserBack") shouldBe false
      Platform.path() shouldBe "/b"
      Host.paths.index shouldBe 2
    }
  }
}
