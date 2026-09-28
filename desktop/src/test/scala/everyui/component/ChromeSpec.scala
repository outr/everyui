package everyui.component

import everyui.focus.Focus
import everyui.network.Network
import everyui.render.Nodes
import everyui.split.{SplitPane, SplitSide}
import everyui.{FxSpec, ui}
import reactify.*

/** Smaller things apps asked for: relative URLs on the app's origin, a split made folded, and a compact checkbox. */
class ChromeSpec extends FxSpec {
  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      ui.children ++= parts
    }
    try test finally fx {
      ui.children.clear()
      Focus.focused @= None
    }
  }

  "A relative URL" should {
    "go to the origin, however it is written" in {
      val was = Network.origin
      try {
        Network.origin = "https://example.com/"
        Network.resolve("/api/x") shouldBe "https://example.com/api/x"
        Network.resolve("api/x") shouldBe "https://example.com/api/x"
        Network.resolve("//cdn.example.com/a.js") shouldBe "https://cdn.example.com/a.js"
        Network.resolve("http://other.com/y") shouldBe "http://other.com/y"
        Network.origin = ""
        an[IllegalArgumentException] should be thrownBy Network.resolve("/api/x")
      }
      finally Network.origin = was
    }
  }

  "A split made folded" should {
    "show folded from the first" in {
      val (a, b) = fx((new Component(Nodes.container()), new Component(Nodes.container())))
      val split = fx(new SplitPane(a, b, ratio = Var(0.3), collapsible = Some(SplitSide.First), startCollapsed = true))
      fx {
        split.size.width @= 1006.0
        split.size.height @= 600.0
      }
      fx((a.size.width(), b.size.width())) shouldBe (0.0, 1000.0)
      fx(split.toggle())
      fx(a.size.width()) shouldBe 300.0
    }
  }

  "A compact checkbox" should {
    "be 24 pixels tall and as wide as its label" in {
      val (small, full) = fx((new Checkbox("Wrap lines", compact = true), new Checkbox("Wrap lines")))
      onScreen(small, full) {
        fx(small.size.height()) shouldBe 24.0
        fx(full.size.height()) shouldBe 28.0
        fx(small.size.width()) should be < 150.0
        fx(full.size.width()) shouldBe 400.0
        fx(small.focus())
        fx(small.borderWidth()) shouldBe 2.0
      }
    }
  }
}
