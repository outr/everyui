package everyui.chat

import everyui.component.{ImageView, TextView}
import everyui.picker.HighlightRow
import everyui.{Color, FxSpec, ui}
import reactify.*

import java.io.File

/** A face's picture with clear areas (a logo): once it has loaded, neither the letter nor their colour shows through
  * it, the picture drawn on the avatar's `pictureGround`; both back where it fails to load or they have none. And a
  * picker's row with something before its words (`HighlightRow`'s `leading`), following the row's item. */
class AvatarPictureSpec extends FxSpec {
  private val ada = Participant("p-ada", "Ada")

  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    fx(ui.children.clear())
    offStage()
    super.afterAll()
  }

  /** A 40px picture, a disc of one colour on a clear square, as a file's URL. */
  private def logo(name: String): String = {
    val dir = new File("target/avatarpicture")
    dir.mkdirs()
    val file = new File(dir, name + ".png")
    val image = new java.awt.image.BufferedImage(40, 40, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    (0 until 40).foreach(x => (0 until 40).foreach { y =>
      val (dx, dy) = (x - 20, y - 20)
      if (dx * dx + dy * dy < 100) image.setRGB(x, y, 0xff2244cc)
    })
    javax.imageio.ImageIO.write(image, "png", file)
    "file://" + file.getAbsolutePath
  }

  private def faceOf(who: Var[Participant]): Avatar = fx {
    ui.children.clear()
    val face = new Avatar(who(), 32.0)
    ui.children += face
    face
  }
  private def letter(face: Avatar): TextView = face.childComponents.collectFirst { case t: TextView => t }.get
  private def shown(face: Avatar): Boolean = face.childComponents.exists {
    case p: ImageView => p.visible()
    case _ => false
  }

  "An avatar's picture" should {
    "hide the letter and their colour once it has loaded, and show both again without it" in {
      val who = Var(ada)
      val face = faceOf(who)
      fx(letter(face).visible()) shouldBe true
      fx(face.backgroundColor()) shouldBe ada.colour
      val url = logo("clear")
      fx(who @= ada.copy(avatar = Some(url)))
      eventually(shown(face)) shouldBe true
      fx(letter(face).visible()) shouldBe false
      fx(face.backgroundColor()) shouldBe Color.Clear
      // a ground the app chose
      fx(face.pictureGround @= Color.White)
      fx(face.backgroundColor()) shouldBe Color.White
      fx(who @= ada)
      fx(shown(face)) shouldBe false
      fx(letter(face).visible()) shouldBe true
      fx(face.backgroundColor()) shouldBe ada.colour
    }

    "keep the letter and their colour where it fails to load" in {
      val who = Var(ada.copy(avatar = Some("file:///nowhere/at/all.png")))
      val face = faceOf(who)
      // a picture that loads after it proves the failed one was settled
      val url = logo("after")
      Thread.sleep(500L)
      fx(shown(face)) shouldBe false
      fx(letter(face).visible()) shouldBe true
      fx(face.backgroundColor()) shouldBe ada.colour
      fx(who @= ada.copy(avatar = Some(url)))
      eventually(shown(face)) shouldBe true
      fx(letter(face).visible()) shouldBe false
    }
  }

  "A picker's row" should {
    "put what `leading` makes before its words, following the row's item" in {
      val (row, tile) = fx {
        ui.children.clear()
        var made: TextView = null
        val row = new HighlightRow[String](identity, _ => Nil, leading = Some((item: Val[Option[String]]) => {
          made = new TextView {
            content := item().map(_.take(1).toUpperCase).getOrElse("")
          }
          made
        }))
        row.size.width @= 300.0
        row.size.height @= 32.0
        row.item @= Some("alpha")
        ui.children += row
        (row, made)
      }
      fx(row.childComponents.head) should be theSameInstanceAs tile
      fx(tile.content()) shouldBe "A"
      fx(())
      val label = fx(row.childComponents(1))
      fx(label.position.left()) shouldBe 8.0 + fx(tile.size.width()) + 14.0 * 0.6
      fx(tile.position.top()) shouldBe fx((32.0 - tile.size.height()) / 2.0)
      fx(row.item @= Some("beta"))
      fx(tile.content()) shouldBe "B"
      fx(label.position.left()) shouldBe 8.0 + fx(tile.size.width()) + 14.0 * 0.6
      // without it, the words where they always were
      val plain = fx(new HighlightRow[String](identity, _ => Nil))
      fx(plain.childComponents.head.position.left()) shouldBe 8.0
    }
  }
}
