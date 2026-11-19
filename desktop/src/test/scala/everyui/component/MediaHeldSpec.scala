package everyui.component

import everyui.render.Nodes
import everyui.{FxSpec, ui}

/** What holds a player (`MediaSupport.held`): a view with a source, until it is let go — released, taken apart, or put
  * away out of sight (and held again as it comes back). A screen with a video left and come back to many times holds
  * one at most, and none once it is left for good. */
class MediaHeldSpec extends FxSpec {
  private def view(): VideoView = fx {
    val v = new VideoView(Nodes.container())
    v.size.width @= 320.0
    v.size.height @= 180.0
    ui.children += v
    v
  }

  "A player" should {
    "be held from its source until it is let go" in {
      val base = fx(MediaSupport.held)
      val v = view()
      fx(MediaSupport.held) shouldBe base
      fx(v.open("clip.webm", 30.0))
      fx(MediaSupport.held) shouldBe base + 1
      fx(v.source @= "")
      fx(MediaSupport.held) shouldBe base
      fx(v.source @= "clip.webm")
      fx {
        v.release()
        v.release()
      }
      fx(MediaSupport.held) shouldBe base
      fx {
        ui.children -= v
        v.dispose()
      }
      fx(MediaSupport.held) shouldBe base
    }
    "be let go out of sight, and held again back in it" in {
      val base = fx(MediaSupport.held)
      val v = view()
      fx(v.source @= "clip.webm")
      eventually(MediaSupport.held == base + 1) shouldBe true
      // seen once, then taken off the screen: put away
      Thread.sleep(700)
      fx(ui.children -= v)
      eventually(MediaSupport.held == base) shouldBe true
      fx(ui.children += v)
      eventually(MediaSupport.held == base + 1) shouldBe true
      fx {
        ui.children -= v
        v.dispose()
      }
      fx(MediaSupport.held) shouldBe base
    }
    "be one at most for a screen left and come back to twenty times, and none once left" in {
      val base = fx(MediaSupport.held)
      (1 to 20).foreach { i =>
        val v = view()
        fx(v.open("clip.webm", i.toDouble))
        fx(MediaSupport.held) shouldBe base + 1
        fx(v.seek(5.0))
        fx {
          ui.children -= v
          v.dispose()
        }
      }
      fx(MediaSupport.held) shouldBe base
    }
  }
}
