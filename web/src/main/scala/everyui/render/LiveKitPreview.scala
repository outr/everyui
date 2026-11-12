package everyui.render

import everyui.call.*
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js

/** A call preview's camera and microphone in a browser (LiveKitEngine's `preview`): LiveKit's own local tracks, made
  * with its `createLocalTracks` before any room (its script loaded for it), so that the room the call joins publishes
  * those very tracks (`LiveKitRoom.take`, `whenOpen`) and the device is neither asked for nor opened again. Both are
  * asked for in one `getUserMedia`, under one prompt; where that fails for want of one of them (no camera plugged in,
  * or another app holding it), each is asked for alone, so the other still opens. Another device is opened in place of
  * the one open (the old track stopped first, as a camera may not open twice); the browser asks nothing once it has
  * been let. The camera is drawn in each preview's box as a `<video>` LiveKit attaches, and the microphone is heard
  * through an analyser (`CallAccess.analyse`) on the track itself. A track ending by itself (unplugged) is told as
  * lost, for want of the device. */
class LiveKitPreview(feed: PreviewFeed) extends PreviewMedia {
  private var stopped = false
  private var serial = 0
  // each kind's latest opening (a close moves it on too), so that an opening let go before it answers is stopped
  private var asked: Map[DeviceKind, Int] = Map.empty
  private var opening: Set[DeviceKind] = Set.empty
  private var tracks: Map[DeviceKind, js.Dynamic] = Map.empty
  // what waits for an opening to be done (a room taking the track over)
  private var waiting: Map[DeviceKind, List[Option[js.Dynamic] => Unit]] = Map.empty
  private var views: Vector[CameraPreview] = Vector.empty
  // each view's element, with the track it shows
  private var drawn: Map[CameraPreview, (js.Dynamic, html.Element)] = Map.empty
  private var hearing: () => Unit = () => ()

  def open(wanted: Map[DeviceKind, Option[String]], done: (DeviceKind, Option[MediaRefusal]) => Unit): Unit =
    if (!stopped) {
      val asking = wanted.filter { case (k, _) => k != DeviceKind.Speaker }
      asking.keys.foreach(close)
      val mine = asking.keys.map(k => k -> next(k)).toMap
      opening ++= asking.keys
      LiveKitScript.load { ok =>
        val still = asking.filter { case (k, _) => current(k, mine(k)) }
        if (!ok) still.keys.foreach(k => fail(k, MediaRefusal.Unavailable, done))
        else if (still.nonEmpty) create(still, mine, done)
      }
    }

  def close(kind: DeviceKind): Unit = {
    next(kind)
    opening -= kind
    tracks.get(kind).foreach { t =>
      if (kind == DeviceKind.Camera) views.foreach(unattach)
      else {
        hearing()
        hearing = () => ()
        feed.level(0.0)
      }
      tracks -= kind
      t.stop()
    }
    resolve(kind, None)
  }

  def show(into: CameraPreview): Unit = if (!stopped) {
    if (!views.contains(into)) views :+= into
    tracks.get(DeviceKind.Camera).foreach(t => attach(into, t))
  }

  def hide(into: CameraPreview): Unit = {
    unattach(into)
    views = views.filterNot(_ eq into)
  }

  def stop(): Unit = if (!stopped) {
    List[DeviceKind](DeviceKind.Camera, DeviceKind.Microphone).foreach(close)
    stopped = true
    views.foreach(unattach)
    views = Vector.empty
  }

  /** `f` with the open track of `kind` (LiveKit's LocalTrack) once its opening is done; None where none is open, or it
    * failed or was closed. */
  private[render] def whenOpen(kind: DeviceKind)(f: Option[js.Dynamic] => Unit): Unit = tracks.get(kind) match {
    case Some(t) => f(Some(t))
    case None =>
      if (opening.contains(kind)) waiting += kind -> (f :: waiting.getOrElse(kind, Nil))
      else f(None)
  }

  private def next(kind: DeviceKind): Int = {
    serial += 1
    asked += kind -> serial
    serial
  }

