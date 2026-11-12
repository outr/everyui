package everyui.render

import everyui.Permission
import everyui.call.{CallEngine, DeviceKind, MediaDevice, MediaRefusal}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.typedarray.Float32Array

/** Calls (`everyui.call`) and the devices they use, in a browser: LiveKit's client carries the call (LiveKitEngine),
  * and `navigator.mediaDevices` lists the microphones, cameras and speakers, asks for them and meters a microphone.
  * Only a secure context (https, or localhost) has any of it. The browser's own "default" and "communications"
  * entries are left out of the lists: no choice (None) is the system's own. */
object CallAccess {
  private def window: js.Dynamic = dom.window.asInstanceOf[js.Dynamic]
  private def media: js.Dynamic = window.navigator.mediaDevices

  def engine: CallEngine = LiveKitEngine

  def supported(): Boolean = window.isSecureContext.asInstanceOf[Boolean] && LiveKitRoom.defined(media) &&
    js.typeOf(media.getUserMedia) == "function" && js.typeOf(window.RTCPeerConnection) == "function"

  def speakersChosen(): Boolean = supported() && js.typeOf(window.HTMLMediaElement.prototype.setSinkId) == "function"

  def devices(found: Vector[MediaDevice] => Unit): Unit =
    if (!supported()) found(Vector.empty)
    else LiveKitRoom.after(media.enumerateDevices())(list => {
      val all = list.asInstanceOf[js.Array[js.Dynamic]].toVector
      val kinds = List("audioinput" -> DeviceKind.Microphone, "videoinput" -> DeviceKind.Camera,
        "audiooutput" -> DeviceKind.Speaker)
      found(kinds.flatMap { case (named, kind) =>
        all.filter(d => String.valueOf(d.kind) == named).map(d => (String.valueOf(d.deviceId), String.valueOf(d.label)))
          .filter { case (id, _) => id.nonEmpty && id != "default" && id != "communications" }
          .zipWithIndex.map { case ((id, label), i) => MediaDevice(id, label, kind, i) }
      }.toVector)
    }, _ => found(Vector.empty))

  /** `changed` on a device plugged in or taken out, and `permission` as the person changes one in the site's
    * settings (where the browser's Permissions API knows the microphone and the camera). */
  def watch(changed: () => Unit, permission: (DeviceKind, Permission) => Unit): Unit = if (supported()) {
    media.addEventListener("devicechange", ((_: js.Any) => changed()): js.Function1[js.Any, Unit])
    val permissions = window.navigator.permissions
    if (LiveKitRoom.defined(permissions)) {
      List("microphone" -> DeviceKind.Microphone, "camera" -> DeviceKind.Camera).foreach { case (named, kind) =>
        try {
          LiveKitRoom.after(permissions.query(js.Dynamic.literal(name = named)))(found => {
            val status = found.asInstanceOf[js.Dynamic]
            val tell = () => permission(kind, String.valueOf(status.state) match {
              case "granted" => Permission.Granted
              case "denied" => Permission.Refused
              case _ => Permission.NotAsked
            })
            tell()
            status.onchange = (() => tell()): js.Function0[Unit]
          }, _ => ())
        }
        catch {
          // a browser that does not know the name throws rather than rejecting
          case _: Throwable => ()
        }
      }
    }
  }

  def ask(microphone: Boolean, camera: Boolean, mic: Option[String], cam: Option[String],
          done: Option[MediaRefusal] => Unit): Unit =
    if (!supported()) done(Some(MediaRefusal.Unavailable))
    else if (!microphone && !camera) done(None)
    else LiveKitRoom.after(media.getUserMedia(js.Dynamic.literal(audio = wanted(microphone, mic),
      video = wanted(camera, cam))))(stream => {
      stop(stream.asInstanceOf[js.Dynamic])
      done(None)
    }, e => done(Some(refusal(e))))

