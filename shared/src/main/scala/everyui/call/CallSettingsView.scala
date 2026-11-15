package everyui.call

import everyui.component.*
import everyui.component.types.FontWeight
import everyui.dialog.DialogPanel
import everyui.font.MaterialIcon
import everyui.layout.{FlowLayout, HorizontalLayout, VerticalLayout}
import everyui.theme.Theme
import reactify.*

/** What `CallSettings.open` puts in its dialog, and a page of the app's own can show: from the top, the camera's
  * picture (mirrored), the microphone with how loud it hears the room, the camera, the speaker with a test sound, the
  * microphone's processing (noise suppression, echo cancellation, automatic gain control) and the camera's quality
  * (Auto, 720p, 360p, Audio only). Each change is kept on the device and followed at once by a call or a preview under
  * way.
  *
  * The picture and the level come from `call` while it is under way (connecting or connected: its own tile, and the
  * microphone it sends, nothing opened again), else from `preview` (a "ready to join?" page's), else from a
  * [[CallPreview]] of its own on `engine`, opened as this is made and closed as it is taken apart, as the level's
  * listening and a test sound are. A screen reader hears the level in fifths, not each flicker. It is `wide` across
  * (bind `size.width` to change it); `CallSettings.open` makes it as wide as its dialog has room for on a phone.
  *
  * {{{
  *   page.children += new CallSettingsView()      // its own camera and microphone, until it is taken apart
  * }}} */
