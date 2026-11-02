package everyui.render

import everyui.call.*
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** One call in LiveKit's client (LiveKitEngine): a `Room` of its, joined once its script is in the page. What the room
  * says (who is there, their tracks muted or not, who speaks, the connection) is read afresh into `CallParticipant`s
  * on each of its events. Each remote sound plays in a hidden `<audio>` element of its own in the page's body,
  * silenced while the call is not `audible`; each picture is a `<video>` drawn into its `CallVideo`'s box. The adaptive
  * stream is on, so a picture is sent only as large as its box draws it, and not at all while none does. */
class LiveKitRoom(feed: CallFeed) extends CallRoom {
  private var room: js.Dynamic = null
  private var left = false
  // the room reached: until then its own end (a join failure, said of any) waits for why joining failed
  private var joined = false
  private var hearing = true
  // each remote sound's element, by its track's sid
  private var sounds: Map[String, html.Element] = Map.empty
  // each picture drawn, by its track's sid and the box it is drawn in
  private var pictures: Map[(String, CallVideo), html.Element] = Map.empty

  def connect(url: String, token: String, devices: Map[DeviceKind, String]): Unit = LiveKitScript.load { ok =>
    if (!left) {
      if (ok) open(url, token, devices) else feed.state(CallState.Ended(CallEnd.Unavailable))
    }
  }

  private def open(url: String, token: String, devices: Map[DeviceKind, String]): Unit = {
    val client = LiveKitScript.client
    if (!js.isUndefined(client.setLogLevel)) client.setLogLevel("warn")
    val options = js.Dynamic.literal(adaptiveStream = true, dynacast = true)
    devices.get(DeviceKind.Microphone).foreach(id => options.audioCaptureDefaults = js.Dynamic.literal(deviceId = id))
    devices.get(DeviceKind.Camera).foreach(id => options.videoCaptureDefaults = js.Dynamic.literal(deviceId = id))
    devices.get(DeviceKind.Speaker).foreach(id => options.audioOutput = js.Dynamic.literal(deviceId = id))
    val r = js.Dynamic.newInstance(client.Room)(options)
    room = r
    LiveKitRoom.refreshing.foreach(event => r.on(event, (() => refresh()): js.Function0[Unit]))
    r.on("trackSubscribed", ((track: js.Dynamic) => {
      if (String.valueOf(track.kind) == "audio") play(track)
      refresh()
    }): js.Function1[js.Dynamic, Unit])
    r.on("trackUnsubscribed", ((track: js.Dynamic) => {
      silence(String.valueOf(track.sid), track)
      refresh()
    }): js.Function1[js.Dynamic, Unit])
    r.on("reconnecting", (() => feed.state(CallState.Reconnecting)): js.Function0[Unit])
    r.on("signalReconnecting", (() => feed.state(CallState.Reconnecting)): js.Function0[Unit])
    r.on("reconnected", (() => {
      feed.state(CallState.Connected)
      refresh()
    }): js.Function0[Unit])
    r.on("disconnected", ((reason: js.Any) => if (joined) {
      release()
      feed.state(CallState.Ended(LiveKitRoom.ended(reason)))
    }): js.Function1[js.Any, Unit])
    r.on("audioPlaybackChanged", (() => blocked()): js.Function0[Unit])
    LiveKitRoom.after(r.connect(url, token))(_ => if (left) r.disconnect() else {
      joined = true
      feed.state(CallState.Connected)
      blocked()
      refresh()
    }, e => if (!left) {
      release()
      feed.state(CallState.Ended(LiveKitRoom.failed(e)))
    })
  }

  def leave(): Unit = if (!left) {
    left = true
    release()
    if (room != null) room.disconnect()
  }

  def send(source: TrackSource, on: Boolean, device: Option[String], done: Option[MediaRefusal] => Unit): Unit =
    if (room == null || left) done(if (on) Some(MediaRefusal.Unavailable) else None)
    else {
      val local = room.localParticipant
      val capture: js.Any = device.map(id => js.Dynamic.literal(deviceId = id): js.Any).getOrElse(js.undefined)
      val asked: Option[js.Dynamic] = source match {
        case TrackSource.Microphone => Some(local.setMicrophoneEnabled(on, capture))
        case TrackSource.Camera => Some(local.setCameraEnabled(on, capture))
        // its sound too, where the browser offers it (a tab's)
        case TrackSource.Screen => Some(local.setScreenShareEnabled(on, js.Dynamic.literal(audio = true)))
        case TrackSource.ScreenAudio => None
      }
      asked match {
        case None => done(Some(MediaRefusal.Failed("a screen's sound is shared with the screen")))
        case Some(p) => LiveKitRoom.after(p)(_ => {
          refresh()
          done(None)
        }, e => {
          refresh()
          done(Some(CallAccess.refusal(e)))
        })
      }
    }

