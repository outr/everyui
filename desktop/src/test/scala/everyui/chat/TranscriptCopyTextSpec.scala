package everyui.chat

import everyui.clipboard.Clipboard
import everyui.component.{Component, Modal}
import everyui.focus.Focus
import everyui.font.MaterialIcon
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

/** The words a transcript copies a message as (`Transcript.copyText`), on the desktop: by default its text, copied
  * exactly as before by Copy message beside it and in its menu, by Ctrl+C and by Copy as Markdown; an app's own words
  * used by every one of them, asked as each copy is made, so a streaming message copies as far as it has come. What the
  * machine's clipboard held before is put back after. */
class TranscriptCopyTextSpec extends FxSpec {
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
  private val me = Participant("u-me", "Me")
  private val written = "**Report:** see [notes.txt](file:notes.txt)\nand [docs](https://x.io)\n\n- one\n- two"
  // the app's words: a file's link as its name alone, as the app copies it
  private val fileLink = """\[([^\]]+)\]\(file:[^)]+\)""".r
  private def asApp(m: ChatEntry.Message): String = fileLink.replaceAllIn(m.text(), "$1")

  // the clipboard's HTML and text (on the FX thread)
  private def held: (Option[String], Option[String]) = {
    val clipboard = javafx.scene.input.Clipboard.getSystemClipboard
    (Option(clipboard.getHtml), Option(clipboard.getString))
  }

  // what `copy` puts on a cleared clipboard
  private def copying(copy: () => Unit): (Option[String], Option[String]) = {
    fx(javafx.scene.input.Clipboard.getSystemClipboard.clear())
    fx(copy())
    fx(held)
  }

  private def menuCopy(t: Transcript, m: ChatEntry.Message): () => Unit = fx {
    val items = t.menu()(m)
    items.map(_.label) shouldBe List(ChatWords.copyMessage)
    items.head.action
  }

  private def ctrlC(): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "c", "c", KeyCode.C, false, true, false,
      false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "c", "c", KeyCode.C, false, true, false,
      false))
  }

  private def onScreen[T](c: Component)(test: => T): T = {
    fx {
      ui.children.clear()
      ui.children += c
    }
    try test finally fx {
      Modal.close()
      ui.children.clear()
      c.dispose()
      Focus.focused @= None
    }
  }

  private def transcript(entries: Var[Vector[ChatEntry]]): Transcript = fx {
    val t = new Transcript(entries)
    t.size.width @= 600.0
    t.size.height @= 400.0
    t
  }

  "A transcript's copied words" should {
    "be a message's text by default, copied exactly as before by every path" in {
      val m = ChatEntry.message("m0", me, self = true, written)
      val t = transcript(Var(Vector[ChatEntry](m)))
      fx(t.copiedText(m)) shouldBe written
      val asBefore = copying(() => Clipboard.copyMarkdown(written, breaks = true))
      asBefore._1.isDefined shouldBe true
      copying(() => MessageAction.copy(m)) shouldBe asBefore
      copying(MessageAction.copyText(m).action) shouldBe asBefore
      val action = fx(t.actionsFor(m))
      action.map(a => (a.icon, a.label, a.done)) shouldBe List((MaterialIcon.ContentCopy, fx(ChatWords.copyMessage),
        fx(ChatWords.copied)))
      copying(action.head.action) shouldBe asBefore
      copying(fx(t.messageActions()(m)).head.action) shouldBe asBefore
      copying(menuCopy(t, m)) shouldBe asBefore
      copying(MessageAction.copyMarkdownSource(m).action) shouldBe ((None, Some(written)))
      copying(fx(MessageAction.copyMarkdownSource(m, t.copiedText)).action) shouldBe ((None, Some(written)))
      onScreen(t) {
        (1 to 5).foreach(_ => fx(t.list.refresh()))
        fx(t.focus())
        fx(t.list.current @= Some("m0"))
        fx(javafx.scene.input.Clipboard.getSystemClipboard.clear())
        ctrlC()
        eventually(held == asBefore) shouldBe true
      }
    }

    "be the app's words wherever a message is copied whole, asked as each copy is made" in {
      val m = ChatEntry.message("m0", ada, self = false, written)
      val t = transcript(Var(Vector[ChatEntry](m)))
      // made before the app gives its words: still the app's, as they are asked when pressed
      val early = fx(t.actionsFor(m)).head.action
      val earlyMenu = menuCopy(t, m)
      fx(t.copyText @= asApp)
      val apps = "**Report:** see notes.txt\nand [docs](https://x.io)\n\n- one\n- two"
      fx(t.copiedText(m)) shouldBe apps
      val expected = copying(() => Clipboard.copyMarkdown(apps, breaks = true))
      expected._1.exists(_.contains("file:")) shouldBe false
      expected._2 shouldBe Some("Report: see notes.txt\nand docs (https://x.io)\n\n- one\n- two")
      copying(early) shouldBe expected
      copying(earlyMenu) shouldBe expected
      copying(fx(t.actionsFor(m)).head.action) shouldBe expected
      copying(menuCopy(t, m)) shouldBe expected
      copying(fx(MessageAction.copyMarkdownSource(m, t.copiedText)).action) shouldBe ((None, Some(apps)))
      onScreen(t) {
        (1 to 5).foreach(_ => fx(t.list.refresh()))
        fx(t.focus())
        fx(t.list.current @= Some("m0"))
        fx(javafx.scene.input.Clipboard.getSystemClipboard.clear())
        ctrlC()
        eventually(held == expected) shouldBe true
      }
      // given back, the text again
      fx(t.copyText @= ((m: ChatEntry.Message) => m.text()))
      copying(early) shouldBe copying(() => Clipboard.copyMarkdown(written, breaks = true))
    }

    "copy a streaming message as far as it has come" in {
      val m = ChatEntry.message("m0", ada, self = false, "Reading [a.txt](file:a.txt)")
      val t = transcript(Var(Vector[ChatEntry](m)))
      fx(t.copyText @= asApp)
      val action = fx(t.actionsFor(m)).head.action
      val source = fx(MessageAction.copyMarkdownSource(m, t.copiedText)).action
      copying(action) shouldBe copying(() => Clipboard.copyMarkdown("Reading a.txt", breaks = true))
      fx(m.text @= "Reading [a.txt](file:a.txt) and [b.txt](file:b.txt), *done*")
      copying(action) shouldBe copying(() => Clipboard.copyMarkdown("Reading a.txt and b.txt, *done*", breaks = true))
      copying(source) shouldBe ((None, Some("Reading a.txt and b.txt, *done*")))
      onScreen(t) {
        (1 to 5).foreach(_ => fx(t.list.refresh()))
        fx(t.focus())
        fx(t.list.current @= Some("m0"))
        fx(m.text @= "Reading [a.txt](file:a.txt) and [c.txt](file:c.txt)")
        fx(javafx.scene.input.Clipboard.getSystemClipboard.clear())
        ctrlC()
        eventually(held._2.contains("Reading a.txt and c.txt")) shouldBe true
      }
    }
  }
}
