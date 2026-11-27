package everyui.app

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The paths an app has been at where the platform has no browser (PathHistory): kept as a browser keeps them, and a
  * Back nothing took going back through them only for an app that listens to its path. */
class PathHistorySpec extends AnyWordSpec with Matchers {
  "A path history" should {
    "start with the path the app was launched at, and nowhere to go" in {
      val history = new PathHistory("/start")
      history.entries shouldBe List("/start")
      history.current shouldBe "/start"
      history.back() shouldBe None
      history.forward() shouldBe None
    }
    "push a path after the one showing, and step back and forward through them" in {
      val history = new PathHistory("/")
      history.push("/a")
      history.push("/b")
      history.back() shouldBe Some("/a")
      history.back() shouldBe Some("/")
      history.back() shouldBe None
      history.forward() shouldBe Some("/a")
      history.forward() shouldBe Some("/b")
      history.forward() shouldBe None
    }
    "drop the paths gone back past when one is pushed" in {
      val history = new PathHistory("/")
      history.push("/a")
      history.push("/b")
      history.back()
      history.push("/c")
      history.entries shouldBe List("/", "/a", "/c")
      history.forward() shouldBe None
    }
    "replace the path showing, and only that one" in {
      val history = new PathHistory("/")
      history.push("/a")
      history.push("/b")
      history.back()
      history.replace("/a2")
      history.entries shouldBe List("/", "/a2", "/b")
      history.current shouldBe "/a2"
    }
    "go back to a launch path replaced as the app lands, rather than leave" in {
      // Voidcraft: its landing conversation written over the launch path, then a conversation pushed
      val history = new PathHistory("")
      history.listen(_ => ())
      history.replace("/c/landing")
      history.entries shouldBe List("/c/landing")
      history.takesBack shouldBe false
      history.push("/c/other")
      history.takesBack shouldBe true
      history.back() shouldBe Some("/c/landing")
      history.takesBack shouldBe false
    }
    "keep the last hundred paths" in {
      val history = new PathHistory("/0")
      (1 to 150).foreach(i => history.push("/" + i))
      history.entries.length shouldBe 100
      history.entries.head shouldBe "/51"
      history.current shouldBe "/150"
      (1 to 99).foreach(_ => history.back().nonEmpty shouldBe true)
      history.current shouldBe "/51"
      history.back() shouldBe None
    }
    "come back as it was saved" in {
      val history = new PathHistory("/")
      history.push("/a")
      history.push("/b")
      history.back()
      val restored = PathHistory.restored(history.entries, history.index)
      restored.entries shouldBe List("/", "/a", "/b")
      restored.current shouldBe "/a"
      restored.forward() shouldBe Some("/b")
      PathHistory.restored(List("/x"), 5).current shouldBe "/x"
      PathHistory.restored(Nil, 0).current shouldBe ""
    }
    "tell its listeners the path arrived at" in {
      val history = new PathHistory("/")
      var heard = List.empty[String]
      history.listen(p => heard = heard :+ p)
      history.tell("/a")
      heard shouldBe List("/a")
    }
  }

  "A Back nothing in the app took" should {
    "go back a path where the app listens to its path and has one to go back to" in {
      val history = new PathHistory("/")
      history.push("/a")
      history.takesBack shouldBe false
      history.listen(_ => ())
      history.takesBack shouldBe true
      history.takesForward shouldBe false
      history.back()
      history.takesBack shouldBe false
      history.takesForward shouldBe true
    }
    "leave the app where it does not listen, whatever it pushed" in {
      val history = new PathHistory("/")
      history.push("/a")
      history.push("/b")
      history.takesBack shouldBe false
      history.back()
      history.takesForward shouldBe false
    }
  }
}
