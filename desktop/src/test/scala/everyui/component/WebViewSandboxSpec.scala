package everyui.component

import everyui.FxSpec
import everyui.render.RenderNode
import javafx.scene.layout.Pane

/** A WebView's sandbox as its node is told it: nothing by default, before the page where it is set first, again each
  * time it changes and not when it does not; and an empty box, where there is no engine, taking it as it takes the rest. */
class WebViewSandboxSpec extends FxSpec {
  /** A node that writes down what it is told of its page. */
  private class Told extends RenderNode(new Pane) {
    var said: List[String] = Nil
    override def setWebSource(url: String): Unit = said = said :+ ("source " + url)
    override def setWebSandbox(flags: Option[Set[String]]): Unit =
      said = said :+ ("sandbox " + flags.map(_.toList.sorted.mkString(" ")).getOrElse("off"))
  }

  "A WebView's sandbox" should {
    "be none by default, told before the page where set first, and told again only as it changes" in {
      val node = fx(new Told)
      val view = fx(new WebView("bridge", node))
      fx(view.sandbox()) shouldBe None
      fx(view.source @= "page.html")
      node.said shouldBe List("source page.html")

      val other = fx(new Told)
      val boxed = fx(new WebView("bridge", other))
      fx {
        boxed.sandbox @= Some(Set.empty)
        boxed.source @= "file.html"
      }
      other.said shouldBe List("sandbox ", "source file.html")
      fx(boxed.sandbox @= Some(Set("allow-scripts")))
      fx(boxed.sandbox @= Some(Set("allow-scripts")))
      fx(boxed.sandbox @= None)
      other.said shouldBe List("sandbox ", "source file.html", "sandbox allow-scripts", "sandbox off")
    }

    "be taken and ignored where there is no engine to lend a page" in {
      val view = fx(new WebView("bridge"))
      fx {
        view.sandbox @= Some(Set("allow-scripts", "allow-forms"))
        view.source @= "page.html"
        view.run("ping()")
      }
      fx(view.sandbox()) shouldBe Some(Set("allow-scripts", "allow-forms"))
      fx(view.dispose())
    }
  }
}
