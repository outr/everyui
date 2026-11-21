package everyui.component

import everyui.render.Host
import everyui.{FxSpec, ui}
import javafx.scene.image.WritableImage

/** A picture given a radius is cut to its rounded box, as the web's border-radius cuts an `<img>`, with no container
  * round it to clip it (`MenuPictureView`): its corners show what is under it, its middle the picture, as it is
  * resized, and square again at no radius. */
class PictureCornerSpec extends FxSpec {
  /** A red square, 100 by 100, as a file's URL. */
  private lazy val red: String = {
    val img = new java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
    for (y <- 0 until 100; x <- 0 until 100) img.setRGB(x, y, 0xff0000)
    val file = java.nio.file.Files.createTempFile("corner", ".png").toFile
    file.deleteOnExit()
    javax.imageio.ImageIO.write(img, "png", file)
    file.toURI.toString
  }

  /** The red picture at 100,100, 100 by 100, its corners rounded by 30. */
  private def picture(): ImageView = fx {
    ui.children.clear()
    val view = new ImageView
    view.sizedByPicture @= false
    view.position.left @= 100.0
    view.position.top @= 100.0
    view.size.width @= 100.0
    view.size.height @= 100.0
    view.radius @= 30.0
    view.image @= new everyui.image.Image(red, 100.0, 100.0)
    ui.children += view
    view
  }

  // the scene's pixel, read on the FX thread (`eventually` reads it there already)
  private def pixel(x: Int, y: Int): Int = {
    val image = Host.scene.snapshot(new WritableImage(Host.scene.getWidth.toInt, Host.scene.getHeight.toInt))
    image.getPixelReader.getArgb(x, y)
  }
  private def at(x: Int, y: Int): Int = fx(pixel(x, y))
  private def isRed(argb: Int): Boolean = ((argb >> 16) & 0xff) > 200 && ((argb >> 8) & 0xff) < 60

  "A picture with a radius" should {
    "be cut to its rounded corners" in {
      picture()
      eventually(isRed(pixel(150, 150))) shouldBe true
      isRed(at(102, 102)) shouldBe false
      isRed(at(197, 197)) shouldBe false
      isRed(at(150, 102)) shouldBe true
    }
    "follow its box as it is resized" in {
      val view = picture()
      eventually(isRed(pixel(150, 150))) shouldBe true
      fx(view.size.width @= 160.0)
      isRed(at(250, 150)) shouldBe true
      isRed(at(257, 102)) shouldBe false
    }
    "be square again at no radius" in {
      val view = picture()
      eventually(isRed(pixel(150, 150))) shouldBe true
      fx(view.radius @= 0.0)
      isRed(at(102, 102)) shouldBe true
    }
  }

  override def afterAll(): Unit = {
    fx(ui.children.clear())
    super.afterAll()
  }
}
