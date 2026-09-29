package everyui.chat

import everyui.component.{Component, TextView}
import everyui.component.types.PasteAction
import everyui.focus.Focus
import everyui.kit.Toast
import everyui.{FxSpec, ui}
import javafx.scene.control.TextInputControl
import javafx.scene.input.{ClipboardContent, DataFormat}
import reactify.*

/** Pastes into a composer on the desktop backend's real field: a long one a text file's chip, named after its first
  * line, its bytes the words' UTF-8, refused as a dropped file is where the composer's limits say; a short one
  * landing, other words, or nothing. Whatever the clipboard held before is put back after. */
class ComposerPasteSpec extends FxSpec {
  private var before: Option[java.util.Map[DataFormat, AnyRef]] = None

  override def beforeAll(): Unit = {
    super.beforeAll()
    before = Some(fx {
      val clipboard = javafx.scene.input.Clipboard.getSystemClipboard
      val kept = new java.util.HashMap[DataFormat, AnyRef]()
      clipboard.getContentTypes.forEach(t => Option(clipboard.getContent(t)).foreach(c => kept.put(t, c)))
      kept
    })
  }

  override def afterAll(): Unit = {
    before.foreach(kept => fx(javafx.scene.input.Clipboard.getSystemClipboard.setContent(kept)))
    fx(Toast.hide())
    super.afterAll()
  }

  private def make(accept: List[String] = Nil, maxSize: Option[Long] = None,
                   allow: Attachment => Boolean = _ => true): Composer = fx {
    Toast.hide()
    val c = new Composer(_ => (), accept = accept, allow = allow, maxSize = maxSize, touch = false)
    ui.children.clear()
    Focus.focused @= None
    ui.children += c
    c.focusField()
    c
  }

  // the words pasted into the composer's field, by its control's own paste
  private def paste(c: Composer, words: String): Unit = fx {
    val content = new ClipboardContent()
    content.putString(words)
    javafx.scene.input.Clipboard.getSystemClipboard.setContent(content)
    c.field.field.get.node.fx.asInstanceOf[TextInputControl].paste()
  }

  // what the toast says, if one shows
  private def toast(): Option[String] = fx {
    var found: Option[String] = None
    var pending: List[Component] = ui.childComponents.toList
    while (pending.nonEmpty) {
      pending.head match {
        case t: TextView if found.isEmpty && (t.content().endsWith(ComposerWords().tooLarge) ||
          t.content().endsWith(ComposerWords().notAccepted)) =>
          found = Some(t.content())
        case _ => ()
      }
      pending = pending.head.childComponents.toList ::: pending.tail
    }
    found
  }

  // the names on the chips under `c`
  private def chips(c: Composer): Vector[String] = fx {
    var found = Vector.empty[String]
    var pending: List[Component] = List(c)
    while (pending.nonEmpty) {
      pending.head match {
        case chip: AttachmentChip => found = found :+ chip.attachment.name
        case _ => ()
      }
      pending = pending.head.childComponents.toList ::: pending.tail
    }
    found
  }

  private val trace = "java.lang.IllegalStateException: stack trace\n" +
    (1 to 60).map(i => "\tat everyui.chat.Somewhere.call" + i + "(Somewhere.scala:" + i + ")").mkString("\n") + "\n— done"

  "A paste into a composer" should {
    "land as it is with no hook" in {
      val c = make()
      paste(c, "hi there")
      fx(c.text()) shouldBe "hi there"
    }
    "become a text file's chip where it is long, named after its first line, the field left as it was" in {
      val c = make(accept = List("image/*", ".txt"), maxSize = Some(100000L))
      fx(c.pasted @= Some(ComposerPaste.longAsFile(1500)))
      fx(c.text @= "see this: ")
      trace.length should be > 1500
      paste(c, trace)
      fx(c.text()) shouldBe "see this: "
      val attached = fx(c.attachments())
      attached.map(_.name) shouldBe List("java.lang.IllegalStateException stack.txt")
      attached.head.mime shouldBe "text/plain"
      attached.head.size shouldBe trace.getBytes("UTF-8").length.toLong
      // more bytes than characters: the dash is three
      attached.head.size shouldBe (trace.length + 2).toLong
      var read: Option[String] = None
      fx(attached.head.bytes.map(b => read = Some(new String(b, "UTF-8"))).startUnit())
      eventually(read.nonEmpty) shouldBe true
      read shouldBe Some(trace)
      chips(c) shouldBe Vector("java.lang.IllegalStateException stack.txt")
    }
    "land where it is short" in {
      val c = make()
      fx(c.pasted @= Some(ComposerPaste.longAsFile(1500)))
      paste(c, "short")
      fx(c.text()) shouldBe "short"
      fx(c.attachments()) shouldBe Nil
    }
    "put other words in its place, or nothing" in {
      val c = make()
      fx(c.pasted @= Some(w => ComposerPaste.InsertText(w.toUpperCase)))
      paste(c, "quiet")
      fx(c.text()) shouldBe "QUIET"
      fx(c.pasted @= Some(_ => ComposerPaste.Ignore))
      paste(c, "more")
      fx(c.text()) shouldBe "QUIET"
      fx(c.pasted @= Some(w => ComposerPaste.AsText(PasteAction.Insert)))
      paste(c, "!")
      fx(c.text()) shouldBe "QUIET!"
    }
    "be refused as a dropped file is where it is too large, not accepted or not allowed, landing nowhere" in {
      val big = make(maxSize = Some(1000L))
      fx(big.pasted @= Some(ComposerPaste.longAsFile(1500)))
      paste(big, trace)
      fx(big.attachments()) shouldBe Nil
      fx(big.text()) shouldBe ""
      toast().exists(_.contains(ComposerWords().tooLarge)) shouldBe true
      val pictures = make(accept = List("image/*"))
      fx(pictures.pasted @= Some(ComposerPaste.longAsFile(1500)))
      paste(pictures, trace)
      fx(pictures.attachments()) shouldBe Nil
      toast().exists(_.contains(ComposerWords().notAccepted)) shouldBe true
      val refusing = make(allow = a => !a.name.startsWith("java"))
      fx(refusing.pasted @= Some(ComposerPaste.longAsFile(1500)))
      paste(refusing, trace)
      fx(refusing.attachments()) shouldBe Nil
      paste(refusing, "Log\n" + trace)
      fx(refusing.attachments().map(_.name)) shouldBe List("Log.txt")
    }
    "take nothing while it is out of use" in {
      val c = make()
      fx(c.pasted @= Some(w => ComposerPaste.Attach(Attachment.pasted(w))))
      fx(c.enabled @= false)
      paste(c, "anything")
      fx(c.attachments()) shouldBe Nil
    }
  }

  "A pasted file's name" should {
    "be its first line with anything on it, cut short, without what a file's name cannot hold" in {
      Attachment.textName("stack trace\nmore") shouldBe "stack trace.txt"
      Attachment.textName("\n\n   \n  C:\\logs\\app.log: failed?\n") shouldBe "C logs app.log failed.txt"
      Attachment.textName("a" * 60) shouldBe ("a" * 40) + ".txt"
      Attachment.textName("the quick brown fox jumps over the lazy dog and on") shouldBe "the quick brown fox jumps over the lazy.txt"
      Attachment.textName("Done...") shouldBe "Done.txt"
      Attachment.textName("   \n\t\n") shouldBe "pasted.txt"
      Attachment.textName("") shouldBe "pasted.txt"
    }
  }
}
