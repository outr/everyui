package everyui.chat

import everyui.component.{Component, InputView, TextView}
import everyui.file.PickedFile
import everyui.focus.Focus
import everyui.kit.Toast
import everyui.{FxSpec, ui}
import javafx.scene.control.TextInputControl
import javafx.scene.image.WritableImage
import javafx.scene.input.{ClipboardContent, DataFormat}
import javafx.scene.paint.Color
import reactify.*

import scala.jdk.CollectionConverters.*

/** Pictures and files pasted into a composer, and into a plain field, on the desktop backend's real field, with the
  * clipboard (Xvfb's) set as a screenshot tool or a file manager would set it: a picture a chip named
  * "pasted-image-1.png", a PNG, its number counting on; words beside it landing as words; a file by its own name, its
  * path not landing as words; the app's `pastedFiles` taking another in its place, or none; the composer's size limit
  * refusing one as a dropped file is; and a plain field's hook hearing them, none taken without one. Whatever the
  * clipboard held before is put back after. */
class ComposerPasteFilesSpec extends FxSpec {
  private var before: Option[java.util.Map[DataFormat, AnyRef]] = None
  private val signature = List(0x89, 0x50, 0x4e, 0x47).map(_.toByte)

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

  private def make(maxSize: Option[Long] = None): Composer = fx {
    Toast.hide()
    val c = new Composer(_ => (), maxSize = maxSize, touch = false)
    ui.children.clear()
    Focus.focused @= None
    ui.children += c
    c.focusField()
    c
  }

  // a picture `width` by `height` of noise (which a PNG cannot make much smaller) on the clipboard, with `words`
  // beside it where there are any
  private def copyPicture(width: Int, height: Int, words: String = ""): Unit = fx {
    val image = new WritableImage(width, height)
    val noise = new scala.util.Random(width * 31 + height)
    for (x <- 0 until width; y <- 0 until height) image.getPixelWriter.setColor(x, y,
      Color.rgb(noise.nextInt(256), noise.nextInt(256), noise.nextInt(256)))
    val content = new ClipboardContent()
    content.putImage(image)
    if (words.nonEmpty) content.putString(words)
    javafx.scene.input.Clipboard.getSystemClipboard.setContent(content)
  }

  // a file on the clipboard as a file manager copies one: the file, and its path as words
  private def copyFile(file: java.io.File): Unit = fx {
    val content = new ClipboardContent()
    content.putFiles(List(file).asJava)
    content.putString(file.getAbsolutePath)
    javafx.scene.input.Clipboard.getSystemClipboard.setContent(content)
  }

  private def paste(control: TextInputControl): Unit = fx(control.paste())

  private def control(c: Composer): TextInputControl = fx(c.field.field.get.node.fx.asInstanceOf[TextInputControl])

  // the bytes `task` reads
  private def read(task: everyui.async.Task[Array[Byte]]): Array[Byte] = {
    var got: Option[Array[Byte]] = None
    fx(task.map(b => got = Some(b)).startUnit())
    eventually(got.nonEmpty) shouldBe true
    got.get
  }