  private def current(kind: DeviceKind, n: Int): Boolean = !stopped && asked.get(kind).contains(n)

  private def create(wanted: Map[DeviceKind, Option[String]], mine: Map[DeviceKind, Int],
                     done: (DeviceKind, Option[MediaRefusal]) => Unit): Unit = {
    val options = js.Dynamic.literal(audio = capture(wanted, DeviceKind.Microphone),
      video = capture(wanted, DeviceKind.Camera))
    LiveKitRoom.after(LiveKitScript.client.createLocalTracks(options))(got => {
      val made = got.asInstanceOf[js.Array[js.Dynamic]].toVector
      made.foreach { t =>
        val k = LiveKitPreview.kindOf(t)
        // one closed (or another device asked for) while it opened is let go at once
        if (wanted.contains(k) && current(k, mine(k))) {
          install(k, t)
          done(k, None)
        }
        else t.stop()
      }
      wanted.keys.foreach(k => if (current(k, mine(k)) && !made.exists(t => LiveKitPreview.kindOf(t) == k)) {
        fail(k, MediaRefusal.NoDevice, done)
      })
    }, e => {
      val why = CallAccess.refusal(e)
      val still = wanted.filter { case (k, _) => current(k, mine(k)) }
      // one of the two missing or held keeps the other from opening: each alone
      if (still.size > 1 && (why == MediaRefusal.NoDevice || why == MediaRefusal.InUse))
        still.foreach { case (k, id) => create(Map(k -> id), mine, done) }
      else still.keys.foreach(k => fail(k, why, done))
    })
  }

  private def capture(wanted: Map[DeviceKind, Option[String]], kind: DeviceKind): js.Any = wanted.get(kind) match {
    case None => false
    case Some(None) => true
    case Some(Some(id)) => js.Dynamic.literal(deviceId = id)
  }

  private def install(kind: DeviceKind, t: js.Dynamic): Unit = {
    opening -= kind
    tracks += kind -> t
    val ended: js.Function0[Unit] = () => if (tracks.get(kind).exists(_ eq t)) feed.lost(kind, MediaRefusal.NoDevice)
    t.on("ended", ended)
    if (kind == DeviceKind.Camera) views.foreach(v => attach(v, t))
    else {
      val stream = js.Dynamic.newInstance(dom.window.asInstanceOf[js.Dynamic].MediaStream)(js.Array(t.mediaStreamTrack))
      hearing = CallAccess.analyse(stream, l => if (tracks.get(kind).exists(_ eq t)) feed.level(l))
    }
    resolve(kind, Some(t))
  }

  private def fail(kind: DeviceKind, why: MediaRefusal, done: (DeviceKind, Option[MediaRefusal]) => Unit): Unit = {
    opening -= kind
    resolve(kind, None)
    done(kind, Some(why))
  }

  private def resolve(kind: DeviceKind, track: Option[js.Dynamic]): Unit = waiting.get(kind).foreach { fs =>
    waiting -= kind
    fs.reverse.foreach(f => f(track))
  }

  private def attach(into: CameraPreview, t: js.Dynamic): Unit = {
    unattach(into)
    val el = t.attach().asInstanceOf[html.Element]
    el.setAttribute("playsinline", "")
    el.asInstanceOf[js.Dynamic].muted = true
    el.style.cssText = "position:absolute;left:0;top:0;width:100%;height:100%;display:block;" +
      "object-fit:" + (if (into.cover()) "cover" else "contain") +
      (if (into.mirror()) ";transform:scaleX(-1)" else "")
    into.picture.node.el.appendChild(el)
    drawn += into -> (t, el)
  }

  private def unattach(into: CameraPreview): Unit = drawn.get(into).foreach { case (t, el) =>
    t.detach(el)
    if (el.parentNode != null) el.parentNode.removeChild(el)
    drawn -= into
  }
}

object LiveKitPreview {
  /** What a LiveKit local track is of: its camera or its microphone. */
  def kindOf(t: js.Dynamic): DeviceKind =
    if (String.valueOf(t.kind) == "video") DeviceKind.Camera else DeviceKind.Microphone
}