class CallSettingsView(call: Option[Call] = None, preview: Option[CallPreview] = None,
                       engine: CallEngine = CallEngine.default, wide: Double = CallSettingsView.width)
  extends Container {
  private val inCall: Option[Call] = call.filter(c => c.state() == CallState.Connecting || c.live)
  // outside a call, the preview the picture and the level come from: the page's, or one of its own
  private val made: Boolean = inCall.isEmpty && preview.isEmpty
  private val previewing: Option[CallPreview] =
    if (inCall.nonEmpty) None else Some(preview.getOrElse(new CallPreview(call.map(_.engine).getOrElse(engine))))
  private val heard: Var[Double] = own(Var(0.0))
  private val hearing: () => Unit = inCall.map(c => c.meter(l => heard @= l)).getOrElse(() => ())
  private var sounding: () => Unit = () => ()

  /** The camera's picture, as wide as this and 16:9 up to `CallSettingsView.tallest`: the call's own tile, or the
    * preview's. */
  val picture: Container = inCall match {
    case Some(c) => new VideoTile(c, c.local().map(_.identity).getOrElse(""))
    case None => previewing.map(p => new CameraPreview(p): Container).getOrElse(new Container)
  }
  picture.size.width := size.width()
  picture.size.height := math.min(size.width() * 9.0 / 16.0, CallSettingsView.tallest)

  /** How loud the microphone hears the room, 0 to 1. */
  val level: Val[Double] = own(Val(previewing.map(_.level()).getOrElse(heard())))
  /** The level as a bar under the microphone. */
  val meter: MicLevel = new MicLevel(level(), wide, 6.0, 5)
  meter.size.width := size.width()

  /** The microphone, the camera and the speaker, each the system's own first. */
  val microphones: Select = devices(MediaDevices.microphone, MediaDevices.microphones, CallWords.microphoneSwitch)
  val cameras: Select = devices(MediaDevices.camera, MediaDevices.cameras, CallWords.cameraSwitch)
  val speakers: Select = devices(MediaDevices.speaker, MediaDevices.speakers, CallWords.speakerChoice)

  /** Plays a chime through the chosen speaker. */
  val test: Button = new Button(CallWords.testSound, () => {
    sounding()
    sounding = MediaDevices.testSound()
  }, ButtonStyle.Tonal, Some(MaterialIcon.VolumeUp))
  microphones.size.width := size.width()
  cameras.size.width := size.width()
  // the test beside the speakers where both fit, else under them
  private val beside: Val[Boolean] =
    own(Val(MediaDevices.speakersChosen && size.width() - test.size.width() - 8.0 >= CallSettingsView.narrowest))
  speakers.size.width := size.width() - (if (beside()) test.size.width() + 8.0 else 0.0)

  /** The microphone's processing, each kept as it is switched. */
  val noiseSuppression: Switch = switch(CallWords.noiseSuppression, CallSettings.noiseSuppression)
  val echoCancellation: Switch = switch(CallWords.echoCancellation, CallSettings.echoCancellation)
  val autoGain: Switch = switch(CallWords.autoGain, CallSettings.autoGain)
  // the switches' words in line with the headings, and their ends with the selects'
  private val processing: Container = new Container {
    private val column = Column()(noiseSuppression, echoCancellation, autoGain)
    column.position.left @= -Switch.pad
    size.height := column.size.height()
    children += column
  }
  processing.size.width := size.width()

  /** The camera's quality, one chosen. */
  val qualities: Container = new Container {
    layout @= Some(FlowLayout(8.0, 8.0))
    children ++= VideoQuality.all.map(q =>
      new Choice(CallWords.quality(q), CallSettings.quality() == q, () => CallSettings.quality @= q))
  }
  qualities.size.width := size.width()

  // where a speaker cannot be chosen (Safari), the test alone; where there are no calls, nothing to play
  private val speaker: List[Component] =
    if (!MediaDevices.supported) List(speakers)
    else if (MediaDevices.speakersChosen) List(new Container {
      layout := Some(if (beside()) HorizontalLayout(8.0, middle = true) else VerticalLayout(8.0))
      children ++= List(speakers, test)
    })
    else List(test)

  layout @= Some(VerticalLayout(16.0))
  size.width @= wide
  children ++= List(
    picture,
    group(CallWords.microphoneSwitch, microphones, meter),
    group(CallWords.cameraSwitch, cameras),
    group(CallWords.speakerChoice, speaker*),
    group(CallWords.processing, processing),
    group(CallWords.videoQuality, qualities))

  onDispose(() => {
    hearing()
    sounding()
    if (made) previewing.foreach(_.dispose())
  })

  // a heading, and what it is over, close under it
  private def group(title: String, parts: Component*): Container =
    Column(8.0)((TextView.line(title, 13.0, Theme.muted(), FontWeight.Bold) +: parts)*)

  // a device of a kind, chosen by its place in the list after the system's own, and following the choice kept
  private def devices(chosen: Var[Option[String]], listed: Val[Vector[MediaDevice]], name: String): Select = {
    val select = new Select(CallWords.systemDefault :: listed().toList.map(_.shown))
    val at: Val[Int] = own(Val(listed().find(d => chosen().contains(d.id)).map(_.index + 1).getOrElse(0)))
    select.size.height @= 36.0
    select.label @= name
    select.selected @= at()
    at.attach(i => if (select.selected() != i) select.selected @= i)
    select.selected.attach(i => if (i != at()) chosen @= listed().lift(i - 1).map(_.id))
    select
  }

  // a line of the processing, its switch at the end
  private def switch(label: String, kept: Var[Boolean]): Switch = {
    val s = new Switch(label, linked(kept), Some(wide))
    s.size.width := size.width() + Switch.pad * 2.0
    s
  }

  // a switch's own copy of a setting that lives on, the two kept the same until this is taken apart
  private def linked(kept: Var[Boolean]): Var[Boolean] = {
    val mine = own(Var(kept()))
    mine.attach(on => if (kept() != on) kept @= on)
    follow[Boolean](kept, on => if (mine() != on) mine @= on)
    mine
  }

  override def kind: String = "CallSettingsView"
}

object CallSettingsView {
  /** How wide it is unless it is told: a dialog's inside. */
  def width: Double = DialogPanel.width - DialogPanel.textSize() * 2.9
  /** The tallest the camera's picture is drawn, so the whole fits a laptop's screen. */
  val tallest: Double = 180.0
  /** The narrowest the speakers are drawn with the test beside them; narrower, it goes under them. */
  val narrowest: Double = 200.0
}
