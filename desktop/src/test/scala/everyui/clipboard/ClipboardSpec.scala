package everyui.clipboard

import everyui.FxSpec

/** The clipboard on the desktop: what is copied is read back, HTML and text together and Markdown as both, and a
  * clipboard with no text reads as None. Whatever the machine's clipboard held before is put back after. */
class ClipboardSpec extends FxSpec {
  private var before: Option[java.util.Map[javafx.scene.input.DataFormat, AnyRef]] = None

  override def beforeAll(): Unit = {
    super.beforeAll()
    before = Some(fx {
      val clipboard = javafx.scene.input.Clipboard.getSystemClipboard
      val kept = new java.util.HashMap[javafx.scene.input.DataFormat, AnyRef]()
      clipboard.getContentTypes.forEach(t => Option(clipboard.getContent(t)).foreach(c => kept.put(t, c)))
      kept
    })
  }

  override def afterAll(): Unit = {
    before.foreach(kept => fx(javafx.scene.input.Clipboard.getSystemClipboard.setContent(kept)))
    super.afterAll()
  }

  private def read(): Option[String] = {
    var answer: Option[Option[String]] = None
    fx(Clipboard.read().map(t => answer = Some(t)).start())
    eventually(answer.nonEmpty) shouldBe true
    answer.get
  }

  "The clipboard" should {
    "copy and read on the desktop" in {
      Clipboard.copies shouldBe true
      Clipboard.readable shouldBe true
    }

    "read back what was copied" in {
      fx(Clipboard.copy("everyui clipboard spec"))
      read() shouldBe Some("everyui clipboard spec")
    }

    "copy HTML and text together" in {
      fx(Clipboard.copyRich("<p><strong>Hi</strong></p>", "Hi"))
      fx(javafx.scene.input.Clipboard.getSystemClipboard.getHtml) shouldBe "<p><strong>Hi</strong></p>"
      fx(javafx.scene.input.Clipboard.getSystemClipboard.getString) shouldBe "Hi"
      read() shouldBe Some("Hi")
    }

    "copy Markdown as clean HTML and as prose" in {
      fx(Clipboard.copyMarkdown("**Total:** 3 < 4\n\n- one\n- [two](https://x.io)"))
      fx(javafx.scene.input.Clipboard.getSystemClipboard.getHtml) shouldBe
        "<p><strong>Total:</strong> 3 &lt; 4</p>\n<ul>\n<li>one</li>\n<li><a href=\"https://x.io\">two</a></li>\n</ul>"
      read() shouldBe Some("Total: 3 < 4\n\n- one\n- two (https://x.io)")
    }

    "have no selection to copy on the desktop" in {
      fx(Clipboard.selection()) shouldBe None
    }

    "read None where it holds no text" in {
      fx(javafx.scene.input.Clipboard.getSystemClipboard.clear())
      read() shouldBe None
    }
  }
}
