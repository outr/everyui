package everyui.component

import everyui.FxSpec
import everyui.render.{Platform, RenderNode}
import javafx.scene.layout.Pane

/** A WebView lets its page go once as it is taken apart, which Android takes as the view's end (destroyed, its page
  * stopped), and the engine warmed (`WebView.prewarm`) where there is none to warm does nothing, as often as asked. */
class WebViewReleaseSpec extends FxSpec {
  /** A node that counts the times it is told to let its page go. */
  private class Told extends RenderNode(new Pane) {
    var released: Int = 0
    override def releaseWeb(): Unit = released += 1
  }

  "A WebView" should {
    "let its page go once as it is taken apart, and not before" in {
      val node = fx(new Told)
      val view = fx(new WebView("bridge", node))
      fx(view.source @= "page.html")
      node.released shouldBe 0
      fx(view.dispose())
      node.released shouldBe 1
      fx(view.dispose())
      node.released shouldBe 1
    }

    "warm nothing where there is no engine, as often as asked, and still make a view after" in {
      fx(Platform.webViews()) shouldBe false
      fx {
        WebView.prewarm()
        WebView.prewarm()
      }
      val view = fx(new WebView("bridge"))
      fx(view.source @= "page.html")
      fx(view.dispose())
    }
  }
}