  /** The microphone (`device`, or the system's own) heard through an analyser, its loudness told every 50 ms as 0 to
    * 1 (-60 dB to 0 dB), until the function answered is called. A page that has not been used yet may hear silence:
    * the browser starts sound only after a click, so a meter is best started from one. */
  def meter(device: Option[String], opened: () => Unit, level: Double => Unit,
            refused: MediaRefusal => Unit): () => Unit =
    if (!supported()) {
      refused(MediaRefusal.Unavailable)
      () => ()
    }
    else {
      var stopped = false
      var close: () => Unit = () => ()
      LiveKitRoom.after(media.getUserMedia(js.Dynamic.literal(audio = wanted(true, device))))(got => {
        val stream = got.asInstanceOf[js.Dynamic]
        if (stopped) stop(stream)
        else {
          opened()
          val hearing = analyse(stream, level)
          close = () => {
            hearing()
            stop(stream)
          }
        }
      }, e => if (!stopped) refused(refusal(e)))
      () => {
        stopped = true
        close()
      }
    }

  /** `stream`'s sound heard through an analyser, its loudness told every 50 ms as 0 to 1 (-60 dB to 0 dB), until the
    * function answered is called (the stream itself is the caller's to stop). A page not used yet starts its sound
    * suspended, and the analyser hears silence: it is let run at the first press of a key or a pointer. */
  def analyse(stream: js.Dynamic, level: Double => Unit): () => Unit = {
    val kind = if (LiveKitRoom.defined(window.AudioContext)) window.AudioContext else window.webkitAudioContext
    val context = js.Dynamic.newInstance(kind)()
    val analyser = context.createAnalyser()
    analyser.fftSize = 1024
    context.createMediaStreamSource(stream).connect(analyser)
    val wake: js.Function1[js.Any, Unit] = _ => if (String.valueOf(context.state) == "suspended") context.resume()
    val unwake = () => List("pointerdown", "keydown").foreach(e => dom.document.removeEventListener(e, wake, true))
    if (String.valueOf(context.state) == "suspended") {
      context.resume()
      List("pointerdown", "keydown").foreach(e => dom.document.addEventListener(e, wake, true))
    }
    val samples = new Float32Array(1024)
    val timer = dom.window.setInterval(() => {
      analyser.getFloatTimeDomainData(samples)
      var sum = 0.0
      var i = 0
      while (i < samples.length) {
        sum += samples(i) * samples(i)
        i += 1
      }
      val rms = math.sqrt(sum / samples.length)
      level(if (rms <= 0.0) 0.0 else math.max(0.0, math.min(1.0, (20.0 * math.log10(rms) + 60.0) / 60.0)))
    }, 50)
    () => {
      dom.window.clearInterval(timer)
      unwake()
      context.close()
    }
  }

  /** Why the browser would not give a microphone, a camera or a screen (what getUserMedia, getDisplayMedia or
    * LiveKit rejected with). */
  def refusal(e: Any): MediaRefusal = {
    val said = WebErrors.message(e).toLowerCase
    WebErrors.name(e) match {
      case "NotAllowedError" | "PermissionDeniedError" =>
        if (!window.isSecureContext.asInstanceOf[Boolean] || said.contains("policy") || said.contains("system"))
          MediaRefusal.ByPolicy
        else MediaRefusal.ByPerson
      case "SecurityError" | "DeviceUnsupportedError" => MediaRefusal.ByPolicy
      case "NotFoundError" | "DevicesNotFoundError" | "OverconstrainedError" => MediaRefusal.NoDevice
      case "NotReadableError" | "TrackStartError" | "AbortError" => MediaRefusal.InUse
      case _ => MediaRefusal.Failed(WebErrors.message(e))
    }
  }

  private def wanted(on: Boolean, device: Option[String]): js.Any =
    if (!on) false
    else device.map(id => js.Dynamic.literal(deviceId = js.Dynamic.literal(exact = id)): js.Any).getOrElse(true)

  private def stop(stream: js.Dynamic): Unit =
    stream.getTracks().asInstanceOf[js.Array[js.Dynamic]].foreach(t => t.stop())
}
