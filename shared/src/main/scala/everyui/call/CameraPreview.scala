package everyui.call

import everyui.chat.{Avatar, Participant}
import everyui.component.types.Semantics
import everyui.component.{Container, MaterialIconView, TextView}
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** This person's own camera before a call (a [[CallPreview]]'s), in a box the app sizes and rounded as a call's tiles
  * are: the picture filling the box (`cover`, cut at the edges) or whole inside it, turned as in a mirror (`mirror`,
  * on by default, as people expect to see themselves). While there is no picture, a placeholder in its middle: their
  * face (`face`, their picture or their letter on their colour) while the camera is turned off, where the app gives
  * one, and otherwise an icon, with words under it saying why (`CallWords`: off, refused, none found, could not start;
  * an app words them with `Words.add`, or hides `message` and says its own). A screen reader hears it as a picture
  * of their camera, and why there is none.
  *
  * {{{
  *   val picture = new CameraPreview(preview)
  *   picture.size.width @= 480.0
  *   picture.size.height @= 270.0
  *   picture.face @= Some(Participant("guest", nameField.value()))
  * }}} */
class CameraPreview(val preview: CallPreview, filling: Boolean = true, mirrored: Boolean = true,
                    diameter: Double = 72.0) extends Container {
  /** Filling the box and cut at its edges, or whole inside it. */
  val cover: Var[Boolean] = own(Var(filling))
  /** Turned as in a mirror. */
  val mirror: Var[Boolean] = own(Var(mirrored))
  /** Their face, shown while the camera is turned off; None shows the icon. */
  val face: Var[Option[Participant]] = own(Var(None))
  /** Where the camera stands. */
  val state: Val[PreviewState] = own(Val(preview.cameraState()))
  /** Whether the picture is drawn: the camera live. */
  val showing: Val[Boolean] = own(Val(state() == PreviewState.Live))

  /** The box the picture is drawn in, filling this one. */
  val picture: Container = new Container
  picture.size.width := size.width()
  picture.size.height := size.height()
  picture.radius := radius()
  picture.visible := showing()

  /** Their face while the camera is off. */
  val avatar: Avatar = new Avatar(face().getOrElse(Participant("", "")), diameter)
  avatar.visible := state() == PreviewState.Off && face().nonEmpty
  /** The icon while there is no picture and no face: a camera crossed out, or a sign that it was refused. */
  val icon: MaterialIconView = new MaterialIconView(MaterialIcon.VideocamOff, 40.0) {
    color := Theme.muted()
  }
  icon.icon := (state() match {
    case PreviewState.Denied(_) => MaterialIcon.NoPhotography
    case _ => MaterialIcon.VideocamOff
  })
  icon.visible := !showing() && state() != PreviewState.Starting && !avatar.visible()
  /** Why there is no picture, in `CallWords`; "" while it opens or shows. */
  val message: TextView = new TextView {
    content := CameraPreview.words(state())
    font.size @= 14.0
    color := Theme.muted()
    maxLines @= 2
  }
  message.maxWidth := math.max(0.0, size.width() - 32.0)
  message.visible := !showing() && message.content().nonEmpty

  private val above: Val[Double] = own(Val(if (avatar.visible()) avatar.size.height() else icon.size.height()))
  private val gap = 12.0
  private val tall: Val[Double] =
    own(Val(above() + (if (message.visible()) gap + message.size.height() else 0.0)))
  avatar.position.center := size.width() / 2.0
  avatar.position.top := (size.height() - tall()) / 2.0
  icon.position.center := size.width() / 2.0
  icon.position.top := (size.height() - tall()) / 2.0
  message.position.center := size.width() / 2.0
  message.position.top := (size.height() - tall()) / 2.0 + above() + gap

  radius @= 12.0
  backgroundColor := Theme.surface()
  children ++= List(picture, avatar, icon, message)
  semantics := Semantics.image(if (message.content().isEmpty) CallWords.preview
    else CallWords.preview + ", " + message.content())

  private def redraw(): Unit = {
    preview.media.hide(this)
    preview.media.show(this)
  }

  cover.attach(_ => redraw())
  mirror.attach(_ => redraw())
  onDispose(() => preview.media.hide(this))
  preview.media.show(this)

  override def kind: String = "CameraPreview"
}

object CameraPreview {
  /** What a preview says of a camera in `state`: nothing while it opens or shows. */
  def words(state: PreviewState): String = state match {
    case PreviewState.Off => CallWords.cameraOff
    case PreviewState.Denied(_) => CallWords.cameraDenied
    case PreviewState.NoDevice => CallWords.noCamera
    case PreviewState.Unavailable | PreviewState.Failed(_) => CallWords.cameraFailed
    case _ => ""
  }
}