  // the names on the chips under `c`, on the FX thread
  private def chips(c: Composer): Vector[String] = {
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

  // what the toast says, if one shows, on the FX thread
  private def toast(): Option[String] = {
    var found: Option[String] = None
    var pending: List[Component] = ui.childComponents.toList
    while (pending.nonEmpty) {
      pending.head match {
        case t: TextView if found.isEmpty && t.content().endsWith(ComposerWords().tooLarge) => found = Some(t.content())
        case _ => ()
      }
      pending = pending.head.childComponents.toList ::: pending.tail
    }
    found
  }

  "A picture pasted into a composer" should {
    "go with the message as a chip named pasted-image-1.png, a PNG, the next one numbered on" in {
      val c = make()
      copyPicture(4, 3)
      paste(control(c))
      eventually(c.attachments().nonEmpty) shouldBe true
      val first = fx(c.attachments()).head
      first.name shouldBe "pasted-image-1.png"
      first.mime shouldBe "image/png"
      first.image shouldBe true
      first.preview.nonEmpty shouldBe true
      val bytes = read(first.bytes)
      bytes.length.toLong shouldBe first.size
      bytes.take(4).toList shouldBe signature
      val decoded = fx(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes)))
      (decoded.getWidth, decoded.getHeight) shouldBe (4.0, 3.0)
      fx(c.text()) shouldBe ""
      eventually(chips(c) == Vector("pasted-image-1.png")) shouldBe true
      paste(control(c))
      eventually(c.attachments().length == 2) shouldBe true
      fx(c.attachments().map(_.name)) shouldBe List("pasted-image-1.png", "pasted-image-2.png")
    }
    "leave the words beside it to land as words" in {
      val c = make()
      fx(c.text @= "look: ")
      copyPicture(2, 2, "the caption")
      paste(control(c))
      fx(c.text()) shouldBe "look: the caption"
      eventually(c.attachments().nonEmpty) shouldBe true
      fx(c.attachments().map(_.name)) shouldBe List("pasted-image-1.png")
    }
    "be what the app's pastedFiles makes it, or nothing where it refuses it or takes none" in {
      val c = make()
      var asked = List.empty[String]
      fx(c.pastedFiles @= Some(a => {
        asked = asked :+ a.name
        Some(a.copy(name = "screenshot.png"))
      }))
      copyPicture(2, 2)
      paste(control(c))
      eventually(c.attachments().nonEmpty) shouldBe true
      asked shouldBe List("pasted-image-1.png")
      fx(c.attachments().map(_.name)) shouldBe List("screenshot.png")
      fx(c.pastedFiles @= Some(a => {
        asked = asked :+ a.name
        None
      }))
      paste(control(c))
      eventually(asked.length == 2) shouldBe true
      fx(c.attachments().map(_.name)) shouldBe List("screenshot.png")
      // where files cannot go, none is asked about, and nothing lands
      fx(c.attachable @= false)
      paste(control(c))
      fx(())
      asked.length shouldBe 2
      fx(c.text()) shouldBe ""
      fx(c.attachments().map(_.name)) shouldBe List("screenshot.png")
    }
    "be refused as a dropped file is where it is larger than maxSize" in {
      val c = make(maxSize = Some(200L))
      copyPicture(64, 64)
      paste(control(c))
      eventually(toast().nonEmpty) shouldBe true
      fx(c.attachments()) shouldBe Nil
      copyPicture(1, 1)
      paste(control(c))
      eventually(c.attachments().nonEmpty) shouldBe true
      fx(c.attachments().head.size) should be <= 200L
    }
  }

  "A file pasted into a composer" should {
    "go by its own name, its path not landing as words" in {
      val dir = java.nio.file.Files.createTempDirectory("everyui-paste")
      val file = dir.resolve("notes.txt").toFile
      java.nio.file.Files.write(file.toPath, "some notes".getBytes("UTF-8"))
      try {
        val c = make()
        copyFile(file)
        paste(control(c))
        eventually(c.attachments().nonEmpty) shouldBe true
        val a = fx(c.attachments()).head
        a.name shouldBe "notes.txt"
        a.mime shouldBe "text/plain"
        new String(read(a.bytes), "UTF-8") shouldBe "some notes"
        fx(c.text()) shouldBe ""
      } finally {
        file.delete()
        dir.toFile.delete()
      }
    }
  }

  "A plain field's pastedFiles" should {
    def field(): (InputView, TextInputControl) = fx {
      ui.children.clear()
      Focus.focused @= None
      val input = new InputView()
      input.size.width @= 300.0
      input.size.height @= 24.0
      ui.children += input
      (input, input.node.fx.asInstanceOf[TextInputControl])
    }
    "hear each picture pasted, named and numbered, words beside it landing" in {
      val (input, control) = field()
      var heard = List.empty[PickedFile]
      fx(input.pastedFiles @= Some(f => heard = heard :+ f))
      copyPicture(3, 3, "words")
      paste(control)
      eventually(heard.nonEmpty) shouldBe true
      heard.map(f => (f.name, f.mime)) shouldBe List("pasted-image-1.png" -> "image/png")
      read(heard.head.bytes).take(4).toList shouldBe signature
      fx(input.text()) shouldBe "words"
      copyPicture(3, 3)
      paste(control)
      eventually(heard.length == 2) shouldBe true
      heard.last.name shouldBe "pasted-image-2.png"
      fx(input.text()) shouldBe "words"
    }
    "take none without a hook, nor where the field is read-only" in {
      val (input, control) = field()
      copyPicture(3, 3, "words")
      paste(control)
      fx(input.text()) shouldBe "words"
      var heard = List.empty[PickedFile]
      fx {
        input.pastedFiles @= Some(f => heard = heard :+ f)
        input.readOnly @= true
      }
      paste(control)
      fx(())
      heard shouldBe Nil
      fx(input.text()) shouldBe "words"
    }
  }
}
