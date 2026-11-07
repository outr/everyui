package everyui.menu

import everyui.component.{Component, Container, ImageView, MaterialIconView, TextView}
import everyui.component.types.FontWeight
import everyui.font.MaterialIcon
import everyui.image.Image
import everyui.theme.Theme
import reactify.*

/** A `MenuPicture`, `side` across, rounded: a letter on its colour drawn at once, a picture once it has loaded, and
  * until it has, or where it will not, what it falls back to (`MenuPicture.Picture.fallback`), else `glyph` in
  * `ink`. Only pictured: a screen reader hears the entry's label. */
class MenuPictureView(picture: MenuPicture, side: Double, glyph: MaterialIcon, ink: => everyui.Color)
  extends Container {
  size.width @= side
  size.height @= side
  radius @= side * 0.25

  // what shows until the picture has loaded, and where it will not
  private def standIn(of: Option[MenuPicture]): Component = of match {
    case Some(MenuPicture.Letter(text, colour)) => letter(text, colour)
    case Some(MenuPicture.Picture(_, fallback)) => standIn(fallback)
    case None =>
      val view = new MaterialIconView(glyph, side * 0.82)
      view.color := ink
      view.position.center := side / 2.0
      view.position.middle := side / 2.0
      view
  }

  private def letter(text: String, colour: everyui.Color): Component = {
    val tile = new Container {
      size.width @= side
      size.height @= side
      radius @= side * 0.25
      backgroundColor @= colour
    }
    val words = new TextView {
      content @= text
      font.size @= side * 0.55
      font.weight @= FontWeight.SemiBold
      color := Theme.wordsOn(colour)
    }
    words.position.center := side / 2.0
    words.position.middle := side / 2.0
    tile.children += words
    tile
  }

  private var gone = false
  onDispose(() => gone = true)

  picture match {
    case MenuPicture.Letter(text, colour) => children += letter(text, colour)
    case MenuPicture.Picture(url, fallback) =>
      val waiting = standIn(fallback)
      children += waiting
      Image(url).map { loaded =>
        // (one that will not load comes as 0 x 0: what stands in for it stays)
        if (!gone && loaded.width > 0.0 && loaded.height > 0.0) {
          val shown = new ImageView {
            sizedByPicture @= false
            radius @= side * 0.25
          }
          shown.image @= loaded
          shown.size.width @= side
          shown.size.height @= side
          children @= Vector(shown)
          waiting.dispose()
        }
      }.startUnit()
  }

  override def kind: String = "MenuPictureView"
}
