package everyui.call

import everyui.Color
import everyui.chat.{Avatar, Participant}
import everyui.component.types.Semantics
import everyui.component.{Container, MaterialIconView, TextView}
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** Someone in a call, in a box the app sizes: their camera's picture while they send it (this person's own as in a
  * mirror), and otherwise their face (`face`: their picture, or their letter on their colour). Their name is in its
  * corner, with a crossed-out microphone while theirs is off, and a ring is drawn around it while they speak.
  *
  * `who` is their identity, read reactively, so a tile can follow whoever a stage shows. `source` is the camera's, or
  * the screen they share (a [[ScreenShareView]]), whole inside the box, named as theirs and with no face where there
  * is none. A screen reader hears their name, and whether they are muted or speaking.
  *
  * {{{
  *   val tile = new VideoTile(call, "u-17", face = p => Participant(p.identity, p.shownName, avatars.get(p.identity)))
  *   tile.size.width @= 320.0
  *   tile.size.height @= 180.0
  * }}} */
class VideoTile(val call: Call,
                who: => String,
                val source: TrackSource = TrackSource.Camera,
                face: CallParticipant => Participant = VideoTile.face,
                diameter: Double = 72.0) extends Container {
  /** Them, as the call last told of them, while they are in it. */
  val person: Val[Option[CallParticipant]] = own(Val {
    val id = who
    call.everyone().find(_.identity == id)
  })
  /** The track drawn: theirs from `source`, while they send it. */
  val track: Val[Option[CallTrack]] = own(Val(person().flatMap(_.track(source))))
  /** Whether they are speaking, and the ring is drawn (only around a camera's tile). */
  val speaking: Val[Boolean] = own(Val(source == TrackSource.Camera && person().exists(_.speaking)))
  /** Whether their microphone is off. */
  val muted: Val[Boolean] = own(Val(person().exists(!_.microphone)))
  /** The ring's colour while they speak. */
  val ringColor: Var[Color] = own(Var(Theme.success()))
  /** The ring's width while they speak. */
  val ringWidth: Var[Double] = own(Var(3.0))

  private def named: String = person().map(_.shownName).getOrElse("")

  /** Their picture. */
  val video: CallVideo = new CallVideo(call, track(), source == TrackSource.Camera,
    source == TrackSource.Camera && person().exists(_.local))
  video.size.width := size.width()
  video.size.height := size.height()
  video.radius := radius()

  /** Their face, while there is no picture (a camera's tile only). */
  val avatar: Avatar = new Avatar(face(person().getOrElse(CallParticipant(who, ""))), diameter)
  avatar.position.center := size.width() / 2.0
  avatar.position.middle := size.height() / 2.0
  avatar.visible := source == TrackSource.Camera && track().isEmpty

  private val mic = new MaterialIconView(MaterialIcon.MicOff, 16.0) {
    color @= Color.White
    visible := source == TrackSource.Camera && muted()
  }
  private val name = new TextView {
    content := (if (source == TrackSource.Camera) named else CallWords.screenOf(named))
    font.size @= 13.0
    color @= Color.White
    maxLines @= 1
  }
  /** Their name in the corner, on a shade the picture shows through. */
  val label: Container = new Container {
    backgroundColor @= Color.fromRGBA(0, 0, 0, 0.55)
    radius @= 6.0
    children ++= List(mic, name)
  }
  mic.position.left @= 6.0
  mic.position.middle := label.size.height() / 2.0
  name.position.left := (if (mic.visible()) 26.0 else 8.0)
  name.position.middle := label.size.height() / 2.0
  name.maxWidth := math.max(0.0, size.width() - 16.0 - name.position.left() - 8.0)
  label.size.width := name.position.left() + name.size.width() + 8.0
  label.size.height @= 24.0
  label.position.left @= 8.0
  label.position.bottom := size.height() - 8.0
  label.visible := person().nonEmpty && (source == TrackSource.Camera || track().nonEmpty)

  radius @= 12.0
  backgroundColor := Theme.surface()
  borderColor := ringColor()
  borderWidth := (if (speaking()) ringWidth() else 0.0)
  children ++= List(video, avatar, label)
  semantics := Semantics.image((List(if (source == TrackSource.Camera) named else CallWords.screenOf(named)) ++
    (if (source == TrackSource.Camera && muted()) List(CallWords.muted) else Nil) ++
    (if (speaking()) List(CallWords.speaking) else Nil)).mkString(", "))

  override def kind: String = "VideoTile(" + who + ")"
}

object VideoTile {
  /** Their face as their name and identity draw it: their letter on their colour. */
  def face(p: CallParticipant): Participant = Participant(p.identity, p.shownName)
}
