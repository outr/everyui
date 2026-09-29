package everyui.chat

import everyui.Color
import everyui.component.{Container, ImageView, TextView}
import everyui.component.types.FontWeight
import everyui.image.Image

/** Somebody's face, `diameter` across: their picture once it has loaded, and until then (or without one) the first
  * letter of their name on their own colour. */
class Avatar(who: Participant, diameter: Double = 32.0) extends Container {
  private val letter: TextView = new TextView {
    content @= who.initial
    font.size @= diameter * 0.44
    font.weight @= FontWeight.SemiBold
    color @= Color.White
  }
  letter.position.center := diameter / 2.0
  letter.position.middle := diameter / 2.0

  size.width @= diameter
  size.height @= diameter
  radius @= diameter / 2.0
  backgroundColor @= who.colour
  children += letter

  private var gone = false
  onDispose(() => gone = true)
  who.avatar.foreach { url =>
    Image(url).map { loaded =>
      if (!gone) {
        val picture = new ImageView
        picture.radius @= diameter / 2.0
        picture.image @= loaded
        picture.size.width @= diameter
        picture.size.height @= diameter
        children += picture
      }
    }.startUnit()
  }

  override def kind: String = "Avatar(" + who.name + ")"
}
