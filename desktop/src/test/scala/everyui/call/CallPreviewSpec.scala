package everyui.call

import everyui.FxSpec
import everyui.chat.Participant
import everyui.event.{PointerEvent, PointerKind}

/** A call's preview (`CallPreview`) over an engine standing in for LiveKit's: both devices opened under one ask and
  * live, the microphone's level heard; refused, missing and unavailable devices told apart; a device turned off
  * closed and opened again, another device chosen opened in its place, and an answer to an older opening not heard;
  * a preview let go closing both; and a call joined from it sending the very tracks it opened, with its mute carried
  * in. Its views: the camera drawn mirrored and filling or the placeholder and its words, the level as a meter, and
  * the toggles as switches. */
class CallPreviewSpec extends FxSpec {
  private class FakeTrack(val kind: DeviceKind, val device: Option[String]) {
    var stopped = false
  }

  private class FakeMedia(val feed: PreviewFeed) extends PreviewMedia {
    // each ask, and its answer still to come
    var opens: Vector[Map[DeviceKind, Option[String]]] = Vector.empty
    var answers: Vector[(DeviceKind, Option[MediaRefusal]) => Unit] = Vector.empty
    var tracks: Map[DeviceKind, FakeTrack] = Map.empty
    var made: Vector[FakeTrack] = Vector.empty
    var closed: Vector[DeviceKind] = Vector.empty
    var shown: Vector[CameraPreview] = Vector.empty
    var stopped = false

    def open(wanted: Map[DeviceKind, Option[String]], done: (DeviceKind, Option[MediaRefusal]) => Unit): Unit = {
      wanted.keys.foreach(k => tracks.get(k).foreach(_.stopped = true))
      tracks --= wanted.keys
      opens :+= wanted
      answers :+= ((k: DeviceKind, why: Option[MediaRefusal]) => {
        if (why.isEmpty) {
          val t = new FakeTrack(k, wanted(k))
          tracks += k -> t
          made :+= t
        }
        done(k, why)
      })
    }
    def close(kind: DeviceKind): Unit = {
      closed :+= kind
      tracks.get(kind).foreach(_.stopped = true)
      tracks -= kind
    }
    def show(into: CameraPreview): Unit = shown :+= into
    def hide(into: CameraPreview): Unit = shown = shown.filterNot(_ eq into)
    def stop(): Unit = {
      stopped = true
      tracks.values.foreach(_.stopped = true)
    }

    /** The last ask answered, for `kind`. */
    def answer(kind: DeviceKind, why: Option[MediaRefusal] = None, ask: Int = -1): Unit =
      answers(if (ask < 0) answers.size - 1 else ask)(kind, why)
  }

  private class FakeRoom(val feed: CallFeed) extends CallRoom {
    var taken: Option[PreviewMedia] = None
    var published: Vector[FakeTrack] = Vector.empty
    var opened: Vector[TrackSource] = Vector.empty

    override def take(media: PreviewMedia): Unit = taken = Some(media)
    def connect(url: String, token: String, devices: Map[DeviceKind, String]): Unit = ()
    def leave(): Unit = taken.foreach(_.stop())
    def send(source: TrackSource, on: Boolean, device: Option[String], done: Option[MediaRefusal] => Unit): Unit = {
      val kind = if (source == TrackSource.Camera) DeviceKind.Camera else DeviceKind.Microphone
      taken.collect { case m: FakeMedia => m }.flatMap(_.tracks.get(kind)) match {
        case Some(t) if on => published :+= t
        case _ => if (on) opened :+= source
      }
      feed.sending(source, on)
      done(None)
    }
    def device(kind: DeviceKind, id: Option[String]): Unit = ()
    def audible(on: Boolean): Unit = ()
    def startAudio(): Unit = ()
    def show(track: CallTrack, into: CallVideo): Unit = ()
    def hide(track: CallTrack, into: CallVideo): Unit = ()
  }

  private class FakeEngine extends CallEngine {
    var media: Vector[FakeMedia] = Vector.empty
    var rooms: Vector[FakeRoom] = Vector.empty
    def name: String = "fake"
    def supported: Boolean = true
    def room(feed: CallFeed): CallRoom = {
      val r = new FakeRoom(feed)
      rooms :+= r
      r
    }
    override def preview(feed: PreviewFeed): PreviewMedia = {
      val m = new FakeMedia(feed)
      media :+= m
      m
    }
  }

  private def previewing(): (CallPreview, FakeMedia, FakeEngine) = {
    val engine = new FakeEngine
    val preview = new CallPreview(engine)
    (preview, engine.media.head, engine)
  }

  private def live(): (CallPreview, FakeMedia, FakeEngine) = {
    val (preview, media, engine) = previewing()
    media.answer(DeviceKind.Camera)
    media.answer(DeviceKind.Microphone)
    (preview, media, engine)
  }

