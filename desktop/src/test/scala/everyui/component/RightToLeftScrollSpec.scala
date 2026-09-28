package everyui.component

import everyui.event.{Key, KeyEvent}
import everyui.focus.Focus
import everyui.i18n.Direction
import everyui.layout.HorizontalLayout
import everyui.{FxSpec, ui}
import reactify.*

/** A rail in a right-to-left language runs from the right: it opens on its first items, at its right end, and stays
  * there as it grows or narrows; Left goes on along it, and `reveal` brings a card into view from that end.
  * (tools/rtl-scroll-check.js checks the web's own scroll in a browser.) */
class RightToLeftScrollSpec extends FxSpec {
  private def card(): Container = new Container {
    size.width @= 100.0
    size.height @= 50.0
  }

  /** A rail 300 wide of `count` cards 100 wide, in a right-to-left language, under `ui` for the test. */
  private def rail[T](count: Int)(test: (ScrollContainer, Vector[Container]) => T): T = {
    val (scroll, cards) = fx {
      Direction.rtl @= true
      val scroll = new ScrollContainer(horizontal = true) {
        size.width @= 300.0
        size.height @= 50.0
        content.layout @= Some(HorizontalLayout(0.0))
      }
      val cards = Vector.fill(count)(card())
      scroll.content.children := cards
      ui.children.clear()
      Focus.focused @= None
      ui.children += scroll
      (scroll, cards)
    }
    try test(scroll, cards) finally fx {
      ui.children.clear()
      Focus.focused @= None
      Direction.set(false)
    }
  }

  // where a card shows in the viewport, from its left
  private def shown(scroll: ScrollContainer, c: Component): Double = fx(c.absoluteLeft - scroll.absoluteLeft)

  private def press(scroll: ScrollContainer, key: Key): Unit = fx(scroll.event.key.down @= KeyEvent(key, key.toString))

  "A rail in a right-to-left language" should {
    "open on its first card, at its right end" in rail(10) { (scroll, cards) =>
      fx(scroll.offset()) shouldBe 0.0
      shown(scroll, cards.head) shouldBe 200.0
      shown(scroll, cards(2)) shouldBe 0.0
    }
    "stay on its first card as it grows" in rail(5) { (scroll, cards) =>
      fx(scroll.content.children ++= Vector.fill(5)(card()))
      shown(scroll, cards.head) shouldBe 200.0
    }
    "stay on its first card as it narrows and widens" in rail(10) { (scroll, cards) =>
      fx(scroll.size.width @= 200.0)
      shown(scroll, cards.head) shouldBe 100.0
      fx(scroll.size.width @= 500.0)
      shown(scroll, cards.head) shouldBe 400.0
    }
    "hold a short row at its right" in rail(2) { (scroll, cards) =>
      shown(scroll, cards.head) shouldBe 200.0
      shown(scroll, cards(1)) shouldBe 100.0
    }
    "go on along it with Left, and back with Right" in rail(10) { (scroll, cards) =>
      fx(scroll.step @= 100.0)
      press(scroll, Key.ArrowLeft)
      fx(scroll.offset()) shouldBe 100.0
      shown(scroll, cards.head) shouldBe 300.0
      press(scroll, Key.ArrowRight)
      press(scroll, Key.ArrowRight)
      fx(scroll.offset()) shouldBe 0.0
      shown(scroll, cards.head) shouldBe 200.0
    }
    "go no further than its last card" in rail(10) { (scroll, cards) =>
      fx(scroll.scrollTo(10000.0))
      fx(scroll.offset()) shouldBe 700.0
      shown(scroll, cards.last) shouldBe 0.0
    }
    "reveal a card from its start" in rail(10) { (scroll, cards) =>
      fx {
        scroll.revealMargin @= 0.0
        scroll.revealAlign @= None
        scroll.reveal(cards(5))
      }
      // the least that shows it: at the viewport's left end, with the room `reveal` leaves past it
      fx(scroll.offset()) shouldBe 316.0
      fx {
        scroll.revealAlign @= Some(0.0)
        scroll.reveal(cards(5))
      }
      fx(scroll.offset()) shouldBe 500.0
      shown(scroll, cards(5)) shouldBe 200.0
      fx(scroll.reveal(cards.head))
      fx(scroll.offset()) shouldBe 0.0
    }
    "draw what shows at its right end, and not its far end" in rail(20) { (scroll, cards) =>
      fx(cards.head.visible()) shouldBe true
      fx(cards.last.visible()) shouldBe false
      fx(scroll.scrollTo(10000.0))
      fx(cards.head.visible()) shouldBe false
      fx(cards.last.visible()) shouldBe true
    }
    "start again at its start when the direction changes, laid out again" in rail(10) { (scroll, cards) =>
      fx(scroll.scrollTo(300.0))
      fx(Direction.set(false))
      fx(scroll.offset()) shouldBe 0.0
      shown(scroll, cards.head) shouldBe 0.0
      fx(scroll.scrollTo(300.0))
      fx(Direction.set(true))
      fx(scroll.offset()) shouldBe 0.0
      shown(scroll, cards.head) shouldBe 200.0
    }
  }

