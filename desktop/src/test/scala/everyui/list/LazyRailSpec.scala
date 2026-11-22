package everyui.list

import everyui.component.{Component, Container, ImageView}
import everyui.event.Pointer
import everyui.focus.Focus
import everyui.image.{Image, ImageBudget}
import everyui.layout.LayoutPass
import everyui.perf.Quality
import everyui.{FxSpec, ui}
import reactify.*

/** A rail that makes only the cards in its view and a few beyond: as long as every card would make it, more made as it
  * scrolls or the focus moves along it, those far behind let go where it says, and no picture asked for in a card not
  * made. */
class LazyRailSpec extends FxSpec {
  override protected def keyed: Boolean = true

  override def afterAll(): Unit = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      ImageBudget.megabytes @= ImageBudget.byDevice()
    }
    super.afterAll()
  }

  // a card 120 by 180 with a poster
  private def card(i: Int): Component = {
    val c = new Container
    c.size.width @= 120.0
    c.size.height @= 180.0
    c.focusable @= true
    c.semantics @= everyui.component.types.Semantics(label = "card " + i.toString)
    val poster = new ImageView
    poster.sizedByPicture @= false
    poster.size.width @= 120.0
    poster.size.height @= 180.0
    poster.image @= new Image("/images/card-" + i.toString + ".png", 120.0, 180.0)
    c.children += poster
    c
  }

  /** A rail 900 wide of `n` cards 120 wide, 16 apart. */
  private def rail(n: Int): LazyRail[Int] = fx {
    ui.children.clear()
    Quality.forget()
    ImageBudget.megabytes @= 0.0
    val r = new LazyRail(Var(Vector.tabulate(n)(i => i)), (i: Int) => i.toString, 120.0, 16.0)(card)
    r.size.width @= 900.0
    ui.children += r
    LayoutPass.settle()
    r
  }

  private def press(key: String): Unit = fx {
    Pointer.usedAt = -1.0e9
    Focus.key(key, true)
    Focus.key(key, false)
  }

  private def settled(): Unit = {
    fx(LayoutPass.settle())
    fx(LayoutPass.settle())
  }

  // pictures set on a node, in the whole of `ui`
  private def picturesDrawn(r: LazyRail[Int]): Int = fx(r.content.children().count(c => c.childComponents.exists {
    case i: ImageView => i.drawnSource.nonEmpty
    case _ => false
  }))

  "A LazyRail" should {
    "make the cards in its view and a few beyond, as long as all of them" in {
      val r = rail(100)
      settled()
      // 900 wide is under seven cards of 136, and three beyond the end: ten made, never the hundred
      fx(r.madeCount) should be >= 7
      fx(r.madeCount) should be <= 11
      fx(r.content.size.width()) shouldBe (100 * 120.0 + 99 * 16.0)
      fx(r.size.height()) shouldBe 180.0
      picturesDrawn(r) should be <= 11
      fx(r.scrollTo(136.0 * 50))
      settled()
      fx(r.cardAt(52).nonEmpty) shouldBe true
      fx(r.cardAt(52).get.position.left()) shouldBe 136.0 * 52
      fx(r.cardAt(30)) shouldBe None
    }
    "move the focus along into cards not made yet, and let those far behind go where it says" in {
      val r = rail(60)
      fx(r.releaseBeyond @= 4)
      fx(r.make(0).get.focus())
      (1 to 40).foreach { i =>
        press("ArrowRight")
        settled()
        fx(Focus.focused().map(_.semantics().label)) shouldBe Some("card " + i.toString)
      }
      fx(r.cardAt(0)) shouldBe None
      fx(r.madeCount) should be <= 20
      fx(r.offset()) should be > 0.0
      press("ArrowLeft")
      settled()
      fx(Focus.focused().map(_.semantics().label)) shouldBe Some("card 39")
    }
    "run from the right in a right-to-left language, Left going on along it" in {
      try {
        fx(everyui.i18n.Direction.set(true))
        val r = rail(40)
        settled()
        // the first card at the right end of the content, the view on it
        fx(r.cardAt(0).get.position.left()) shouldBe (40 * 120.0 + 39 * 16.0 - 120.0)
        fx(r.make(0).get.focus())
        (1 to 12).foreach { i =>
          press("ArrowLeft")
          settled()
          fx(Focus.focused().map(_.semantics().label)) shouldBe Some("card " + i.toString)
        }
        fx(r.cardAt(14).nonEmpty) shouldBe true
      }
      finally fx(everyui.i18n.Direction.set(false))
    }
    "keep the cards of items that stay, and tell of the end once for each count" in {
      val r = rail(20)
      var told = Vector.empty[Int]
      fx(r.nearEnd.attach(n => told = told :+ n))
      val first = fx(r.cardAt(0).get)
      fx(r.scrollTo(136.0 * 20))
      settled()
      told shouldBe Vector(20)
      fx(r.items @= r.items() ++ Vector.tabulate(10)(i => 100 + i))
      settled()
      fx(r.cardAt(0).exists(_ eq first)) shouldBe true
      fx(r.content.size.width()) shouldBe (30 * 120.0 + 29 * 16.0)
    }
  }
}