  def device(kind: DeviceKind, id: Option[String]): Unit = if (room != null && !left) {
    val named = kind match {
      case DeviceKind.Microphone => "audioinput"
      case DeviceKind.Camera => "videoinput"
      case DeviceKind.Speaker => "audiooutput"
    }
    // the system's own: the one Chrome lists as "default", asked for loosely, since Firefox lists none (a camera has
    // none anywhere, and stays as it is)
    if (id.nonEmpty || kind != DeviceKind.Camera) {
      LiveKitRoom.after(room.switchActiveDevice(named, id.getOrElse("default"), id.nonEmpty))(_ => (),
        e => Platform.log("[call] could not switch " + named + ": " + WebErrors.message(e)))
    }
  }

  def audible(on: Boolean): Unit = {
    hearing = on
    sounds.values.foreach(el => el.asInstanceOf[js.Dynamic].muted = !on)
  }

  def startAudio(): Unit = if (room != null && !left) {
    LiveKitRoom.after(room.startAudio())(_ => blocked(), _ => blocked())
  }

  def show(track: CallTrack, into: CallVideo): Unit =
    if (room != null && !left && !pictures.contains((track.id, into))) find(track.id).foreach { t =>
      val el = t.attach().asInstanceOf[html.Element]
      el.setAttribute("playsinline", "")
      el.asInstanceOf[js.Dynamic].muted = true
      el.style.cssText = "position:absolute;left:0;top:0;width:100%;height:100%;display:block;" +
        "object-fit:" + (if (into.cover()) "cover" else "contain") +
        (if (into.mirror()) ";transform:scaleX(-1)" else "")
      into.node.el.appendChild(el)
      pictures += (track.id, into) -> el
    }

  def hide(track: CallTrack, into: CallVideo): Unit = pictures.get((track.id, into)).foreach { el =>
    find(track.id).foreach(t => t.detach(el))
    if (el.parentNode != null) el.parentNode.removeChild(el)
    pictures -= ((track.id, into))
  }

  // the room's track with `sid`: a remote one subscribed to, or this person's own
  private def find(sid: String): Option[js.Dynamic] = if (room == null) None else {
    var found: Option[js.Dynamic] = None
    val look = (p: js.Dynamic) => p.trackPublications.forEach(((pub: js.Dynamic) => {
      if (found.isEmpty && String.valueOf(pub.trackSid) == sid && LiveKitRoom.defined(pub.track)) {
        found = Some(pub.track)
      }
    }): js.Function1[js.Dynamic, Unit])
    look(room.localParticipant)
    room.remoteParticipants.forEach(((p: js.Dynamic) => look(p)): js.Function1[js.Dynamic, Unit])
    found
  }

  private def play(track: js.Dynamic): Unit = {
    val sid = String.valueOf(track.sid)
    if (!sounds.contains(sid)) {
      val el = track.attach().asInstanceOf[html.Element]
      el.style.display = "none"
      el.asInstanceOf[js.Dynamic].muted = !hearing
      dom.document.body.appendChild(el)
      sounds += sid -> el
    }
  }

  private def silence(sid: String, track: js.Dynamic): Unit = sounds.get(sid).foreach { el =>
    track.detach(el)
    if (el.parentNode != null) el.parentNode.removeChild(el)
    sounds -= sid
  }

  private def blocked(): Unit = if (room != null) feed.audioBlocked(!room.canPlaybackAudio.asInstanceOf[Boolean])

  // every element let go of: the sounds' taken out of the page, the pictures' out of their boxes
  private def release(): Unit = {
    sounds.values.foreach(el => if (el.parentNode != null) el.parentNode.removeChild(el))
    sounds = Map.empty
    pictures.values.foreach(el => if (el.parentNode != null) el.parentNode.removeChild(el))
    pictures = Map.empty
  }

