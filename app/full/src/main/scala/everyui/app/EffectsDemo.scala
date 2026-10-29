package everyui.app

import everyui.{Color, Gradient, GradientStop, Shadow, ui}
import everyui.component.{Button, ButtonStyle, Container, TextView}
import everyui.component.feature.FontFeature
import everyui.render.Platform
import reactify.*

/** The drawing a branded login screen asks for: a dark ground lit by two radial glows, a field of dots fading out from
  * a point (a radial mask), a pane of frosted glass over them (a backdrop blur) with two layered shadows tucked in by
  * their spread, a button turned off, and words drawn without optical sizing (`optical=auto` leaves it to the
  * browser). */
object EffectsDemo {
  def run(): Unit = {
    if (Platform.launchArg("optical") != "auto") FontFeature.opticalSizing @= Some(false)
    val violet = Color.fromHex("#7c4dff")
    val teal = Color.fromHex("#1de9b6")
    ui.backgroundColor @= Color.fromHex("#0b0d17")
    val glow = new Container {
      size.width := ui.size.width()
      size.height := ui.size.height()
      gradient @= Some(Gradient.radial(0.2, 0.15, 0.9,
        List(GradientStop(violet.withAlpha(0.55), 0.0), GradientStop(violet.withAlpha(0.0), 1.0))))
    }
    val glow2 = new Container {
      size.width := ui.size.width()
      size.height := ui.size.height()
      gradient @= Some(Gradient.radial(0.85, 0.9, 0.7,
        List(GradientStop(teal.withAlpha(0.35), 0.0), GradientStop(teal.withAlpha(0.0), 1.0))))
    }
    // a field of dots, shown about a point and faded to nothing away from it
    val dots = new Container {
      size.width := ui.size.width()
      size.height := ui.size.height()
      mask @= Some(Gradient.radial(0.5, 0.45, 0.6,
        List(GradientStop(Color.White, 0.0), GradientStop(Color.White.withAlpha(0.0), 1.0))))
    }
    val step = 22.0
    for (i <- 0 until 80; j <- 0 until 50) dots.children += new Container {
      size.width @= 2.0
      size.height @= 2.0
      radius @= 1.0
      backgroundColor @= Color.White.withAlpha(0.35)
      position.left @= i * step
      position.top @= j * step
    }
    val card = new Container {
      size.width @= 360.0
      size.height @= 230.0
      radius @= 20.0
      backgroundColor @= Color.White.withAlpha(0.08)
      borderWidth @= 1.0
      borderColor @= Color.White.withAlpha(0.18)
      backdropBlur @= 18.0
      shadows @= List(Shadow(0.0, 8.0, 24.0, Color.Black.withAlpha(0.45), -8.0),
        Shadow(0.0, 30.0, 60.0, violet.withAlpha(0.35), -28.0))
      position.left := (ui.size.width() - size.width()) / 2.0
      position.top := (ui.size.height() - size.height()) / 2.0
    }
    val title = new TextView {
      content @= "Sign in"
      font.size @= 22.0
      color @= Color.White
      position.left @= 24.0
      position.top @= 24.0
    }
    val email = new TextView {
      content @= "teammate@example.test"
      font.size @= 14.0
      color @= Color.White.withAlpha(0.8)
      position.left @= 24.0
      position.top @= 70.0
    }
    val go = new Button("Continue", () => Platform.log("effects continue"), ButtonStyle.Filled)
    go.position.left @= 24.0
    go.position.top @= 120.0
    val off = new Button("Grant admin access", () => Platform.log("effects granted"), ButtonStyle.Outlined)
    off.enabled @= false
    off.position.left @= 24.0
    off.position.top @= 170.0
    card.children ++= List(title, email, go, off)
    ui.children ++= List(glow, glow2, dots, card)
    Platform.log("effects ok")
  }
}
