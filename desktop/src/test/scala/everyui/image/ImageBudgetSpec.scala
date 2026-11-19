package everyui.image

import everyui.component.{Container, ImageView}
import everyui.perf.{Quality, QualityLevel}
import everyui.{FxSpec, ui}

/** Pictures out of sight are kept while they fit in the budget, the one longest out of sight let go to make room, and
  * one kept comes back as it was; with no budget (the desktop's own) each goes at once. One in sight for less than a
  * moment, or disposed of, is never kept; below Full the budget is halved; what is hidden under a screen left is passed
  * over; and what pictures hold is counted. Pictures here are 100 by 100, 40,000 bytes decoded, so a budget of 0.1 MB
  * keeps two. */
class ImageBudgetSpec extends FxSpec {
  private def picture(n: Int): Image = new Image("/images/budget-" + n.toString + ".png", 100.0, 100.0)

  /** `n` views on the page, each with its picture, drawn. */
  private def views(n: Int): Vector[ImageView] = fx {
    ui.children.clear()
    Vector.tabulate(n) { i =>
      val v = new ImageView
      v.image @= picture(i)
      ui.children += v
      v
    }
  }

  private def drawn(v: ImageView): String = fx(v.drawnSource)

  private def fresh(mb: Double, seenMs: Double = 0.0): Unit = fx {
    ui.children.clear()
    ImageBudget.trim()
    ImageBudget.megabytes @= mb
    ImageBudget.seenMs = seenMs
    Quality.forget()
  }

  override def afterAll(): Unit = {
    fresh(ImageBudget.byDevice(), 100.0)
    super.afterAll()
  }

  "The picture budget" should {
    "keep pictures out of sight while they fit, letting the one longest out of sight go, and give one back as it was" in {
      fresh(0.1)
      val Vector(a, b, c) = views(3)
      fx(a.holding @= false)
      fx(b.holding @= false)
      drawn(a) should endWith("budget-0.png")
      drawn(b) should endWith("budget-1.png")
      fx((ImageBudget.keptCount, ImageBudget.keptBytes)) shouldBe (2, 80000.0)
      fx(c.holding @= false)
      drawn(a) shouldBe ""
      drawn(b) should endWith("budget-1.png")
      drawn(c) should endWith("budget-2.png")
      fx(ImageBudget.keptCount) shouldBe 2
      // back in sight: kept, it is drawn as it was; let go, it is drawn again from its source
      fx(b.holding @= true)
      drawn(b) should endWith("budget-1.png")
      fx((ImageBudget.keptCount, ImageBudget.keptBytes)) shouldBe (1, 40000.0)
      fx(a.holding @= true)
      drawn(a) should endWith("budget-0.png")
      fx(ImageBudget.keptCount) shouldBe 1
    }
    "let each go at once with no budget, as off the web" in {
      fresh(0.0)
      val Vector(a, b) = views(2)
      fx(a.holding @= false)
      drawn(a) shouldBe ""
      drawn(b) should endWith("budget-1.png")
      fx(ImageBudget.keptCount) shouldBe 0
      fx(a.holding @= true)
      drawn(a) should endWith("budget-0.png")
    }
    "keep none in sight for less than a moment, as one built out of sight and culled at once" in {
      fresh(0.1, seenMs = 60000.0)
      val Vector(a) = views(1)
      fx(a.holding @= false)
      drawn(a) shouldBe ""
      fx(ImageBudget.keptCount) shouldBe 0
    }
    "never keep one disposed of, and let a kept one go as another picture is set out of sight" in {
      fresh(0.1)
      val before = fx((ImageBudget.held, ImageBudget.heldBytes))
      val Vector(a, b) = views(2)
      fx((ImageBudget.held, ImageBudget.heldBytes)) shouldBe (before._1 + 2, before._2 + 80000.0)
      fx(a.holding @= false)
      fx(ImageBudget.keptCount) shouldBe 1
      fx(a.image @= picture(7))
      drawn(a) shouldBe ""
      fx(ImageBudget.keptCount) shouldBe 0
      fx(a.holding @= true)
      drawn(a) should endWith("budget-7.png")
      fx {
        ui.children.clear()
        a.dispose()
        b.dispose()
      }
      drawn(a) shouldBe ""
      fx(ImageBudget.keptCount) shouldBe 0
      fx((ImageBudget.held, ImageBudget.heldBytes)) shouldBe before
    }
    "keep half as much below Full" in {
      fresh(0.1)
      fx(Quality.start(Some(QualityLevel.Reduced)))
      val Vector(a, b) = views(2)
      fx(a.holding @= false)
      fx(b.holding @= false)
      drawn(a) shouldBe ""
      drawn(b) should endWith("budget-1.png")
      fx(ImageBudget.keptCount) shouldBe 1
      fx(Quality.forget())
    }
    "pass over what is hidden as a screen is left and shown again" in {
      fresh(0.0)
      val (screen, shown, hidden) = fx {
        ui.children.clear()
        val screen = new Container
        val tab = new Container
        val shown = new ImageView
        val hidden = new ImageView
        shown.image @= picture(1)
        hidden.image @= picture(2)
        tab.children += hidden
        screen.children ++= List(shown, tab)
        ui.children += screen
        // a tab kept but not shown has let its pictures go already
        tab.visible @= false
        ImageView.showing(tab, false)
        (screen, shown, hidden)
      }
      drawn(hidden) shouldBe ""
      fx(ImageView.showing(screen, false, passHidden = true))
      drawn(shown) shouldBe ""
      fx(ImageView.showing(screen, true, passHidden = true))
      drawn(shown) should endWith("budget-1.png")
      drawn(hidden) shouldBe ""
    }
  }
}
