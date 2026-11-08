package everyui.component

import everyui.layout.VerticalLayout
import everyui.{FxSpec, ui}

/** A scroll without its bar (`bar` off) scrolls as one with it does and keeps no room for one, and its ends fade
  * (`edgeFade`) only where there is more beyond them, following the scroll: none with nothing past the viewport, the
  * end alone at the start, both in the middle, the start alone at the end. */
class ScrollBarFadeSpec extends FxSpec {
  private def tile(): Container = new Container {
    size.width @= 44.0
    size.height @= 44.0
  }

  // a rail of tiles down the window's edge, 44 wide and 200 tall, holding `n` tiles 44 tall
  private def rail[T](n: Int)(test: ScrollContainer => T): T = {
    val scroll = fx {
      val scroll = new ScrollContainer {
        size.width @= 44.0
        size.height @= 200.0
        content.layout @= Some(VerticalLayout(0.0))
      }
      scroll.content.children := Vector.fill(n)(tile())
      ui.children.clear()
      ui.children += scroll
      scroll
    }
    try test(scroll) finally fx(ui.children.clear())
  }

  "A scroll without its bar" should {
    "keep no room for one, wherever the platform would draw one" in {
      ScrollContainer.room(horizontal = true, bar = true, draws = true, over = true) shouldBe ScrollContainer.barRoom
      ScrollContainer.room(horizontal = true, bar = false, draws = true, over = true) shouldBe 0.0
      ScrollContainer.room(horizontal = true, bar = true, draws = true, over = false) shouldBe 0.0
      ScrollContainer.room(horizontal = false, bar = true, draws = true, over = true) shouldBe 0.0
    }
    "scroll by its offset, scrollTo and reveal as one with its bar does" in rail(20) { scroll =>
      fx(scroll.bar @= false)
      fx(scroll.scrollbarRoom()) shouldBe 0.0
      fx(scroll.content.size.width()) shouldBe 44.0
      fx(scroll.offset @= 100.0)
      fx(scroll.content.position.top()) shouldBe -100.0
      fx(scroll.scrollTo(10000.0))
      fx(scroll.offset()) shouldBe 20 * 44.0 - 200.0
      fx(scroll.scrollTo(0.0))
      val tenth = fx(scroll.content.children()(10))
      fx(scroll.reveal(tenth))
      fx(scroll.offset()) should be > 0.0
      val shown = fx(tenth.absoluteTop - scroll.absoluteTop)
      shown should be >= 0.0
      (shown + 44.0) should be <= 200.0
    }
  }

  "A scroll's edge fade" should {
    "fade only the ends with more beyond them, as worked out" in {
      ScrollContainer.ends(24.0, 0.0, 0.0, 200.0) shouldBe ((0.0, 0.0))
      ScrollContainer.ends(24.0, 0.0, 680.0, 200.0) shouldBe ((0.0, 24.0))
      ScrollContainer.ends(24.0, 300.0, 680.0, 200.0) shouldBe ((24.0, 24.0))
      ScrollContainer.ends(24.0, 680.0, 680.0, 200.0) shouldBe ((24.0, 0.0))
      ScrollContainer.ends(0.0, 300.0, 680.0, 200.0) shouldBe ((0.0, 0.0))
      // never more than half the viewport
      ScrollContainer.ends(500.0, 300.0, 680.0, 200.0) shouldBe ((100.0, 100.0))
    }
    "follow the scroll: none with nothing past it, the end at the start, both between, the start at the end" in
      rail(20) { scroll =>
        fx(scroll.edgeFade @= 24.0)
        fx(scroll.fades()) shouldBe ((0.0, 24.0))
        fx(scroll.offset @= 300.0)
        fx(scroll.fades()) shouldBe ((24.0, 24.0))
        fx(scroll.scrollTo(10000.0))
        fx(scroll.fades()) shouldBe ((24.0, 0.0))
        fx(scroll.edgeFade @= 0.0)
        fx(scroll.fades()) shouldBe ((0.0, 0.0))
      }
    "fade nothing where everything shows, and the end once the viewport is shorter than what it holds" in
      rail(3) { scroll =>
        fx(scroll.edgeFade @= 24.0)
        fx(scroll.fades()) shouldBe ((0.0, 0.0))
        fx(scroll.size.height @= 100.0)
        fx(scroll.fades()) shouldBe ((0.0, 24.0))
      }
  }
}
