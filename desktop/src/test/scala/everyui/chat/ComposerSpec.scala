package everyui.chat

import com.sun.javafx.scene.input.DragboardHelper
import com.sun.javafx.tk.TKClipboard
import everyui.async.Task
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.{FxSpec, ui}
import javafx.scene.input.{DataFormat, DragEvent, KeyCode, KeyEvent, TransferMode}
import javafx.util.Pair
import reactify.*

import java.io.File
import scala.jdk.CollectionConverters.*

/** A composer on the desktop backend's real nodes, with real JavaFX key events and files dropped as the window hands
  * them over: a suggestion taken and put away, Enter sending and held, completions put in place of the word, and files
  * dropped becoming chips (the file dialog itself cannot be driven here). */
class ComposerSpec extends FxSpec {
  private var sent: List[Draft] = Nil
  private var stops = 0
  private var accepted: List[String] = Nil

  private def make(): Composer = fx {
    sent = Nil
    stops = 0
    accepted = Nil
    val c = new Composer(d => sent = sent :+ d, onStop = () => stops += 1, onAcceptSuggestion = s => accepted = accepted :+ s,
      complete = Some((t, p) => Task(Vector("grace", "greta", "ada").filter(_.startsWith(p)).map(n => Completion(n, t.toString + n)))),
      triggers = List(CompletionTrigger('@')), accept = List("image/*", ".txt"), maxSize = Some(1000L), touch = false)
    ui.children.clear()
    Focus.focused @= None
    ui.children += c
    c.focusField()
    c
  }

