package everyui.chat

import everyui.Color
import everyui.component.{Container, ImageView, TextView}
import everyui.component.types.{FontWeight, Semantics}
import everyui.image.Image
import everyui.kit.BusyRing
import reactify.*

/** Somebody's face, `diameter` across: their picture once it has loaded, and until then (or without one, or where it
  * fails to load) the first letter of their name on their own colour, white or black, whichever reads on it. A loaded
  * picture hides the letter and is drawn on `pictureGround` rather than their colour, so a picture with clear areas
  * (a logo) shows neither through them. `who` is read reactively, so a row handed one item after another as a list
  * scrolls can bind it (`new Avatar(item().map(_.who).getOrElse(nobody))`) and the face follows.
  *
  * While `busy` (an agent at work) a ring turns around it in the accent (`BusyRing`), standing still where people ask
  * for less motion, and gone the moment `busy` is false again. A pointer resting on it then says `busyDetail` ("Running
  * bash"), and a screen reader hears their name, that they are busy, and the detail. The ring is made the first time
  * it is wanted, so a list of faces nobody is busy in makes none. */
class Avatar(who: => Participant, diameter: Double = 32.0) extends Container {
  private val person: Val[Participant] = own(Val(who))
  /** Whether they are at work: a ring turns around the face. */
  val busy: Var[Boolean] = own(Var(false))
  /** What they are doing while busy, said by a pointer resting on the face and to a screen reader; "" says nothing. */
  val busyDetail: Var[String] = own(Var(""))
  /** What a loaded picture is drawn on, in place of their colour: nothing by default, so what is behind the avatar shows
    * through a picture's clear areas; an app can give it a ground (`Theme.raised()`). */
  val pictureGround: Var[Color] = own(Var(Color.Clear))
  // whether their picture has loaded and is shown
  private val shown: Var[Boolean] = own(Var(false))
  private val letter: TextView = new TextView {
    content := person().initial
    font.size @= diameter * 0.44
    font.weight @= FontWeight.SemiBold
    color := everyui.theme.Theme.wordsOn(person().colour)
    visible := !shown()
  }
  letter.position.center := diameter / 2.0
  letter.position.middle := diameter / 2.0
  private val picture: ImageView = new ImageView {
    sizedByPicture @= false
    radius @= diameter / 2.0
    size.width @= diameter
    size.height @= diameter
    visible @= false
  }

  size.width @= diameter
  size.height @= diameter
  radius @= diameter / 2.0
  backgroundColor := (if (shown()) pictureGround() else person().colour)
  children ++= List(letter, picture)
  tooltip := (if (busy()) busyDetail() else "")
  semantics := (if (busy()) Semantics.image((List(person().name, ChatWords.busy) ++
    (if (busyDetail().isEmpty) Nil else List(busyDetail()))).mkString(", ")) else Semantics.Plain)

  /** The ring around the face, once it has been busy. */
  private var ring: Option[BusyRing] = None
  private def ringed(on: Boolean): Unit = if (on && ring.isEmpty) {
    val made = new BusyRing(diameter, busy())
    ring = Some(made)
    // it goes around the face, outside its box
    clipsToBox @= Some(false)
    children += made
  }
  busy.attach(ringed)

  private var gone = false
  // the picture asked for last: one that loads after another was asked for is not shown
  private var asked: Option[String] = None
  onDispose(() => gone = true)

  private def load(url: Option[String]): Unit = if (url != asked) {
    asked = url
    picture.visible @= false
    shown @= false
    url.foreach { u =>
      Image(u).map { loaded =>
        // (one that will not load comes as 0 x 0: their letter and colour stay)
        if (!gone && asked.contains(u) && loaded.width > 0.0 && loaded.height > 0.0) {
          // (a picture takes its own size as it comes: it is drawn at the avatar's)
          picture.image @= loaded
          picture.size.width @= diameter
          picture.size.height @= diameter
          picture.visible @= true
          shown @= true
        }
      }.startUnit()
    }
  }
  person.attach(p => load(p.avatar))
  load(person().avatar)

  /** The ring around the face, where it has been busy. */
  def busyRing: Option[BusyRing] = ring

  override def kind: String = "Avatar(" + person().name + ")"
}
