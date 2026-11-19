package everyui.list

import everyui.component.{Container, ImageView, ScrollContainer}
import everyui.image.{Image, ImageBudget}
import everyui.layout.{HorizontalLayout, LayoutPass}
import everyui.perf.{Quality, QualityLevel}
import everyui.{FxSpec, ui}
import reactify.*

/** What a long list and a long rail hold, counted: a list of ten thousand makes the rows of a screenful and a few beyond
  * each end, however far it scrolls, and fewer beyond below Full; a rail of sixty cards draws only those in its view
  * and the slack beside it, the rest culled with their pictures let go. */
class WindowingSpec extends FxSpec {
  override def afterAll(): Unit = {
    fx {
      ui.children.clear()
      Quality.forget()
      ImageBudget.megabytes @= ImageBudget.byDevice()
    }
    super.afterAll()
  }

  /** A list of `n` items, 280 tall (ten rows of 28), on the page and laid out. */
  private def list(n: Int): VirtualList[Int] = fx {
    ui.children.clear()
    val l = VirtualList.text(Var(Vector.tabulate(n)(i => i)), (i: Int) => i.toString, (i: Int) => "Row " + i.toString)
    l.size.width @= 300.0
    l.size.height @= 280.0
    ui.children += l
    LayoutPass.settle()
    l
  }

  "A long list" should {
    "make a screenful of rows and a few beyond, however far it is scrolled" in {
      fx(Quality.forget())
      val l = list(10000)
      val shown = 10
      val beyond = fx(l.overscan())
      eventually(l.rowsMade > 0) shouldBe true
      fx(l.rowsMade) should be <= shown + 1 + beyond * 2
      fx {
        l.scrollTo(5000)
        LayoutPass.settle()
      }
      fx(l.rowsMade) should be <= shown + 1 + beyond * 2 + beyond
      fx {
        l.scrollTo(9999)
        LayoutPass.settle()
      }
      fx(l.rowsMade) should be <= shown + 1 + beyond * 2 + beyond
    }
    "make fewer beyond below Full" in {
      fx(Quality.forget())
      val first = list(10000)
      val full = fx(first.rowsMade)
      fx(Quality.start(Some(QualityLevel.Reduced)))
      val second = list(10000)
      val reduced = fx(second.rowsMade)
      reduced should be < full
      fx(Quality.forget())
    }
  }

  "A long rail" should {
    "draw the cards in its view and beside it, culling the rest with their pictures" in {
      val rail = fx {
        Quality.forget()
        ImageBudget.megabytes @= 0.0
        ui.children.clear()
        val r = new ScrollContainer(horizontal = true)
        r.size.width @= 900.0
        r.size.height @= 200.0
        r.content.layout @= Some(HorizontalLayout(16.0))
        r.content.children @= Vector.tabulate(60) { i =>
          val card = new Container
          card.size.width @= 120.0
          card.size.height @= 180.0
          val poster = new ImageView
          poster.sizedByPicture @= false
          poster.size.width @= 120.0
          poster.size.height @= 180.0
          poster.image @= new Image("/images/card-" + i.toString + ".png", 120.0, 180.0)
          card.children += poster
          card
        }
        ui.children += r
        LayoutPass.settle()
        r
      }
      def drawnCards(): Int = rail.content.children().count(c => c.childComponents.exists {
        case i: ImageView => i.drawnSource.nonEmpty
        case _ => false
      })
      // 900 wide is seven cards of 136, and half a view of slack each way: about fourteen drawn, never the sixty
      eventually(drawnCards() < 20) shouldBe true
      fx(drawnCards()) should be >= 7
      fx(rail.content.children().count(c => rail.outOfView(c))) should be >= 40
      fx {
        rail.scrollTo(136.0 * 30)
        LayoutPass.settle()
      }
      eventually(rail.content.children()(33).childComponents.head.asInstanceOf[ImageView].drawnSource.nonEmpty) shouldBe
        true
      fx(drawnCards()) should be < 20
      fx(rail.content.children().head.childComponents.head.asInstanceOf[ImageView].drawnSource) shouldBe ""
    }
  }
}
