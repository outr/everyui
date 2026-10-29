package everyui.component

import everyui.{Color, FxSpec, Gradient, GradientStop, RadialShape, Shadow}
import everyui.component.feature.FontFeature
import javafx.scene.layout.Pane
import javafx.scene.paint.RadialGradient

/** What a branded screen ported from Flutter needs drawn: a radial gradient in pixels that follows its box, layered shadows with a
  * spread, a mask, a backdrop blur and a variable face's optical sizing, each kept as a component's own. */
class VisualEffectsSpec extends FxSpec {
  private val stops = List(GradientStop(Color.fromHex("#7c4dff"), 0.0), GradientStop(Color.Clear, 1.0))

  "A radial gradient" should {
    "be drawn about its centre, its radius the shorter side's share, and follow the box's size" in {
      val box = fx(new Container {
        size.width @= 400.0
        size.height @= 200.0
        gradient @= Some(Gradient.radial(0.25, 0.5, 0.5, stops))
      })
      def drawn: RadialGradient = fx(box.node.fx.asInstanceOf[Pane].getBackground.getFills.get(0).getFill
        .asInstanceOf[RadialGradient])
      drawn.getCenterX shouldBe 100.0
      drawn.getCenterY shouldBe 100.0
      drawn.getRadius shouldBe 100.0
      fx(box.size.height @= 300.0)
      drawn.getCenterY shouldBe 150.0
      drawn.getRadius shouldBe 150.0
    }
    "keep its shape through a blend, turning at half-way, and as it clears" in {
      val a = Gradient(90.0, stops)
      val b = Gradient.radial(0.5, 0.5, 0.8, stops)
      Gradient.lerp(Some(a), Some(b), 0.25).get.radial shouldBe None
      Gradient.lerp(Some(a), Some(b), 0.75).get.radial shouldBe Some(RadialShape(0.5, 0.5, 0.8))
      b.clear.radial shouldBe b.radial
    }
  }

  "Shadows" should {
    "keep the four-part constructor, and carry layers and their spread to the seam" in {
      Shadow(0.0, 4.0, 12.0, Color.Black).spread shouldBe 0.0
      val layers = List(Shadow(0.0, 2.0, 6.0, Color.Black, -8.0), Shadow(0.0, 18.0, 40.0, Color.Black, -16.0))
      Shadow.decode(Shadow.encode(layers)).map(l => (l._1, l._2, l._3, l._4)) shouldBe
        List((0.0, 2.0, 6.0, -8.0), (0.0, 18.0, 40.0, -16.0))
    }
    "draw the first layer where only one is drawn" in {
      val card = fx(new Container {
        size.width @= 100.0
        size.height @= 60.0
        shadows @= List(Shadow(1.0, 3.0, 9.0, Color.Black, -8.0), Shadow(0.0, 18.0, 40.0, Color.Black))
      })
      val effect = fx(card.node.fx.getEffect.asInstanceOf[javafx.scene.effect.DropShadow])
      (effect.getOffsetX, effect.getOffsetY, effect.getRadius) shouldBe (1.0, 3.0, 9.0)
      fx(card.shadows @= Nil)
      fx(card.node.fx.getEffect) shouldBe null
    }
  }

  "A mask and a backdrop blur" should {
    "be the component's to set and clear, drawn whole where the platform has neither" in {
      val pane = fx(new Container {
        mask @= Some(Gradient.radial(0.5, 0.5, 0.5, stops))
        backdropBlur @= 18.0
      })
      fx(pane.mask().flatMap(_.radial)) shouldBe Some(RadialShape(0.5, 0.5, 0.5))
      fx(pane.backdropBlur()) shouldBe 18.0
      fx(pane.backdropBlur @= 0.0)
      fx(pane.mask @= None)
      fx(pane.node.fx.getEffect) shouldBe null
    }
  }

  "Optical sizing" should {
    "be the app's default for text made after it is set, and each text's own after" in {
      try {
        fx(FontFeature.opticalSizing @= Some(false))
        val label = fx(new TextView { content @= "teammate@example.test" })
        fx(label.font.opticalSizing()) shouldBe Some(false)
        fx(label.font.opticalSizing @= None)
        fx(label.font.opticalSizing()) shouldBe None
        fx(label.font.variations @= Map("wdth" -> 87.5))
        fx(label.font.variations()) shouldBe Map("wdth" -> 87.5)
      } finally fx(FontFeature.opticalSizing @= None)
      fx(new TextView().font.opticalSizing()) shouldBe None
    }
  }
}
