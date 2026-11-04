package everyui.component

import everyui.render.Host
import everyui.{Color, FxSpec, Shadow, ui}
import javafx.scene.image.WritableImage

/** A container that draws a box cuts what it holds to it, by default: a card's words too wide for it stay in it, its own
  * shadow drawn whole about it; one that draws none leaves them be; an app's `clipsToBox` either way; a clip a
  * component makes of its own (a scroll's) is never undone by it. */
class ClipSpec extends FxSpec {
  private val red = Color.fromHex("ff0000")

  /** A card at 100,100, 100 by 60, holding a red bar 200 wide that runs out of its right side. */
  private def card(boxed: Boolean): (Container, Container) = fx {
    ui.children.clear()
    val c = new Container
    c.position.left @= 100.0
    c.position.top @= 100.0
    c.size.width @= 100.0
    c.size.height @= 60.0
    if (boxed) c.backgroundColor @= Color.fromHex("ffffff")
    val bar = new Container
    bar.size.width @= 200.0
    bar.size.height @= 20.0
    bar.position.top @= 20.0
    bar.backgroundColor @= red
    c.children += bar
    ui.children += c
    (c, bar)
  }

  private def at(x: Int, y: Int): Int = fx {
    val image = Host.scene.snapshot(new WritableImage(Host.scene.getWidth.toInt, Host.scene.getHeight.toInt))
    image.getPixelReader.getArgb(x, y)
  }
  private def isRed(argb: Int): Boolean = ((argb >> 16) & 0xff) > 200 && ((argb >> 8) & 0xff) < 60

  "A container" should {
    "cut what it holds to the box it draws" in {
      card(boxed = true)
      isRed(at(150, 130)) shouldBe true
      isRed(at(250, 130)) shouldBe false
    }
    "leave what it holds whole where it draws no box" in {
      card(boxed = false)
      isRed(at(250, 130)) shouldBe true
    }
    "cut it once it draws a box, and go on cutting it as the box goes" in {
      val (c, _) = card(boxed = false)
      fx(c.borderColor @= Color.fromHex("cccccc"))
      isRed(at(250, 130)) shouldBe true
      fx(c.borderWidth @= 1.0)
      isRed(at(250, 130)) shouldBe false
      fx(c.borderWidth @= 0.0)
      isRed(at(250, 130)) shouldBe false
    }
    "follow its box as it grows" in {
      val (c, _) = card(boxed = true)
      fx(c.size.width @= 180.0)
      isRed(at(270, 130)) shouldBe true
      isRed(at(290, 130)) shouldBe false
    }
    "let what it holds hang out where the app says, and cut it where it draws none" in {
      val (c, _) = card(boxed = true)
      fx(c.clipsToBox @= Some(false))
      isRed(at(250, 130)) shouldBe true
      fx(c.clipsToBox @= None)
      isRed(at(250, 130)) shouldBe false
      val (plain, _) = card(boxed = false)
      fx(plain.clipsToBox @= Some(true))
      isRed(at(250, 130)) shouldBe false
    }
    "draw its own shadow whole: on the desktop, where a clip takes it, by leaving a shadowed box uncut" in {
      val (c, _) = card(boxed = true)
      fx(c.shadow @= Some(Shadow(0.0, 0.0, 12.0, Color.fromHex("000000"))))
      // just below the card, in its shadow
      ((at(150, 163) >> 16) & 0xff) should be < 250
      isRed(at(250, 130)) shouldBe true
      fx(c.shadow @= None)
      isRed(at(250, 130)) shouldBe false
    }
    "never undo a scroll's own clip" in {
      val scroll = fx {
        ui.children.clear()
        val s = new ScrollContainer()
        s.size.width @= 100.0
        s.size.height @= 60.0
        s.backgroundColor @= Color.fromHex("ffffff")
        ui.children += s
        s
      }
      val own = fx(scroll.node.fx.getClip)
      own should not be null
      fx(scroll.clipsToBox @= Some(false))
      fx(scroll.node.fx.getClip) shouldBe own
    }
  }

  override def afterAll(): Unit = {
    fx(ui.children.clear())
    super.afterAll()
  }
}
