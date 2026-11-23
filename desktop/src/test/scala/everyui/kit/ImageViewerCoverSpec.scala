package everyui.kit

import everyui.component.{Container, Modal}
import everyui.theme.Theme
import everyui.{Color, FxSpec, ui}

import java.io.File

/** What goes over the whole screen covers all of it, to its edges: the image viewer's black (none of the app beside
  * it), and a dialog's dimmed sheet; and the window outside the app's screen (a phone's bars) is painted as they cover
  * it, the theme's background again once they have closed. */
class ImageViewerCoverSpec extends FxSpec {
  private def picture(): String = {
    val dir = new File("target/imageviewercover")
    dir.mkdirs()
    val file = new File(dir, "large.png")
    val image = new java.awt.image.BufferedImage(4000, 3000, java.awt.image.BufferedImage.TYPE_INT_RGB)
    javax.imageio.ImageIO.write(image, "png", file)
    "file://" + file.getAbsolutePath
  }

  private def clear(): Unit = fx {
    while (Modal.showing) Modal.close()
    ui.children.clear()
  }

  "An image viewer" should {
    "cover the whole screen in black, its edges painted black too" in {
      clear()
      fx(Modal.edgesPainted) shouldBe ""
      fx(new ImageViewer(List(picture()), 0, saves = false).open())
      val panel = fx(Modal.top.get)
      val sheet = fx(panel.parent().get)
      fx((sheet.absoluteLeft, sheet.absoluteTop, sheet.size.width(), sheet.size.height())) shouldBe
        fx((0.0, 0.0, ui.size.width(), ui.size.height()))
      fx((panel.absoluteLeft, panel.absoluteTop, panel.size.width(), panel.size.height())) shouldBe
        fx((0.0, 0.0, ui.size.width(), ui.size.height()))
      fx(panel.backgroundColor()) shouldBe Color.Black
      fx(sheet.backgroundColor()) shouldBe Color.Black
      fx(Modal.edgesPainted) shouldBe "#000000"
      // the picture fitted to the whole screen, not to a margin inside it
      eventually(panel.childComponents.exists(c => c.isInstanceOf[everyui.component.ImageView] &&
        (math.abs(c.size.width() - ui.size.width()) < 0.5 || math.abs(c.size.height() - ui.size.height()) < 0.5)))
        .shouldBe(true)
      fx(Modal.close())
      fx(Modal.edgesPainted) shouldBe ""
    }
  }

  "A dialog's sheet" should {
    "dim the whole screen, and the window's edges as much" in {
      clear()
      val panel = fx(new Container {
        size.width @= 200.0
        size.height @= 100.0
        focusable @= true
      })
      fx(Modal.over(panel, panel))
      val sheet = fx(panel.parent().get)
      fx((sheet.absoluteLeft, sheet.absoluteTop, sheet.size.width(), sheet.size.height())) shouldBe
        fx((0.0, 0.0, ui.size.width(), ui.size.height()))
      val dimmed = fx {
        val c = Theme.backdrop()
        Theme.background().blend(c.withAlpha(1.0), c.alpha).toHex
      }
      fx(Modal.edgesPainted) shouldBe dimmed
      // one over it dims them again; closed, they are as the one under it left them
      val second = fx(new Container {
        size.width @= 100.0
        size.height @= 50.0
        focusable @= true
      })
      fx(Modal.over(second, second))
      fx(Modal.edgesPainted) should not be dimmed
      fx(Modal.close())
      fx(Modal.edgesPainted) shouldBe dimmed
      // under a cover (a screensaver), and closed, the theme's background
      fx(Modal.covered @= true)
      fx(Modal.edgesPainted) shouldBe ""
      fx(Modal.covered @= false)
      fx(Modal.edgesPainted) shouldBe dimmed
      fx(Modal.close())
      fx(Modal.edgesPainted) shouldBe ""
    }
  }
}
