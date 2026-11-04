package everyui.component

import everyui.chat.{Avatar, Participant}
import everyui.image.Image
import everyui.{FxSpec, ui}

/** A picture view the app sizes itself keeps its size whatever picture comes, however often; one left to itself takes
  * each picture's own size. An avatar is one the app sizes. */
class ImageSizeSpec extends FxSpec {
  private val portrait = new Image("/images/none.png", 512.0, 512.0)
  private val other = new Image("/images/other.png", 300.0, 200.0)

  "A picture view" should {
    "keep the size the app gives it when it sizes itself, whatever picture comes" in {
      val own = fx {
        ui.children.clear()
        val v = new ImageView
        v.sizedByPicture @= false
        v.size.width @= 36.0
        v.size.height @= 36.0
        ui.children += v
        v
      }
      fx(own.image @= portrait)
      fx((own.size.width(), own.size.height())) shouldBe (36.0, 36.0)
      fx(own.image @= other)
      fx((own.size.width(), own.size.height())) shouldBe (36.0, 36.0)
    }
    "take each picture's own size by default" in {
      val view = fx(new ImageView)
      fx(view.image @= portrait)
      fx(view.size.width()) shouldBe 512.0
      fx(view.image @= other)
      fx((view.size.width(), view.size.height())) shouldBe (300.0, 200.0)
    }
    "be sized by the avatar it is in" in {
      val avatar = fx(new Avatar(Participant("ada", "Ada"), 36.0))
      val picture = fx(avatar.children().collectFirst { case i: ImageView => i }.get)
      fx(picture.sizedByPicture()) shouldBe false
      fx(picture.image @= portrait)
      fx(picture.size.width()) shouldBe 36.0
    }
  }

  override def afterAll(): Unit = {
    fx(ui.children.clear())
    super.afterAll()
  }
}
