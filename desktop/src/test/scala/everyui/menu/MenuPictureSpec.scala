package everyui.menu

import everyui.Color
import everyui.component.{Component, Container, ImageView, MaterialIconView, TextView}
import everyui.font.MaterialIcon
import everyui.{FxSpec, ui}
import reactify.*
import java.io.File

/** An entry of a menu with a picture where its icon would be: a letter on its colour at once, a picture once it has
  * loaded and what it falls back to until then, or where it will not; dimmed where the entry is disabled, its tick at
  * the row's end, and a screen reader hearing only its label; as big as an icon whatever its own size, as far from its
  * words as an icon is, with a detail and without, and every row's words, after a picture, an icon, a tick or nothing,
  * in one column. */
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
    "draw a letter on its colour where the icon would be, as big as an icon" in {
      val row = shown(MenuItem("Nabo", picture = Some(MenuPicture.letter("N", Color.fromHex("1565c0")))))
      val tile = picture(row)
      fx(tile.size.width()) shouldBe MenuRow.lead(14.0) +- 0.01
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

  // a row's words (its label and detail), and what it leads with: its picture, else the glyph before the words
  private def wordsOf(row: MenuRow): Component = row.childComponents.find(c => !c.isInstanceOf[MenuPictureView] &&
    inside(c).exists { case t: TextView => t.content() == row.item.label; case _ => false }).get
  private def leadOf(row: MenuRow): Option[Component] = {
    val words = wordsOf(row)
    row.childComponents.find(c => (c ne words) && c.position.left() < words.position.left())
  }
  // between the end of what a row leads with and its words
  private def gapOf(row: MenuRow): Double = fx {
    val lead = leadOf(row).get
    wordsOf(row).position.left() - lead.position.left() - lead.size.width()
  }
  private def menuOf(items: List[MenuItem]): Menu = fx {
    ui.children.clear()
    Menu.at(10.0, 10.0, items)
  }
  private def closed(): Unit = fx {
    while (everyui.component.Modal.showing) everyui.component.Modal.close()
    ui.children.clear()
  }

  "A menu with pictures and icons" should {
    // pictures smaller than the icon, as big, larger and far larger, drawn at the picture's size whatever their own
    def sized(name: String, side: Int): String = {
      val dir = new File("target/menupicture")
      dir.mkdirs()
      val file = new File(dir, name + ".png")
      val image = new java.awt.image.BufferedImage(side, side, java.awt.image.BufferedImage.TYPE_INT_RGB)
      (0 until side).foreach(x => (0 until side).foreach(y => image.setRGB(x, y, 0x22aa44)))
      javax.imageio.ImageIO.write(image, "png", file)
      "file://" + file.getAbsolutePath
    }
    val pictures = List(sized("eight", 8), sized("sixteen", 16), sized("twenty", 20), sized("ninetysix", 96))
    def items(detail: String): List[MenuItem] =
      MenuItem("Icon", icon = MaterialIcon.Folder, detail = detail) ::
        MenuItem("Letter", detail = detail, picture = Some(MenuPicture.letter("S", Color.fromHex("5c6bc0")))) ::
        pictures.zipWithIndex.map { case (url, i) => MenuItem("Picture " + i, detail = detail,
          picture = Some(MenuPicture.image(url, Some(MenuPicture.letter("P", Color.Black))))) } ++
        List(MenuItem("Blank", detail = detail), MenuItem("Ticked", checked = Some(true), detail = detail),
          MenuItem.separator, MenuItem("Last icon", icon = MaterialIcon.Settings))

    "keep each picture as far from its words as an icon is, with a detail and without, whatever its size" in {
      List("", "A line of detail under the label").foreach { detail =>
        val menu = menuOf(items(detail))
        val rows = fx(menu.rows.filterNot(_.item.separator))
        val pictured = rows.filter(_.item.picture.nonEmpty)
        // the pictures loaded, so it is their drawn size measured, not what stood in for them
        eventually(pictured.forall(r => inside(leadOf(r).get).exists(_.isInstanceOf[ImageView]) ||
          r.item.picture.exists(_.isInstanceOf[MenuPicture.Letter]))) shouldBe true
        val icon = gapOf(rows.head)
        icon shouldBe MenuRow.gap(Menu.textSize()) +- 0.01
        pictured.foreach(r => withClue(r.item.label + " " + detail)(gapOf(r) shouldBe icon +- 0.01))
        // each picture drawn as big as the icon whatever its image's own size: 8, 16, 20 and 96 pixels about an icon
        // of 16 at a menu's 14 points
        val side = fx(leadOf(rows.head).get.size.width())
        pictured.foreach(r => fx(leadOf(r).get.size.width()) shouldBe side +- 0.01)
        closed()
      }
    }
    "line every row's words up in one column: icons, pictures, a tick and none" in {
      List("", "A line of detail under the label").foreach { detail =>
        val menu = menuOf(items(detail))
        val starts = fx(menu.rows.filterNot(_.item.separator).map(r => r.item.label -> wordsOf(r).position.left()))
        starts.map(_._2).distinct.size shouldBe 1
        closed()
        // where a menu of icons alone has its words: a picture moves no row's words
        val plain = menuOf(List(MenuItem("Icon", icon = MaterialIcon.Folder, detail = detail), MenuItem("Blank")))
        fx(plain.rows.map(r => wordsOf(r).position.left()).distinct) shouldBe List(starts.head._2)
        closed()
      }
    }
    "keep a picture apart from its words on a row of its own, outside a menu" in {
      val icon = gapOf(shown(MenuItem("Icon", icon = MaterialIcon.Folder, detail = "Detail")))
      val letter = gapOf(shown(MenuItem("Letter", detail = "Detail", picture = Some(MenuPicture.letter("S",
        Color.Black)))))
      letter shouldBe icon +- 0.01
      fx(ui.children.clear())
    }
  }
}
