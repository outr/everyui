package everyui.chat

import everyui.component.{Component, Container, ImageView, Modal}
import everyui.component.types.Semantics
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.kit.ImageViewer
import everyui.{FxSpec, ui}
import javafx.scene.input.{KeyCode, KeyEvent}
import reactify.*

import java.io.File

/** A file's chip in a composer pressed (`Composer.onAttachmentOpen`), on the desktop's real nodes: a picture shown
  * whole over the screen, closed with the focus and the draft as they were; the × taking it off without opening it; the
  * app's hook in place of the viewer; a file that is not a picture no button without the hook; Enter and Space; the
  * names a screen reader hears; Right and Left between the chip and its ×. And the viewer's zoom: a double click, the
  * wheel with Ctrl, a drag moving the zoomed picture about without closing it, and a click outside closing it. */
class ComposerPreviewSpec extends FxSpec {
  private var sent: List[Draft] = Nil

  private def make(): Composer = fx {
    sent = Nil
    while (Modal.showing) Modal.close()
    val c = new Composer(d => sent = sent :+ d, touch = false)
    ui.children.clear()
    Focus.focused @= None
    ui.children += c
    c.focusField()
    c
  }

  /** A picture of one colour, `w` by `h`, as a file's URL. */
  private def picture(name: String, w: Int, h: Int): String = {
    val dir = new File("target/composerpreview")
    dir.mkdirs()
    val file = new File(dir, name + ".png")
    val image = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
    (0 until w).foreach(x => (0 until h).foreach(y => image.setRGB(x, y, 0x3366cc)))
    javax.imageio.ImageIO.write(image, "png", file)
    "file://" + file.getAbsolutePath
  }

  private def photo: Attachment = Attachment("photo.png", 2048L, "image/png",
    everyui.async.Task(Array.emptyByteArray), Some(picture("photo", 400, 300)))
  private def notes: Attachment = Attachment("notes.txt", 12L, "text/plain", everyui.async.Task(Array.emptyByteArray))

  private def press(code: KeyCode, text: String = ""): Unit = fx {
    List(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED).foreach(kind =>
      javafx.event.Event.fireEvent(window, new KeyEvent(kind, text, text, code, false, false, false, false)))
  }

  // a click at `x`, `y` of the root, each a little apart from the last (one the same as a consumed one is held off)
  private var nudge = 0.0
  private def clickAt(c: Component, x: Double, y: Double): Unit = fx {
    nudge += 0.01
    c.event.click @= PointerEvent(PointerKind.Click, x + nudge, y)
  }
  private def click(c: Component): Unit = clickAt(c, c.absoluteLeft + 2.0, c.absoluteTop + 2.0)

  // (on the FX thread)
  private def chips(c: Composer): Vector[AttachmentChip] = {
    var found = Vector.empty[AttachmentChip]
    var pending = List[Component](c)
    while (pending.nonEmpty) {
      val at = pending.head
      pending = at.childComponents.toList ::: pending.tail
      at match {
        case x: AttachmentChip => found = found :+ x
        case _ => ()
      }
    }
    found
  }

  // the picture the viewer on top shows (the second of its pictures: the first is the one sliding out)
  private def shown(): Option[ImageView] =
    Modal.top.flatMap(_.childComponents.collect { case v: ImageView => v }.drop(1).headOption)

