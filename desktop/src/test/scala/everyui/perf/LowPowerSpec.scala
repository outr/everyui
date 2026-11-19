package everyui.perf

import everyui.component.{Container, ImageView}
import everyui.render.{Platform, TvEngine}
import everyui.{Color, FxSpec, ui}

/** A slow device runs at Reduced where no level is kept for it: an old TV's browser engine, Android's low-memory
  * devices, one of 1.5 GB or less. Below Full a blurred picture is drawn darker in place of its blur, a pane of frosted
  * glass is given a fill nearly solid, and shadows go; each comes back at Full. */
class LowPowerSpec extends FxSpec {
  private val webOS35 = "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/38.0.2125.122 " +
    "Safari/537.36 LG Browser/8.00.00(LGE; 43UJ6300-UA; 05.20.40; 1; DTV_W17P); webOS.TV-2017; LG NetCast.TV-2013"
  private val webOS4 = "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.34 " +
    "Safari/537.36 WebAppManager"
  private val tizen4 = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 4.0) AppleWebKit/537.36 (KHTML, like Gecko) " +
    "SamsungBrowser/2.1 Chrome/56.0.2924.0 TV Safari/537.36"
  private val webOS5 = "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/68.0.3440.106 " +
    "Safari/537.36 WebAppManager"
  private val webOS2 = "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/538.2 (KHTML, like Gecko) Large Screen " +
    "WebAppManager Safari/538.2"

  private def effect(c: Container): javafx.scene.effect.Effect = fx(c.node.fx.getEffect)
  private def fillAlpha(c: Container): Double = fx {
    c.node.fx.asInstanceOf[javafx.scene.layout.Region].getBackground.getFills.get(0).getFill
      .asInstanceOf[javafx.scene.paint.Color].getOpacity
  }

  override def afterAll(): Unit = {
    fx {
      ui.children.clear()
      Quality.forget()
    }
    super.afterAll()
  }

  "A slow device" should {
    "be told by its TV's engine: webOS 4 and before, Tizen 4 and before, never webOS 5 or a page off a TV" in {
      TvEngine.chromium(webOS35) shouldBe 38
      TvEngine.slow(remote = true, webOS35) shouldBe true
      TvEngine.slow(remote = true, webOS4) shouldBe true
      TvEngine.slow(remote = true, tizen4) shouldBe true
      TvEngine.slow(remote = true, webOS2) shouldBe true
      TvEngine.slow(remote = true, webOS5) shouldBe false
      TvEngine.slow(remote = false, webOS4) shouldBe false
    }
    "run at Reduced where nothing is kept for it, and at Full otherwise" in {
      Quality.slow(DeviceHint(lowPower = Some(true))) shouldBe true
      Quality.slow(DeviceHint(lowRam = Some(true))) shouldBe true
      Quality.slow(DeviceHint(ramMb = Some(1024))) shouldBe true
      Quality.slow(DeviceHint(ramMb = Some(1536))) shouldBe true
      Quality.slow(DeviceHint(ramMb = Some(4096), lowRam = Some(false), lowPower = Some(false))) shouldBe false
      Quality.slow(DeviceHint()) shouldBe false
      DeviceHint(model = Some("LG"), lowPower = Some(true)).line shouldBe "LG low-power"
      // the desktop says nothing that makes it slow: None runs at Full, and a level given wins
      fx(Quality.forget())
      fx(Quality.suggested) shouldBe QualityLevel.Full
      fx(Quality.start(None))
      fx(Quality()) shouldBe QualityLevel.Full
    }
  }

  "Below Full" should {
    "draw a blurred picture darker in place of its blur, and blur it again at Full" in {
      val picture = fx {
        Quality.forget()
        ui.children.clear()
        val c = new Container
        c.children += new ImageView
        c.blur @= 20.0
        ui.children += c
        c
      }
      effect(picture) shouldBe a[javafx.scene.effect.GaussianBlur]
      fx(Platform.blurs()) shouldBe true
      fx(Quality.start(Some(QualityLevel.Reduced)))
      val dark = effect(picture)
      dark shouldBe a[javafx.scene.effect.ColorAdjust]
      dark.asInstanceOf[javafx.scene.effect.ColorAdjust].getBrightness shouldBe -Quality.blurDarkens +- 0.001
      dark.asInstanceOf[javafx.scene.effect.ColorAdjust].getInput shouldBe null
      fx(Platform.blurs()) shouldBe false
      fx(Quality.forget())
      effect(picture) shouldBe a[javafx.scene.effect.GaussianBlur]
      fx(Platform.blurs()) shouldBe true
    }
    "give a pane of frosted glass a fill nearly solid, and drop shadows" in {
      val pane = fx {
        Quality.forget()
        ui.children.clear()
        val c = new Container
        c.size.width @= 100.0
        c.size.height @= 100.0
        c.backgroundColor @= Color.fromHex("#203040").withAlpha(0.3)
        c.backdropBlur @= 12.0
        c.shadow @= Some(everyui.Shadow(0.0, 4.0, 12.0, Color.Black))
        ui.children += c
        c
      }
      fillAlpha(pane) shouldBe 0.3 +- 0.01
      effect(pane) should not be null
      fx(Quality.start(Some(QualityLevel.Reduced)))
      fillAlpha(pane) shouldBe Quality.frostedFill +- 0.01
      effect(pane) shouldBe null
      fx(Quality.forget())
      fillAlpha(pane) shouldBe 0.3 +- 0.01
      // a fill set while it runs below Full is made solid too
      fx(Quality.start(Some(QualityLevel.Reduced)))
      fx(pane.backgroundColor @= Color.fromHex("#405060").withAlpha(0.5))
      fillAlpha(pane) shouldBe Quality.frostedFill +- 0.01
      fx(Quality.forget())
      fillAlpha(pane) shouldBe 0.5 +- 0.01
    }
  }
}
