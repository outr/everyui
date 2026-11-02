package everyui.call

import reactify.*

/** A call: this person in a room of a media server with others, hearing and seeing them and sent their own
  * microphone, camera and screen as they choose. The app's server mints the token that lets them in (for LiveKit, a
  * JWT naming the room and the person) and keeps whatever it knows of the room; the call only joins it.
  *
  * Who is there is `participants` (everyone else) and `local` (this person, once in), each as the room last told of
  * them: whether they are speaking, their microphone, their camera's and screen's tracks for a [[VideoTile]] or a
  * [[ScreenShareView]] to draw, how well they are heard. The others' sound plays by itself while `audible`, through
  * the chosen speaker (`MediaDevices.speaker`); a browser may hold it back until the page is used (`audioBlocked`),
  * when a click on anything calls `startAudio`.
  *
  * The engine is the platform's (`CallEngine.default`: LiveKit's client in a browser, which is loaded the first time
  * a call connects) or the app's own. Where there is none (`supported` false: the desktop, Android, a Roku, an Apple
  * TV for now) a call ends as it connects, with `CallEnd.Unavailable`.
  *
  * {{{
  *   val call = new Call
  *   call.state.attach {
  *     case CallState.Ended(why) => bar.visible @= false
  *     case _ =>
  *   }
  *   call.connect(server.liveKitUrl, token, microphone = true)
  *   muteButton.event.click.attach(_ => call.setMicrophone(!call.microphone()))
  *   grid.children.bindMaking(call.everyone().map(_.identity))(id => new VideoTile(call, id))
  *   ...
  *   call.leave()
  * }}} */
class Call(val engine: CallEngine = CallEngine.default) {
  private val stateNow: Var[CallState] = Var(CallState.Idle)
  private val remoteNow: Var[Vector[CallParticipant]] = Var(Vector.empty)
  private val localNow: Var[Option[CallParticipant]] = Var(None)
  private val sendingNow: Var[Set[TrackSource]] = Var(Set.empty)
  private val blockedNow: Var[Boolean] = Var(false)

  /** Where it stands: idle, connecting, connected, reconnecting, or ended and why. */
  val state: Val[CallState] = stateNow
  /** Everyone else in the room, in the order the room gives them. */
  val participants: Val[Vector[CallParticipant]] = remoteNow
  /** This person, once in the room. */
  val local: Val[Option[CallParticipant]] = localNow
  /** This person, then everyone else: a grid's tiles. */
  val everyone: Val[Vector[CallParticipant]] = Val(localNow().toVector ++ remoteNow())
  /** Whether this person's microphone is sent. */
  val microphone: Val[Boolean] = Val(sendingNow().contains(TrackSource.Microphone))
  /** Whether this person's camera is sent. */
  val camera: Val[Boolean] = Val(sendingNow().contains(TrackSource.Camera))
  /** Whether this person shares a screen. */
  val screen: Val[Boolean] = Val(sendingNow().contains(TrackSource.Screen))
  /** Whether the others are heard; off keeps every one of them silent, still in the call. */
  val audible: Var[Boolean] = Var(true)
  /** Whether the browser holds the others' sound back until the page is used: `startAudio` from a click lets it. */
  val audioBlocked: Val[Boolean] = blockedNow

  private val room: CallRoom = engine.room(new CallFeed(this))
  // what was asked to be sent before the room was reached, sent once it is
  private var wishes: List[(TrackSource, Boolean, Option[MediaRefusal] => Unit)] = Nil
  private var letGo: List[() => Unit] = Nil

  /** Whether its engine can run a call here. */
  def supported: Boolean = engine.supported

  /** Whether it has connected and not ended. */
  def live: Boolean = stateNow() == CallState.Connected || stateNow() == CallState.Reconnecting

  /** Join the room at `url` with `token`, sending the microphone and the camera as asked once in. Only once: a call
    * that ended is not joined again. */
  def connect(url: String, token: String, microphone: Boolean = true, camera: Boolean = false): Unit =
    if (stateNow() == CallState.Idle) {
      if (microphone) wish(TrackSource.Microphone, true, _ => ())
      if (camera) wish(TrackSource.Camera, true, _ => ())
      stateNow @= CallState.Connecting
      follow(MediaDevices.microphone, DeviceKind.Microphone)
      follow(MediaDevices.camera, DeviceKind.Camera)
      follow(MediaDevices.speaker, DeviceKind.Speaker)
      val hearing = audible.attach(on => room.audible(on))
      letGo = (() => audible.reactions -= hearing) :: letGo
      if (!audible()) room.audible(false)
      val devices = List[DeviceKind](DeviceKind.Microphone, DeviceKind.Camera, DeviceKind.Speaker)
        .flatMap(k => MediaDevices.chosen(k).map(k -> _)).toMap
      room.connect(url, token, devices)
    }

