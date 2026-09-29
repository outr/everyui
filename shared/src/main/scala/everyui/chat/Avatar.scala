package everyui.chat

import everyui.Color
import everyui.component.{Container, ImageView, TextView}
import everyui.component.types.FontWeight
import everyui.image.Image
import reactify.*

/** Somebody's face, `diameter` across: their picture once it has loaded, and until then (or without one) the first
  * letter of their name on their own colour. `who` is read reactively, so a row handed one item after another as a
  * list scrolls can bind it (`new Avatar(item().map(_.who).getOrElse(nobody))`) and the face follows. */
class Avatar(who: => Participant, diameter: Double = 32.0) extends Container {
  private val person: Val[Participant] = own(Val(who))
  private val letter: TextView = new TextView {
    content := person().initial
    font.size @= diameter * 0.44
    font.weight @= FontWeight.SemiBold
    color @= Color.White
  }
  letter.position.center := diameter / 2.0
  letter.position.middle := diameter / 2.0
  private val picture: ImageView = new ImageView {
    radius @= diameter / 2.0
    size.width @= diameter
    size.height @= diameter
    visible @= false
  }

  size.width @= diameter
  size.height @= diameter
  radius @= diameter / 2.0
  backgroundColor := person().colour
  children ++= List(letter, picture)

  private var gone = false
  // the picture asked for last: one that loads after another was asked for is not shown
  private var asked: Option[String] = None
  onDispose(() => gone = true)

  private def load(url: Option[String]): Unit = if (url != asked) {
    asked = url
    picture.visible @= false
    url.foreach { u =>
      Image(u).map { loaded =>
        if (!gone && asked.contains(u)) {
          // (a picture takes its own size as it comes: it is drawn at the avatar's)
          picture.image @= loaded
          picture.size.width @= diameter
          picture.size.height @= diameter
          picture.visible @= true
        }
      }.startUnit()
    }
  }
  person.attach(p => load(p.avatar))
  load(person().avatar)

  override def kind: String = "Avatar(" + person().name + ")"
}