  private def press(code: KeyCode, shift: Boolean = false, ctrl: Boolean = false): Unit = fx {
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, ctrl, false, false))
    javafx.event.Event.fireEvent(window, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, shift, ctrl, false, false))
  }

  // every `T` under `c`, in tree order
  private def under[T](c: everyui.component.Component)(using t: scala.reflect.ClassTag[T]): Vector[T] = {
    var found = Vector.empty[T]
    var pending = List(c)
    while (pending.nonEmpty) {
      val at = pending.head
      pending = at.childComponents.toList ::: pending.tail
      at match {
        case x: T => found = found :+ x
        case _ => ()
      }
    }
    found
  }

  "A suggestion" should {
    "show in the empty field and be taken by Tab, the caret after it" in {
      val c = make()
      fx(c.suggestion @= Some("Tell me more"))
      fx(c.suggesting()) shouldBe true
      fx(c.field.hint()) shouldBe ""
      press(KeyCode.TAB)
      fx(c.text()) shouldBe "Tell me more"
      accepted shouldBe List("Tell me more")
      eventually(c.field.field.get.caret == 12) shouldBe true
      fx(Focus.focused()) shouldBe Some(c.field)
    }
    "be put away by typing, for that turn, and come back with the next" in {
      val c = make()
      fx(c.suggestion @= Some("Tell me more"))
      fx(c.text @= "x")
      fx(c.suggesting()) shouldBe false
      fx(c.text @= "")
      fx(c.suggesting()) shouldBe false
      fx(c.field.hint()) shouldBe "Message"
      fx(c.suggestion @= Some("And then?"))
      fx(c.suggesting()) shouldBe true
      press(KeyCode.ESCAPE)
      fx(c.suggesting()) shouldBe false
      fx(Focus.focused()) shouldBe Some(c.field)
    }
    "leave Tab to move the focus when there is none" in {
      val c = make()
      press(KeyCode.TAB)
      fx(Focus.focused().exists(_ eq c.field)) shouldBe false
    }
  }

  "Enter" should {
    "send and clear, Shift+Enter not" in {
      val c = make()
      fx(c.text @= "Hello")
      press(KeyCode.ENTER, shift = true)
      sent shouldBe Nil
      press(KeyCode.ENTER)
      sent.map(_.text) shouldBe List("Hello")
      fx(c.text()) shouldBe ""
    }
    "do nothing while a reply comes, and Send be Stop" in {
      val c = make()
      fx {
        c.busy @= true
        c.text @= "Next"
      }
      press(KeyCode.ENTER)
      sent shouldBe Nil
      fx(c.text()) shouldBe "Next"
      fx(under[SendButton](c).foreach(_.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0)))
      stops shouldBe 1
    }
    "do nothing while it is out of use, the field taking no typing" in {
      val c = make()
      fx {
        c.text @= "Later"
        c.enabled @= false
      }
      press(KeyCode.ENTER)
      sent shouldBe Nil
      fx(c.field.field.get.node.fx.asInstanceOf[javafx.scene.control.TextArea].isEditable) shouldBe false
      fx(Focus.focused()) shouldBe Some(c.field)
      fx(c.enabled @= true)
      fx(c.field.field.get.node.fx.asInstanceOf[javafx.scene.control.TextArea].isEditable) shouldBe true
    }
  }

  "Completions" should {
    "be offered for the word at the caret and put in its place" in {
      val c = make()
      fx(c.text @= "hi @g")
      eventually(ui.children().exists(_.isInstanceOf[CompletionPopup])) shouldBe true
      fx(ui.children().collect { case p: CompletionPopup => p.completions.map(_.label) }) shouldBe Vector(Vector("grace", "greta"))
      press(KeyCode.DOWN)
      press(KeyCode.ENTER)
      fx(c.text()) shouldBe "hi @greta "
      sent shouldBe Nil
      fx(ui.children().exists(_.isInstanceOf[CompletionPopup])) shouldBe false
      eventually(c.field.field.get.caret == 10) shouldBe true
    }
    "close on Escape, leaving the word" in {
      val c = make()
      fx(c.text @= "@a")
      eventually(ui.children().exists(_.isInstanceOf[CompletionPopup])) shouldBe true
      press(KeyCode.ESCAPE)
      fx(ui.children().exists(_.isInstanceOf[CompletionPopup])) shouldBe false
      fx(c.text()) shouldBe "@a"
    }
  }

  "Files dropped on it" should {
    "be chips, those not taken refused, and each taken off again" in {
      val c = make()
      val dir = java.nio.file.Files.createTempDirectory("composer")
      def file(name: String, bytes: Int): File = {
        val f = dir.resolve(name).toFile
        java.nio.file.Files.write(f.toPath, Array.fill[Byte](bytes)(65))
        f
      }
      val files = List(file("a.png", 10), file("notes.txt", 20), file("big.png", 5000), file("tool.exe", 10))
      val board = fx(DragboardHelper.createDragboard(new ComposerSpec.Board(files)))
      val target = c.node.fx
      def drag(kind: javafx.event.EventType[DragEvent]): DragEvent =
        new DragEvent(kind, board, 10.0, 10.0, 10.0, 10.0, TransferMode.COPY, null, null, null)
      fx(javafx.event.Event.fireEvent(target, drag(DragEvent.DRAG_ENTERED)))
      fx(c.children().head.asInstanceOf[everyui.component.Container].children().length) shouldBe 5
      fx(javafx.event.Event.fireEvent(target, drag(DragEvent.DRAG_OVER)))
      fx(javafx.event.Event.fireEvent(target, drag(DragEvent.DRAG_DROPPED)))
      fx(c.attachments().map(_.name)) shouldBe List("a.png", "notes.txt")
      fx(c.attachments().map(_.mime)) shouldBe List("image/png", "text/plain")
      val chips = fx(under[AttachmentChip](c))
      chips.map(_.attachment.name) shouldBe Vector("a.png", "notes.txt")
      // the bytes, read off the drawing thread
      var read: Option[Int] = None
      fx(c.attachments()(1).bytes.map(b => read = Some(b.length)).startUnit())
      eventually(read.contains(20)) shouldBe true
      fx(chips.head.removal.event.click @= PointerEvent(PointerKind.Click, 0.0, 0.0))
      fx(c.attachments().map(_.name)) shouldBe List("notes.txt")
      fx(c.sendable()) shouldBe true
      press(KeyCode.ENTER)
      sent.map(_.attachments.map(_.name)) shouldBe List(List("notes.txt"))
      fx(c.attachments()) shouldBe Nil
    }
  }
}

object ComposerSpec {
  /** What the window's drag and drop hands over: files, and nothing else. */
  class Board(files: List[File]) extends TKClipboard {
    override def getContentTypes: java.util.Set[DataFormat] = java.util.Set.of(DataFormat.FILES)
    override def putContent(content: Pair[DataFormat, AnyRef]*): Boolean = false
    override def getContent(format: DataFormat): AnyRef = if (format == DataFormat.FILES) files.asJava else null
    override def hasContent(format: DataFormat): Boolean = format == DataFormat.FILES
    override def getTransferModes: java.util.Set[TransferMode] = java.util.Set.of(TransferMode.COPY)
    override def setDragView(image: javafx.scene.image.Image): Unit = ()
    override def setDragViewOffsetX(offset: Double): Unit = ()
    override def setDragViewOffsetY(offset: Double): Unit = ()
    override def getDragView: javafx.scene.image.Image = null
    override def getDragViewOffsetX: Double = 0.0
    override def getDragViewOffsetY: Double = 0.0
  }
}