  "A rail run from its left in a right-to-left language" should {
    "open, go on and reveal as it does left to right" in {
      val (scroll, cards) = fx {
        Direction.set(true)
        val scroll = new ScrollContainer(horizontal = true, followsDirection = false) {
          size.width @= 300.0
          size.height @= 50.0
          step @= 100.0
          revealMargin @= 0.0
          revealAlign @= Some(0.0)
        }
        // a timeline: placed by hand, left to right
        val cards = Vector.tabulate(10) { i =>
          val c = card()
          c.position.left @= i * 100.0
          c
        }
        scroll.content.children := cards
        ui.children += scroll
        (scroll, cards)
      }
      try {
        fx(scroll.offset()) shouldBe 0.0
        shown(scroll, cards.head) shouldBe 0.0
        press(scroll, Key.ArrowRight)
        fx(scroll.offset()) shouldBe 100.0
        shown(scroll, cards.head) shouldBe -100.0
        press(scroll, Key.ArrowLeft)
        fx(scroll.offset()) shouldBe 0.0
        fx(scroll.reveal(cards(5)))
        fx(scroll.offset()) shouldBe 500.0
        shown(scroll, cards(5)) shouldBe 0.0
        fx(cards(5).visible()) shouldBe true
        fx(cards.head.visible()) shouldBe false
        // and a change of direction leaves it where it is
        fx(Direction.set(false))
        fx(scroll.offset()) shouldBe 500.0
        fx(Direction.set(true))
        fx(scroll.offset()) shouldBe 500.0
        shown(scroll, cards(5)) shouldBe 0.0
      }
      finally fx {
        ui.children.clear()
        Direction.set(false)
      }
    }
  }

  "A tab bar in a right-to-left language" should {
    "hold its tabs, and its rule, at its right" in {
      val bar = fx {
        Direction.rtl @= true
        val bar = everyui.kit.TabBar(List("one" -> "One", "two" -> "Two"), Var("one"), 600.0)
        ui.children += bar
        bar
      }
      try {
        val first = fx(bar("one").get)
        fx(first.absoluteLeft + first.size.width() - bar.absoluteLeft) shouldBe 600.0 +- 0.5
        // the rule under them, as far as they go, from the right
        val rule = fx(bar.children().head)
        fx(rule.position.left() + rule.size.width()) shouldBe 600.0 +- 0.5
        fx(rule.size.width()) should be < 600.0
      }
      finally fx {
        ui.children.clear()
        Direction.set(false)
      }
    }
  }

  "A rail in a left-to-right language" should {
    "open on its first card, at its left end, and go on with Right" in {
      val scroll = fx(new ScrollContainer(horizontal = true) {
        size.width @= 300.0
        size.height @= 50.0
        content.layout @= Some(HorizontalLayout(0.0))
        step @= 100.0
      })
      val cards = fx {
        val cards = Vector.fill(10)(card())
        scroll.content.children := cards
        ui.children += scroll
        cards
      }
      try {
        shown(scroll, cards.head) shouldBe 0.0
        press(scroll, Key.ArrowRight)
        shown(scroll, cards.head) shouldBe -100.0
      }
      finally fx(ui.children.clear())
    }
  }
}
