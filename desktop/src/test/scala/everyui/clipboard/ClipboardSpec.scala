package everyui.clipboard

import everyui.FxSpec

/** The clipboard on the desktop: what is copied is read back, and a clipboard with no text reads as None. Whatever the
  * machine's clipboard held before is put back after. */
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

    "read None where it holds no text" in {
      fx(javafx.scene.input.Clipboard.getSystemClipboard.clear())
      read() shouldBe None
    }
  }
}
