package everyui.focus

import everyui.access.{Access, AccessRule}
import everyui.component.*
import everyui.kit.SkipLink
import everyui.{FxSpec, ui}

/** The keys' focus is seen, and goes where the screen reads: a ring round an app's own component that draws none,
  * none round one that draws its own or is EveryUI's; a skip link Tab reaches first, past the header to the content;
  * and Tab going back up the screen found by the check. */
class FocusRingSpec extends FxSpec {
  override def beforeAll(): Unit = {
    super.beforeAll()
    onStage()
  }

  override def afterAll(): Unit = {
    offStage()
    super.afterAll()
  }

  private def card(top: Double): Container = {
    val c = new Container
    c.position.top @= top
    c.size.width @= 200.0
    c.size.height @= 40.0
    c.focusable @= true
    c.children += TextView("Card " + top.toInt)
    c
  }

  private def ringed(c: Component): Boolean = fx(c.node.fx.getEffect != null)

  private def onScreen[T](parts: Component*)(test: => T): T = {
    fx {
      ui.children.clear()
      Focus.focused @= None
      ui.children ++= parts
    }
    try test finally fx {
      Focus.focused @= None
      Focus.visible @= false
      ui.children.clear()
    }
  }

  "The focus moved by the keys" should {
    "be ringed on an app's own component that draws none, and not once it moves on or the pointer is used" in {
      val (a, b) = fx((card(0.0), card(60.0)))
      onScreen(a, b) {
        fx {
          Focus.visible @= true
          a.focus()
        }
        ringed(a) shouldBe true
        fx(b.focus())
        (ringed(a), ringed(b)) shouldBe (false, true)
        fx(Focus.visible @= false)
        ringed(b) shouldBe false
      }
    }
    "not be ringed where the component draws its own, or is EveryUI's" in {
      val (own, marked, button) = fx {
        val own = card(0.0)
        own.borderWidth := (if (own.focusShown()) 2.0 else 0.0)
        (own, Focus.ownRing(card(60.0)), new Button("Save") { position.top @= 120.0 })
      }
      onScreen(own, marked, button) {
        fx(Focus.visible @= true)
        List(own, marked, button).foreach { c =>
          fx(c.focus())
          ringed(c) shouldBe false
        }
      }
    }
  }

  "A skip link" should {
    "be the first stop of Tab, wherever it is put, and take the focus on to the content" in {
      val (header, content, first) = fx {
        val header = new Container
        header.size.width @= 400.0
        header.size.height @= 40.0
        header.children += new Button("Settings")
        val content = new Container
        content.position.top @= 60.0
        content.size.width @= 400.0
        content.size.height @= 200.0
        val first = card(0.0)
        content.children += first
        (header, content, first)
      }
      val skip = fx(new SkipLink(content))
      onScreen(header, content, skip) {
        fx(skip.size.width()) shouldBe 0.0
        fx(Focus.next())
        fx(Focus.focused()) shouldBe Some(skip)
        fx(skip.size.width()) should be > 0.0
        fx(skip.skip())
        fx(Focus.focused()) shouldBe Some(first)
      }
    }
  }

  "The check" should {
    "find Tab going back up the screen, and not the reading order" in {
      val (a, b) = fx((card(0.0), card(60.0)))
      onScreen(a, b)(fx(Access.check().filter(_.rule == AccessRule.Order)) shouldBe Nil)
      onScreen(b, a)(fx(Access.check().filter(_.rule == AccessRule.Order).map(_.component)) shouldBe List(a))
    }
  }
}