  "A file's chip in a composer" should {
    "show a picture whole when pressed, and close back to the field with the draft as it was" in {
      val c = make()
      fx {
        c.text @= "Look at this"
        c.attach(photo)
      }
      val chip = fx(chips(c).head)
      fx(c.field.focus())
      eventually(Focus.focused().contains(c.field)) shouldBe true
      click(chip.body)
      fx(Modal.showing) shouldBe true
      eventually(shown().exists(_.size.width() > 0.0)) shouldBe true
      // no download button for a file of one's own
      fx(Modal.top.get.childComponents.exists(_.isInstanceOf[everyui.component.IconButton])) shouldBe false
      press(KeyCode.ESCAPE)
      fx(Modal.showing) shouldBe false
      eventually(Focus.focused().contains(c.field)) shouldBe true
      fx(c.text()) shouldBe "Look at this"
      fx(c.attachments().map(_.name)) shouldBe List("photo.png")
      sent shouldBe Nil
    }

    "give the focus back to the chip that opened it" in {
      val c = make()
      fx(c.attach(photo))
      val chip = fx(chips(c).head)
      fx(chip.body.focus())
      press(KeyCode.ENTER)
      eventually(Modal.showing) shouldBe true
      press(KeyCode.ESCAPE)
      fx(Modal.showing) shouldBe false
      eventually(Focus.focused().exists(_ eq chip.body)) shouldBe true
      // Space opens it too
      press(KeyCode.SPACE, " ")
      eventually(Modal.showing) shouldBe true
      fx(Modal.close())
      eventually(Focus.focused().exists(_ eq chip.body)) shouldBe true
    }

    "take it off by its × without opening it" in {
      val c = make()
      fx(c.attach(photo))
      click(fx(chips(c).head.removal))
      fx(Modal.showing) shouldBe false
      fx(c.attachments()) shouldBe Nil
    }

    "hand every file to the app's hook in place of the viewer" in {
      val c = make()
      var opened = List.empty[String]
      fx {
        c.attach(photo)
        c.attach(notes)
      }
      fx(chips(c).map(_.opens)) shouldBe Vector(true, false)
      fx(c.onAttachmentOpen @= Some((a: Attachment) => opened = opened :+ a.name))
      val Vector(first, second) = fx(chips(c))
      fx(Vector(first.opens, second.opens)) shouldBe Vector(true, true)
      click(first.body)
      click(second.body)
      opened shouldBe List("photo.png", "notes.txt")
      fx(Modal.showing) shouldBe false
      fx(second.body.semantics()) shouldBe Semantics.button("Preview notes.txt")
      // and the viewer again once it is unset
      fx(c.onAttachmentOpen @= None)
      fx(chips(c).map(_.opens)) shouldBe Vector(true, false)
    }

    "be no button for a file that is not a picture, without the hook" in {
      val c = make()
      fx(c.attach(notes))
      val chip = fx(chips(c).head)
      fx(chip.opens) shouldBe false
      fx(chip.body.focusable()) shouldBe false
      fx(chip.body.semantics()) shouldBe Semantics()
      fx(chip.body.cursor()) should not be "pointer"
      click(chip.body)
      fx(Modal.showing) shouldBe false
      // its × is still there to take it off
      fx(chip.removal.semantics()) shouldBe Semantics.button("Remove notes.txt")
    }

    "be named for a screen reader, and go to its × and back by Right and Left" in {
      val c = make()
      fx(c.attach(photo))
      val chip = fx(chips(c).head)
      fx(chip.body.semantics()) shouldBe Semantics.button("Preview photo.png")
      fx(chip.removal.semantics()) shouldBe Semantics.button("Remove photo.png")
      eventually(Focus.focused().contains(c.field)) shouldBe true
      press(KeyCode.UP)
      eventually(Focus.focused().exists(_ eq chip.body)) shouldBe true
      press(KeyCode.RIGHT)
      eventually(Focus.focused().exists(_ eq chip.removal)) shouldBe true
      press(KeyCode.LEFT)
      eventually(Focus.focused().exists(_ eq chip.body)) shouldBe true
      press(KeyCode.DOWN)
      eventually(Focus.focused().contains(c.field)) shouldBe true
    }
  }

  "An image viewer" should {
    "zoom by a double click and the wheel, move about zoomed, and close by a click outside" in {
      fx {
        while (Modal.showing) Modal.close()
        ui.children.clear()
      }
      var closed = 0
      val viewer = fx(new ImageViewer(List(picture("wide", 400, 300)), 0, () => closed += 1))
      fx(viewer.open())
      eventually(shown().exists(_.size.width() > 0.0)) shouldBe true
      val panel = fx(Modal.top.get)
      val view = fx(shown().get)
      val fitted = fx(view.size.width())
      def middle: (Double, Double) = fx((view.absoluteLeft + view.size.width() / 2.0,
        view.absoluteTop + view.size.height() / 2.0))
      // two clicks on the picture: zoomed, about where they were; two more: whole again
      val (mx, my) = middle
      clickAt(panel, mx, my)
      fx(viewer.zoom()) shouldBe 1.0
      clickAt(panel, mx, my)
      fx(viewer.zoom()) shouldBe ImageViewer.doubled
      fx(view.size.width()) shouldBe (fitted * ImageViewer.doubled +- 0.5)
      clickAt(panel, mx, my)
      clickAt(panel, mx, my)
      fx(viewer.zoom()) shouldBe 1.0
      // the wheel with Ctrl zooms, held to the deepest
      fx(panel.event.pointer.wheel @= PointerEvent(PointerKind.Wheel, 0.0, -250.0, ctrl = true))
      fx(viewer.zoom()) shouldBe (2.0 +- 0.001)
      (1 to 5).foreach(i =>
        fx(panel.event.pointer.wheel @= PointerEvent(PointerKind.Wheel, 0.0, -250.0 - i, ctrl = true)))
      fx(viewer.zoom()) shouldBe ImageViewer.deepest
      // a drag moves it, never past its edge, and is no click
      val left = fx(view.position.left())
      fx(panel.event.pointer.down @= PointerEvent(PointerKind.Down, mx, my))
      fx(ui.event.pointer.move @= PointerEvent(PointerKind.Move, mx + 20.0, my))
      fx(view.position.left()) shouldBe (left + 20.0 +- 0.5)
      fx(ui.event.pointer.move @= PointerEvent(PointerKind.Move, mx + 100000.0, my))
      fx(view.position.left()) shouldBe (0.0 +- 0.5)
      fx(ui.event.pointer.up @= PointerEvent(PointerKind.Up, mx + 100000.0, my))
      clickAt(panel, mx + 100000.0, my)
      fx(Modal.showing) shouldBe true
      // zoomed out whole, a click on the black beside it closes it
      fx(panel.event.pointer.wheel @= PointerEvent(PointerKind.Wheel, 0.0, 5000.0, ctrl = true))
      fx(viewer.zoom()) shouldBe 1.0
      clickAt(panel, fx(panel.absoluteLeft) + 2.0, fx(panel.absoluteTop) + 2.0)
      fx(Modal.showing) shouldBe false
      closed shouldBe 1
    }
  }
}