  private def refresh(): Unit = if (room != null && !left) {
    val local = room.localParticipant
    var remote = Vector.empty[CallParticipant]
    val add: js.Function1[js.Dynamic, Unit] = p => remote :+= LiveKitRoom.person(p, false)
    room.remoteParticipants.forEach(add)
    feed.people(Some(LiveKitRoom.person(local, true)), remote)
    feed.sending(TrackSource.Microphone, local.isMicrophoneEnabled.asInstanceOf[Boolean])
    feed.sending(TrackSource.Camera, local.isCameraEnabled.asInstanceOf[Boolean])
    feed.sending(TrackSource.Screen, local.isScreenShareEnabled.asInstanceOf[Boolean])
  }
}

object LiveKitRoom {
  /** The room's events after which who is there, and what they send, is read again. */
  val refreshing: List[String] = List("connected", "participantConnected", "participantDisconnected", "trackPublished",
    "trackUnpublished", "trackMuted", "trackUnmuted", "localTrackPublished", "localTrackUnpublished",
    "activeSpeakersChanged", "participantNameChanged", "connectionQualityChanged", "trackSubscriptionFailed")

  /** `ok` once `promise` resolves, with what it resolves to, or `failed` with what it rejects with. */
  private[render] def after(promise: js.Dynamic)(ok: Any => Unit, failed: Any => Unit): Unit =
    promise.`then`(ok: js.Function1[Any, Unit], failed: js.Function1[Any, Unit])

  private[render] def defined(v: js.Any): Boolean = v != null && !js.isUndefined(v)

  private def text(v: js.Any): String = if (defined(v)) String.valueOf(v) else ""

  /** A LiveKit participant as the call shows them. */
  def person(p: js.Dynamic, local: Boolean): CallParticipant = {
    val identity = text(p.identity)
    def track(source: String, as: TrackSource): Option[CallTrack] = {
      val pub = p.getTrackPublication(source)
      if (!defined(pub) || pub.isMuted.asInstanceOf[Boolean] || !defined(pub.track)) None
      else Some(CallTrack(text(pub.trackSid), as, identity))
    }
    val level = p.audioLevel
    CallParticipant(identity, text(p.name), local, p.isSpeaking.asInstanceOf[Boolean],
      if (defined(level)) level.asInstanceOf[Double] else 0.0, p.isMicrophoneEnabled.asInstanceOf[Boolean],
      track("camera", TrackSource.Camera), track("screen_share", TrackSource.Screen),
      quality(text(p.connectionQuality)))
  }

  def quality(q: String): CallQuality = q match {
    case "excellent" => CallQuality.Excellent
    case "good" => CallQuality.Good
    case "poor" => CallQuality.Poor
    case "lost" => CallQuality.Lost
    case _ => CallQuality.Unknown
  }

  /** Why the room says it ended (its DisconnectReason, a number). */
  def ended(reason: js.Any): CallEnd =
    if (!defined(reason)) CallEnd.Lost
    else reason.asInstanceOf[Double].toInt match {
      case 1 => CallEnd.Left
      case 2 => CallEnd.Replaced
      case 4 => CallEnd.Removed
      case 5 | 10 => CallEnd.Closed
      case 7 => CallEnd.Refused("could not join the room")
      case 11 | 12 | 13 => CallEnd.Closed
      case 15 => CallEnd.Failed("media failure")
      case 16 => CallEnd.Failed("agent error")
      // SERVER_SHUTDOWN, STATE_MISMATCH, MIGRATION, SIGNAL_CLOSE, CONNECTION_TIMEOUT, and anything unknown
      case _ => CallEnd.Lost
    }

  /** Why joining failed (a ConnectionError's reason, or whatever else was thrown). Its `reason`, a number, and not
    * its `reasonName`: the client wraps a server's refusal (a 401 for a token it did not sign) in an error of its own,
    * "could not establish signal connection", taking on the refusal's reason but keeping its own name for it. */
  def failed(e: Any): CallEnd = {
    val d = e.asInstanceOf[js.Dynamic]
    val said =
      if (defined(d.asInstanceOf[js.Any]) && defined(d.message)) String.valueOf(d.message) else String.valueOf(e)
    val reason = if (defined(d.reason) && js.typeOf(d.reason) == "number") d.reason.asInstanceOf[Double].toInt else -1
    if (WebErrors.name(e) != "ConnectionError") CallEnd.Failed(WebErrors.message(e))
    else reason match {
      // ConnectionErrorReason: NotAllowed 0, Cancelled 3, LeaveRequest 4
      case 0 => CallEnd.Refused(said)
      case 3 | 4 => CallEnd.Left
      case _ => CallEnd.Failed(said)
    }
  }
}
