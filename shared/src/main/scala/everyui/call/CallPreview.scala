package everyui.call

import reactify.*

/** This person's camera and microphone before a call, as a meeting's "ready to join?" page shows them: the camera's
  * picture for a [[CameraPreview]] to draw, the microphone's `level` for a [[MicLevel]], each turned on and off
  * (`cameraOn`, `micOn`, for a [[MediaToggle]]), and where each stands (`cameraState`, `micState`: opening, live, off,
  * or refused, missing or failed, for the app to word). Both are opened as it is made, together, under one prompt
  * where the platform asks; one turned off is closed, its light off, and opened again when it is turned on.
  *
  * The devices are the ones a call uses (`camera` and `microphone` are `MediaDevices.camera` and `.microphone`, kept on
  * the device): another chosen while previewing is opened in place of the one open, which a browser gives without a
  * new prompt once it has been let. `Call.join` with the preview takes its open tracks over and sends them as the call
  * starts, so the device is not opened again (no second prompt, no light blinking off and on), and starts with the
  * camera and the microphone on or off as they are here; one refused starts off. A preview never joined is let go
  * with `dispose`, which closes both.
  *
  * The engine is the platform's (LiveKit's in a browser, which is loaded as the preview opens) or a call's own; it
  * must be the engine of the call that joins with it, or the call opens the devices again. Where there is none (the
  * desktop, Android, a Roku, an Apple TV for now) both are `PreviewState.Unavailable`.
  *
  * {{{
  *   val preview = new CallPreview
  *   val picture = new CameraPreview(preview)
  *   val meter = new MicLevel(preview.level())
  *   val toggles = List(new MediaToggle(preview.micOn, DeviceKind.Microphone),
  *     new MediaToggle(preview.cameraOn, DeviceKind.Camera))
  *   denied.visible := preview.cameraState().isInstanceOf[PreviewState.Denied]
  *   join.event.click.attach(_ => call.join(url, token, preview))
  *   ...
  *   preview.dispose()   // left without joining
  * }}} */
class CallPreview(val engine: CallEngine = CallEngine.default, startCamera: Boolean = true,
                  startMic: Boolean = true) {
  private val camNow: Var[PreviewState] = Var(PreviewState.Off)
  private val micNow: Var[PreviewState] = Var(PreviewState.Off)
  private val heardNow: Var[Double] = Var(0.0)
  // each kind's latest opening, so that an answer to an older one (superseded by another device) is not heard
  private var asked: Map[DeviceKind, Int] = Map.empty
  private var serial = 0
  private var handedOver = false
  private var over = false
  private var letGo: List[() => Unit] = Nil

  /** The camera previewed and sent, by id (None: the system's own): `MediaDevices.camera`. */
  val camera: Var[Option[String]] = MediaDevices.camera
  /** The microphone previewed and sent, by id (None: the system's own): `MediaDevices.microphone`. */
  val microphone: Var[Option[String]] = MediaDevices.microphone
  /** Whether the camera is on: off closes it, and a call joined from here starts without it. */
  val cameraOn: Var[Boolean] = Var(startCamera)
  /** Whether the microphone is on: off closes it, and a call joined from here starts muted. */
  val micOn: Var[Boolean] = Var(startMic)
  /** Where the camera stands. */
  val cameraState: Val[PreviewState] = camNow
  /** Where the microphone stands. */
  val micState: Val[PreviewState] = micNow
  /** How loud the microphone hears the room, from 0 (silence) to 1 (-60 dB to 0 dB); 0 while it is not live. */
  val level: Val[Double] = heardNow

  private[call] val media: PreviewMedia = engine.preview(new PreviewFeed(this))

  /** Whether a call took its devices over (`Call.join`): it no longer opens or closes them. */
  def handed: Boolean = handedOver

  /** Close the camera and the microphone, their lights off, where no call took them over; it previews nothing more.
    * Its views are taken apart by their own `dispose`. */
  def dispose(): Unit = if (!over) {
    over = true
    release()
    if (!handedOver) {
      media.stop()
      asked = Map.empty
      camNow @= PreviewState.Off
      micNow @= PreviewState.Off
      heardNow @= 0.0
    }
  }

  /** Its devices, open or opening, for a call to send: it stops following its switches and choices. */
  private[call] def handOver(): PreviewMedia = {
    if (!handedOver && !over) {
      handedOver = true
      release()
    }
    media
  }

  private[call] def heard(l: Double): Unit =
    if (!over && micNow() == PreviewState.Live) heardNow @= math.max(0.0, math.min(1.0, l))

  private[call] def lost(kind: DeviceKind, why: MediaRefusal): Unit =
    if (active && on(kind) && stateOf(kind)() == PreviewState.Live) {
      asked -= kind
      media.close(kind)
      settle(kind, PreviewState.of(why))
    }

  private def active: Boolean = !over && !handedOver

  private def on(kind: DeviceKind): Boolean = if (kind == DeviceKind.Camera) cameraOn() else micOn()

  private def stateOf(kind: DeviceKind): Var[PreviewState] = if (kind == DeviceKind.Camera) camNow else micNow

  private def settle(kind: DeviceKind, state: PreviewState): Unit = {
    stateOf(kind) @= state
    if (kind == DeviceKind.Microphone && state != PreviewState.Live) heardNow @= 0.0
  }

  private def open(kinds: List[DeviceKind]): Unit = {
    val wanted = kinds.filter(on).map(k => k -> MediaDevices.chosen(k)).toMap
    wanted.keys.foreach { k =>
      serial += 1
      asked += k -> serial
      settle(k, PreviewState.Starting)
    }
    if (wanted.nonEmpty) {
      val mine = asked
      media.open(wanted, (k, why) => answered(k, mine.getOrElse(k, -1), why))
    }
  }

  private def answered(kind: DeviceKind, n: Int, why: Option[MediaRefusal]): Unit =
    if (active && on(kind) && asked.get(kind).contains(n)) why match {
      case None =>
        MediaDevices.granted(kind)
        settle(kind, PreviewState.Live)
      case Some(r) =>
        MediaDevices.refused(kind, r)
        asked -= kind
        settle(kind, PreviewState.of(r))
    }

  private def turned(kind: DeviceKind, now: Boolean): Unit = if (active) {
    if (now) open(List(kind))
    else {
      asked -= kind
      media.close(kind)
      settle(kind, PreviewState.Off)
    }
  }

  private def release(): Unit = {
    letGo.foreach(f => f())
    letGo = Nil
  }

  private def follow[T](v: Var[T], f: T => Unit): Unit = {
    val r = v.attach(f)
    letGo = (() => v.reactions -= r) :: letGo
  }

  follow[Boolean](cameraOn, now => turned(DeviceKind.Camera, now))
  follow[Boolean](micOn, now => turned(DeviceKind.Microphone, now))
  // another device chosen: opened in place of the one open (a refused one is tried again with it)
  follow[Option[String]](camera, _ => if (active && on(DeviceKind.Camera)) open(List(DeviceKind.Camera)))
  follow[Option[String]](microphone, _ => if (active && on(DeviceKind.Microphone)) open(List(DeviceKind.Microphone)))
  open(List(DeviceKind.Camera, DeviceKind.Microphone))
}
