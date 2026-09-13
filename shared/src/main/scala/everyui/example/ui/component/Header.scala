package everyui.example.ui.component

import reactify.*
import everyui.component.types.{PositionType, WhiteSpace}
import everyui.component.{Component, Container, ImageView, TextView}
import everyui.example.ExampleApp
import everyui.font.GoogleFont
import everyui.image.Image
import everyui.render.{Nodes, Platform}
import everyui.ui

/** youi's example-app `Header` (its wide layout): a gradient bar with the youi logo, the active screen's title with
  * "View Source" under it, and the light/dark toggle. youi paints the gradient with CSS and loads the logo's size
  * and the icon font over the network; here the gradient is a bundled image, so both backends draw the same
  * pixels. */
class Header extends Container { self =>
  size.width := ui.size.width
  size.height @= 75.0

  private val gradient: ImageView = new ImageView {
    image @= new Image(Platform.resolvePath("/images/header-gradient.png"), 1920.0, 75.0)
    size.width := ui.size.width
  }

  private val logo: ImageView = new ImageView {
    image @= new Image(Platform.resolvePath("/images/youi.png"), 3063.0, 1172.0)
    size.height @= 65.0
    size.width @= 65.0 * 3063.0 / 1172.0
    position.left @= 10.0
    position.top @= 5.0
  }

  private val darkModeToggle: Component = new Component(Nodes.icon(Header.Brightness7, 24.0)) {
    position.`type` @= PositionType.Absolute
    position.right := ui.size.width - 25.0
    position.middle := self.size.middle
  }

  private val heading: TextView = new TextView {
    font.size @= 20.0 * Header.PointsToPixels
    content := ExampleApp.title()
    whiteSpace @= WhiteSpace.NoWrap
    position.`type` @= PositionType.Absolute
    position.right := darkModeToggle.position.left - 10.0
  }

  private val link: TextView = new TextView {
    font.size @= 14.0 * Header.PointsToPixels
    content @= "View Source"
    whiteSpace @= WhiteSpace.NoWrap
    position.`type` @= PositionType.Absolute
    position.right := darkModeToggle.position.left - 10.0
    position.top := heading.position.bottom - 10.0
  }

  // Center heading+link block vertically within the header
  heading.position.top := (self.size.height - (heading.measured.height + link.measured.height - 10.0)) / 2.0

  GoogleFont.`Open Sans`.`600`.load().map { font =>
    heading.font.weight @= font
    link.font.weight @= font
  }.startUnit()

  heading.color := ExampleApp.accentColor
  link.color := ExampleApp.accentColor
  darkModeToggle.color := ExampleApp.accentColor

  children ++= List(gradient, logo, darkModeToggle, heading, link)
}

object Header:
  /** Material Icons `brightness_7`, youi's light-mode toggle. */
  val Brightness7: String = ""
  val PointsToPixels: Double = 96.0 / 72.0
