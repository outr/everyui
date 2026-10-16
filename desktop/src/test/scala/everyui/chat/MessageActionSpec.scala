package everyui.chat

import everyui.FxSpec
import everyui.font.MaterialIcon

/** A message's own copy actions on the desktop: Copy message puts its words on the clipboard as they read, clean HTML
  * and plain prose with its lines kept, and Copy as Markdown its text as it was written, text alone. What the machine's
  * clipboard held before is put back after. */
class MessageActionSpec extends FxSpec {
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

  private val ada = Participant("u-ada", "Ada")
  private val reply = "**Order:** #4821\n**Status:** shipped\n\n- refund sent\n- see [help](https://x.io)"

  // (on the FX thread)
  private def html: Option[String] = Option(javafx.scene.input.Clipboard.getSystemClipboard.getHtml)
  private def text: Option[String] = Option(javafx.scene.input.Clipboard.getSystemClipboard.getString)

  "A message's actions" should {
    "copy the message as it reads, as HTML and as prose" in {
      val m = ChatEntry.message("m0", ada, self = false, reply)
      val action = fx(MessageAction.copyText(m))
      action.icon shouldBe MaterialIcon.ContentCopy
      action.label shouldBe fx(ChatWords.copyMessage)
      action.done shouldBe fx(ChatWords.copied)
      fx(action.action())
      fx(html) shouldBe Some("<p><strong>Order:</strong> #4821<br>\n<strong>Status:</strong> shipped</p>\n<ul>\n" +
        "<li>refund sent</li>\n<li>see <a href=\"https://x.io\">help</a></li>\n</ul>")
      fx(text) shouldBe Some("Order: #4821\nStatus: shipped\n\n- refund sent\n- see help (https://x.io)")
    }

    "copy the message's Markdown as it was written" in {
      val m = ChatEntry.message("m0", ada, self = false, reply)
      val action = fx(MessageAction.copyMarkdownSource(m))
      action.icon shouldBe MaterialIcon.Code
      action.label shouldBe fx(ChatWords.copyMarkdown)
      everyui.i18n.Words.languages("en")("chat.copyMarkdown") shouldBe "Copy as Markdown"
      action.done shouldBe fx(ChatWords.copied)
      fx(action.action())
      fx(text) shouldBe Some(reply)
      fx(html) shouldBe None
    }

    "follow the message's words as they change" in {
      val m = ChatEntry.message("m0", ada, self = false, "first")
      val action = fx(MessageAction.copyText(m))
      fx(m.text @= "*second*")
      fx(action.action())
      fx(text) shouldBe Some("second")
      fx(html) shouldBe Some("<p><em>second</em></p>")
    }
  }
}
