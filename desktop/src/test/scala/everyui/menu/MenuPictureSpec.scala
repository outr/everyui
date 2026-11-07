package everyui.menu

import everyui.Color
import everyui.component.{Component, Container, ImageView, MaterialIconView, TextView}
import everyui.font.MaterialIcon
import everyui.{FxSpec, ui}
import reactify.*
import java.io.File

/** An entry of a menu with a picture where its icon would be: a letter on its colour at once, a picture once it has
  * loaded and what it falls back to until then, or where it will not; dimmed where the entry is disabled, its tick at
  * the row's end, and a screen reader hearing only its label. */
class MenuPictureSpec extends FxSpec {
  private def portrait(name: String, rgb: Int): String = {
    val dir = new File("target/menupicture")
    dir.mkdirs()
    val file = new File(dir, name + ".png")
    val image = new java.awt.image.BufferedImage(40, 40, java.awt.image.BufferedImage.TYPE_INT_RGB)
    (0 until 40).foreach(x => (0 until 40).foreach(y => image.setRGB(x, y, rgb)))
    javax.imageio.ImageIO.write(image, "png", file)
    "file://" + file.getAbsolutePath
  }

  private def shown(item: MenuItem): MenuRow = fx {
    val row = new MenuRow(item, 260.0, 14.0)
    ui.children.clear()
    ui.children += row
    row
  }

  private def picture(row: MenuRow): MenuPictureView =
    fx(row.childComponents.collectFirst { case p: MenuPictureView => p }.get)

  private def inside(view: Component): List[Component] =
    view.childComponents.toList.flatMap(c => c :: inside(c))

  "A menu entry's picture" should {
    "draw a letter on its colour where the icon would be, as wide a slot" in {
      val row = shown(MenuItem("Nabo", picture = Some(MenuPicture.letter("N", Color.fromHex("1565c0")))))
      val tile = picture(row)
      fx(tile.size.width()) shouldBe (14.0 * 1.4) +- 0.01
      fx(inside(tile).collectFirst { case t: TextView => t.content() }) shouldBe Some("N")
      fx(row.semantics().label) shouldBe "Nabo"
    }
    "show a picture once it has loaded, its letter until then" in {
      val url = portrait("red", 0xcc2222)
      val row = shown(MenuItem("Red",
        picture = Some(MenuPicture.image(url, Some(MenuPicture.letter("R", Color.Black))))))
      val tile = picture(row)
      eventually(inside(tile).exists { case i: ImageView => i.image().source == url; case _ => false }) shouldBe true
      fx(inside(tile).exists(_.isInstanceOf[TextView])) shouldBe false
    }
    "keep what stands in for a picture that will not load: its letter, else the icon" in {
      val gone = "file:///nowhere/at/all.png"
      val lettered = shown(MenuItem("Gone", picture = Some(MenuPicture.image(gone, Some(MenuPicture.letter("G",
        Color.Black))))))
      val first = picture(lettered)
      Thread.sleep(300)
      fx(inside(first).collectFirst { case t: TextView => t.content() }) shouldBe Some("G")
      val plain = shown(MenuItem("Gone too", icon = MaterialIcon.Folder, picture = Some(MenuPicture.image(gone))))
      val second = picture(plain)
      Thread.sleep(300)
      fx(inside(second).collectFirst { case g: MaterialIconView => g.icon() }) shouldBe Some(MaterialIcon.Folder)
    }
    "be dimmed where the entry is disabled, and its tick shown at the row's end" in {
      val off = shown(MenuItem("Off", enabled = false, picture = Some(MenuPicture.letter("O", Color.Black))))
      val dimmed = picture(off)
      fx(dimmed.opacity()) shouldBe 0.5
      val on = Var(true)
      val ticked = shown(MenuItem("On", on = Some(on), checked = Some(true),
        picture = Some(MenuPicture.letter("O", Color.Black))))
      val tick = fx(ticked.childComponents.collect { case g: MaterialIconView => g }.last)
      fx(tick.icon()) shouldBe MaterialIcon.Check
      fx(on @= false)
      fx(tick.icon()) shouldBe MaterialIcon.Empty
      fx(ticked.semantics().checked) shouldBe Some(false)
      fx(ui.children.clear())
    }
  }
}