  "A call's preview" should {
    "open both devices under one ask, go live, and hear the microphone" in fx {
      val (preview, media, _) = previewing()
      media.opens shouldBe Vector(Map(DeviceKind.Camera -> MediaDevices.camera(),
        DeviceKind.Microphone -> MediaDevices.microphone()))
      preview.cameraState() shouldBe PreviewState.Starting
      preview.micState() shouldBe PreviewState.Starting
      media.feed.level(0.5)
      preview.level() shouldBe 0.0
      media.answer(DeviceKind.Camera)
      media.answer(DeviceKind.Microphone)
      preview.cameraState() shouldBe PreviewState.Live
      preview.micState() shouldBe PreviewState.Live
      MediaDevices.cameraAccess() shouldBe MediaPermission.Granted
      media.feed.level(0.4)
      preview.level() shouldBe 0.4
      media.feed.level(3.0)
      preview.level() shouldBe 1.0
      preview.dispose()
    }

    "tell a refused, a missing, a lost and an unavailable device apart" in fx {
      val (preview, media, _) = previewing()
      media.answer(DeviceKind.Camera, Some(MediaRefusal.ByPerson))
      media.answer(DeviceKind.Microphone, Some(MediaRefusal.NoDevice))
      preview.cameraState() shouldBe PreviewState.Denied(MediaRefusal.ByPerson)
      preview.cameraState().refused shouldBe true
      preview.micState() shouldBe PreviewState.NoDevice
      MediaDevices.cameraAccess() shouldBe MediaPermission.refused(MediaRefusal.ByPerson)
      preview.dispose()

      val (policed, held, _) = previewing()
      held.answer(DeviceKind.Camera, Some(MediaRefusal.ByPolicy))
      held.answer(DeviceKind.Microphone, Some(MediaRefusal.InUse))
      policed.cameraState() shouldBe PreviewState.Denied(MediaRefusal.ByPolicy)
      policed.micState() shouldBe PreviewState.Failed(MediaRefusal.InUse)
      policed.dispose()

      val (unplugged, media2, _) = live()
      media2.feed.level(0.3)
      media2.feed.lost(DeviceKind.Microphone, MediaRefusal.NoDevice)
      unplugged.micState() shouldBe PreviewState.NoDevice
      unplugged.level() shouldBe 0.0
      media2.closed shouldBe Vector(DeviceKind.Microphone)
      unplugged.dispose()

      // the desktop's own engine has none
      val none = new CallPreview
      none.cameraState() shouldBe PreviewState.Unavailable
      none.micState() shouldBe PreviewState.Unavailable
      none.dispose()
    }

    "close a device turned off, open it again, and open another chosen in its place" in fx {
      val (preview, media, _) = live()
      val first = media.tracks(DeviceKind.Camera)
      preview.cameraOn @= false
      preview.cameraState() shouldBe PreviewState.Off
      media.closed shouldBe Vector(DeviceKind.Camera)
      first.stopped shouldBe true
      preview.micOn @= false
      preview.micState() shouldBe PreviewState.Off
      preview.level() shouldBe 0.0
      preview.cameraOn @= true
      media.opens.last shouldBe Map(DeviceKind.Camera -> MediaDevices.camera())
      preview.cameraState() shouldBe PreviewState.Starting
      media.answer(DeviceKind.Camera)
      preview.cameraState() shouldBe PreviewState.Live

      val was = MediaDevices.camera()
      try {
        MediaDevices.camera @= Some("cam-2")
        media.opens.last shouldBe Map(DeviceKind.Camera -> Some("cam-2"))
        val older = media.answers.size - 1
        MediaDevices.camera @= Some("cam-3")
        preview.cameraState() shouldBe PreviewState.Starting
        // the answer to the opening of cam-2 comes after cam-3 was asked for: not heard
        media.answer(DeviceKind.Camera, Some(MediaRefusal.NoDevice), older)
        preview.cameraState() shouldBe PreviewState.Starting
        media.answer(DeviceKind.Camera)
        preview.cameraState() shouldBe PreviewState.Live
        media.tracks(DeviceKind.Camera).device shouldBe Some("cam-3")
        // the microphone, turned off, is not opened by another choice of camera
        media.opens.exists(o => o.contains(DeviceKind.Microphone) && o.size == 1) shouldBe false
      }
      finally MediaDevices.camera @= was
      preview.dispose()
    }

    "close both, their lights off, when let go without joining" in fx {
      val (preview, media, _) = live()
      val tracks = media.tracks.values.toList
      preview.dispose()
      media.stopped shouldBe true
      tracks.foreach(_.stopped shouldBe true)
      preview.cameraState() shouldBe PreviewState.Off
      preview.micState() shouldBe PreviewState.Off
      preview.handed shouldBe false
      // nothing more is opened
      val asks = media.opens.size
      preview.cameraOn @= false
      preview.cameraOn @= true
      media.opens.size shouldBe asks
    }

    "hand its very tracks to the call it joins, with the microphone's mute carried in" in fx {
      val (preview, media, engine) = live()
      val camera = media.tracks(DeviceKind.Camera)
      preview.micOn @= false
      val call = new Call(engine)
      call.join("wss://calls.example.test", "token-1", preview)
      preview.handed shouldBe true
      val room = engine.rooms.head
      room.taken.exists(_ eq media) shouldBe true
      call.state() shouldBe CallState.Connecting
      room.feed.state(CallState.Connected)
      room.published.size shouldBe 1
      (room.published.head eq camera) shouldBe true
      room.opened shouldBe empty
      call.camera() shouldBe true
      call.microphone() shouldBe false
      // the camera was opened once, and nothing was opened again for the call
      media.made.count(_.kind == DeviceKind.Camera) shouldBe 1
      media.opens.size shouldBe 1
      // the preview, handed over, neither closes nor opens anything, and letting it go stops nothing
      preview.cameraOn @= false
      media.closed shouldBe Vector(DeviceKind.Microphone)
      preview.dispose()
      camera.stopped shouldBe false
      media.stopped shouldBe false
      call.leave()
      camera.stopped shouldBe true
    }

    "start a call with a refused device off" in fx {
      val (preview, media, engine) = previewing()
      media.answer(DeviceKind.Camera, Some(MediaRefusal.ByPerson))
      media.answer(DeviceKind.Microphone)
      val call = new Call(engine)
      call.join("wss://calls.example.test", "token-1", preview)
      val room = engine.rooms.head
      room.feed.state(CallState.Connected)
      call.camera() shouldBe false
      call.microphone() shouldBe true
      (room.published.map(_.kind)) shouldBe Vector(DeviceKind.Microphone)
      call.leave()
    }

    "draw the camera, mirrored and filling, or the placeholder and its words" in fx {
      val (preview, media, _) = previewing()
      val view = new CameraPreview(preview)
      view.size.width @= 480.0
      view.size.height @= 270.0
      media.shown shouldBe Vector(view)
      view.mirror() shouldBe true
      view.cover() shouldBe true
      view.radius() shouldBe 12.0
      view.picture.visible() shouldBe false
      view.message.visible() shouldBe false
      view.semantics().label shouldBe CallWords.preview

      media.answer(DeviceKind.Camera)
      view.picture.visible() shouldBe true
      view.icon.visible() shouldBe false
      view.message.visible() shouldBe false
      view.cover @= false
      media.shown shouldBe Vector(view)

      preview.cameraOn @= false
      view.picture.visible() shouldBe false
      view.icon.visible() shouldBe true
      view.message.content() shouldBe CallWords.cameraOff
      view.semantics().label shouldBe CallWords.preview + ", " + CallWords.cameraOff
      view.face @= Some(Participant("guest", "Ada"))
      view.avatar.visible() shouldBe true
      view.icon.visible() shouldBe false

      preview.cameraOn @= true
      media.answer(DeviceKind.Camera, Some(MediaRefusal.ByPerson))
      view.avatar.visible() shouldBe false
      view.icon.visible() shouldBe true
      view.message.content() shouldBe CallWords.cameraDenied
      CameraPreview.words(PreviewState.NoDevice) shouldBe CallWords.noCamera
      CameraPreview.words(PreviewState.Failed(MediaRefusal.InUse)) shouldBe CallWords.cameraFailed

      view.dispose()
      media.shown shouldBe empty
      preview.dispose()
    }

    "show the level as a meter, and its toggles as switches" in fx {
      val (preview, media, _) = live()
      val meter = MicLevel(preview)
      media.feed.level(0.25)
      meter.fraction() shouldBe 0.25
      meter.semantics().role shouldBe "meter"
      meter.semantics().label shouldBe CallWords.micLevel
      meter.semantics().range.map(_.text) shouldBe Some("25%")

      val mic = new MediaToggle(preview.micOn, DeviceKind.Microphone)
      mic.semantics().role shouldBe "switch"
      mic.semantics().label shouldBe CallWords.microphoneSwitch
      mic.semantics().checked shouldBe Some(true)
      mic.event.click @= PointerEvent(PointerKind.Click, 5.0, 5.0)
      preview.micOn() shouldBe false
      preview.micState() shouldBe PreviewState.Off
      mic.semantics().checked shouldBe Some(false)
      meter.fraction() shouldBe 0.0
      val cam = new MediaToggle(preview.cameraOn, DeviceKind.Camera)
      cam.semantics().label shouldBe CallWords.cameraSwitch
      List(meter, mic, cam).foreach(_.dispose())
      preview.dispose()
    }
  }
}