  /** Leave the room: everything it holds let go, and the call ended (`CallEnd.Left`). */
  def leave(): Unit = stateNow() match {
    case CallState.Ended(_) => ()
    case CallState.Idle => stateIs(CallState.Ended(CallEnd.Left))
    case _ =>
      room.leave()
      stateIs(CallState.Ended(CallEnd.Left))
  }

  /** Send the microphone, or stop: `done` hears why it could not be (None: done). Asked before the room is reached,
    * it is done once it is. A call that ended answers `MediaRefusal.Unavailable` to turning one on. */
  def setMicrophone(on: Boolean, done: Option[MediaRefusal] => Unit = _ => ()): Unit =
    set(TrackSource.Microphone, on, done)

  /** Send the camera, or stop, as `setMicrophone`. */
  def setCamera(on: Boolean, done: Option[MediaRefusal] => Unit = _ => ()): Unit = set(TrackSource.Camera, on, done)

  /** Share a screen (a window, a tab: the browser asks which, and the person may cancel, `MediaRefusal.ByPerson`), or
    * stop, as `setMicrophone`. Its sound is shared too where the browser offers it. */
  def setScreen(on: Boolean, done: Option[MediaRefusal] => Unit = _ => ()): Unit = set(TrackSource.Screen, on, done)

  /** Let the sound the browser held back play (`audioBlocked`): from a click. */
  def startAudio(): Unit = if (live) room.startAudio()

  private def set(source: TrackSource, on: Boolean, done: Option[MediaRefusal] => Unit): Unit = stateNow() match {
    case CallState.Ended(_) => done(if (on) Some(MediaRefusal.Unavailable) else None)
    case CallState.Idle | CallState.Connecting => wish(source, on, done)
    case _ => send(source, on, done)
  }

  private def wish(source: TrackSource, on: Boolean, done: Option[MediaRefusal] => Unit): Unit =
    wishes = wishes.filterNot(_._1 == source) :+ ((source, on, done))

  private def send(source: TrackSource, on: Boolean, done: Option[MediaRefusal] => Unit): Unit = {
    val kind = source match {
      case TrackSource.Microphone => Some(DeviceKind.Microphone)
      case TrackSource.Camera => Some(DeviceKind.Camera)
      case _ => None
    }
    room.send(source, on, kind.flatMap(MediaDevices.chosen), why => {
      if (on) kind.foreach(k => why match {
        case None => MediaDevices.granted(k)
        case Some(r) => MediaDevices.refused(k, r)
      })
      done(why)
    })
  }

  private def follow(choice: Var[Option[String]], kind: DeviceKind): Unit = {
    val o = choice.attach(id => if (live) room.device(kind, id))
    letGo = (() => choice.reactions -= o) :: letGo
  }

  private[call] def show(track: CallTrack, into: CallVideo): Unit = if (live) room.show(track, into)
  private[call] def hide(track: CallTrack, into: CallVideo): Unit = room.hide(track, into)

  private[call] def stateIs(next: CallState): Unit = stateNow() match {
    case CallState.Ended(_) => ()
    case _ =>
      next match {
        case CallState.Ended(_) =>
          letGo.foreach(f => f())
          letGo = Nil
          val left = wishes
          wishes = Nil
          left.foreach(w => w._3(if (w._2) Some(MediaRefusal.Unavailable) else None))
          remoteNow @= Vector.empty
          localNow @= None
          sendingNow @= Set.empty
          blockedNow @= false
          stateNow @= next
        case CallState.Connected =>
          stateNow @= next
          val asked = wishes
          wishes = Nil
          asked.foreach(w => send(w._1, w._2, w._3))
        case _ => stateNow @= next
      }
  }

  private[call] def peopleAre(local: Option[CallParticipant], remote: Vector[CallParticipant]): Unit =
    if (!stateNow().isInstanceOf[CallState.Ended]) {
      if (local != localNow()) localNow @= local
      if (remote != remoteNow()) remoteNow @= remote
    }

  private[call] def sendingIs(source: TrackSource, on: Boolean): Unit =
    if (!stateNow().isInstanceOf[CallState.Ended]) {
      val next = if (on) sendingNow() + source else sendingNow() - source
      if (next != sendingNow()) sendingNow @= next
    }

  private[call] def blockedIs(blocked: Boolean): Unit = if (blocked != blockedNow()) blockedNow @= blocked
}
