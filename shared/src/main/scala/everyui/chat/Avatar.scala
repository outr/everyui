package everyui.chat

import everyui.Color
import everyui.component.{Container, ImageView, TextView}
import everyui.component.types.{FontWeight, Semantics}
import everyui.image.Image
import everyui.kit.BusyRing
import everyui.list.ListRow
import everyui.theme.Theme
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
  * it is wanted, so a list of faces nobody is busy in makes none.
  *
  * Their `status` (an `AvatarStatus`: in a meeting, away, there) is a dot of its colour on the face's bottom corner
  * (the bottom-left in a right-to-left language), ringed in `statusGround` so it reads over a picture or their colour,
  * and its words are said by a pointer resting on the face (under the busy detail, where there is one) and heard by a
  * screen reader after their name and anything busy says. It follows `Avatar.statuses` for their id, so a status the
  * app sets there is on every face of theirs, those already drawn and those drawn after; set on one face, it is that
  * face's alone. The dot is made the first time one shows. */
class Avatar(who: => Participant, diameter: Double = 32.0) extends Container {
  private val person: Val[Participant] = own(Val(who))
  /** Whether they are at work: a ring turns around the face. */
  val busy: Var[Boolean] = own(Var(false))
  /** What they are doing while busy, said by a pointer resting on the face and to a screen reader; "" says nothing. */
  val busyDetail: Var[String] = own(Var(""))
  /** What a loaded picture is drawn on, in place of their colour: nothing by default, so what is behind the avatar shows
    * through a picture's clear areas; an app can give it a ground (`Theme.raised()`). */
  val pictureGround: Var[Color] = own(Var(Color.Clear))
  /** Their status on this face: by default what `Avatar.statuses` holds for their id (nothing for one without an id),
    * following it as it changes; set (`@=`), this face's own, `None` showing none whatever the map holds, until it is
    * bound to the map again (`status := Avatar.statusOf(id)`). */
  val status: Var[Option[AvatarStatus]] = own(Var(Avatar.statusOf(person().id)))
  /** Whether the status is drawn as a dot on the face: an app's view that marks the same corner with something of its
    * own turns it off, and its words are said all the same. */
  val statusDot: Var[Boolean] = own(Var(true))
  /** What rings the dot, to part it from the face: the ground the avatar is drawn on, the theme's background by
    * default; an avatar on a panel gives it the panel's (`Theme.raised()`). */
  val statusGround: Var[Color] = own(Var(Theme.background()))
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
  // what a pointer says and a reader hears of the status: its words, where it has any
  private val statusWords: Val[List[String]] = own(Val(status().map(_.words).filter(_.nonEmpty).toList))
  tooltip := ((if (busy() && busyDetail().nonEmpty) List(busyDetail()) else Nil) ++ statusWords()).mkString("\n")
  semantics := {
    val said = (if (busy()) ChatWords.busy :: (if (busyDetail().isEmpty) Nil else List(busyDetail())) else Nil) ++
      statusWords()
    if (said.isEmpty) Semantics.Plain else Semantics.image((person().name :: said).mkString(", "))
  }

  /** The ring around the face, once it has been busy. */
  private var ring: Option[BusyRing] = None
  private def ringed(on: Boolean): Unit = if (on && ring.isEmpty) {
    val made = new BusyRing(diameter, busy())
    ring = Some(made)
    // it goes around the face, outside its box
    clipsToBox @= Some(false)
    children += made
    // (under the status's dot)
    dot.foreach { d =>
      children -= d
      children += d
    }
  }
  busy.attach(ringed)

  /** The status's dot, once one has shown: its ring in the ground, the status's colour inside it. */
  private var dot: Option[Container] = None
  private val dotShown: Val[Boolean] = own(Val(statusDot() && status().nonEmpty))
  private def dotted(on: Boolean): Unit = if (on && dot.isEmpty) {
    val outer = Avatar.dotOuter(diameter)
    val ringWidth = Avatar.dotRing(diameter)
    val inner = new Container {
      size.width @= outer - ringWidth * 2.0
      size.height @= outer - ringWidth * 2.0
      radius @= (outer - ringWidth * 2.0) / 2.0
      position.left @= ringWidth
      position.top @= ringWidth
      backgroundColor := status().map(_.colour).getOrElse(Color.Clear)
    }
    val made = new Container {
      size.width @= outer
      size.height @= outer
      radius @= outer / 2.0
      backgroundColor := statusGround()
      position.left := ListRow.along(Avatar.dotStart(diameter), outer, diameter)
      position.top @= Avatar.dotStart(diameter)
      visible := dotShown()
      children += inner
    }
    dot = Some(made)
    // it sits on the face's edge, past its box
    clipsToBox @= Some(false)
    children += made
  }
  dotShown.attach(dotted)
  dotted(dotShown())

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

  /** The status's dot, where one has shown: its ring, holding the dot itself. */
  def statusMark: Option[Container] = dot

  override def kind: String = "Avatar(" + person().name + ")"
}

object Avatar {
  /** Each person's status by their id (`Participant.id`), on every face of theirs: an app sets it once (from their
    * calendar, their presence) and every avatar of theirs shows it, or drops it as it goes from the map.
    * {{{
    *   Avatar.statuses @= Map("u-17" -> AvatarStatus(Color.fromHex("#e53935"), "In a meeting · until 3:00 PM"))
    *   Avatar.statuses @= Avatar.statuses() - "u-17"
    * }}} */
  val statuses: Var[Map[String, AvatarStatus]] = Var(Map.empty[String, AvatarStatus])

  /** The status `Avatar.statuses` holds for `id`, read reactively: none for an empty id (somebody nobody knows). */
  def statusOf(id: String): Option[AvatarStatus] = if (id.isEmpty) None else statuses().get(id)

  /** How wide the status's dot is on a face `diameter` across, its ring in the ground included: the dot about a third
    * of the face, 8 px at the least so it reads on the smallest, and the ring a little past it. */
  def dotOuter(diameter: Double): Double = math.max(8.0, diameter * 0.3) + dotRing(diameter) * 2.0

  /** How thick the dot's ring is, on a face `diameter` across. */
  def dotRing(diameter: Double): Double = math.max(1.5, diameter * 0.06)

  /** Where the dot's box starts from the face's top (and its start edge): its middle on the face's edge, at the
    * bottom corner, so it hangs a little past the face's box. */
  def dotStart(diameter: Double): Double = diameter / 2.0 * (1.0 + 0.7071067811865476) - dotOuter(diameter) / 2.0
}
