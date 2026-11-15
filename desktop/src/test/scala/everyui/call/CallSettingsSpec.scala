package everyui.call

import everyui.{FxSpec, ui}
import everyui.component.{Choice, Component, Modal}
import everyui.event.{PointerEvent, PointerKind}
import everyui.focus.Focus
import everyui.render.Platform

/** A call's settings (`CallSettings`): each kept on the device and read back, each quality mapped to what is captured
  * and published, a call joined with them and following a change, a preview opening with them and carrying them into
  * the call, Audio only keeping the camera off until it is turned on again, and the dialog's view, which opens a
  * preview of its own only where it has neither a call nor a preview, follows and sets the settings, and lets go of
  * everything as it is taken apart. Over an engine standing in for LiveKit's, which notes what it is told. */
class CallSettingsSpec extends FxSpec {
  private class FakeMedia(val feed: PreviewFeed) extends PreviewMedia {
    var told: Vector[String] = Vector.empty
    var chosen: Vector[CallChoices] = Vector.empty
    var answers: Vector[(DeviceKind, Option[MediaRefusal]) => Unit] = Vector.empty
    var stopped = false

    def open(wanted: Map[DeviceKind, Option[String]], done: (DeviceKind, Option[MediaRefusal]) => Unit): Unit = {
      told :+= "open " + wanted.keys.toList.map(_.toString).sorted.mkString(",")
      answers :+= done
    }
    def close(kind: DeviceKind): Unit = told :+= "close " + kind
    def show(into: CameraPreview): Unit = ()
    def hide(into: CameraPreview): Unit = ()
    def stop(): Unit = stopped = true
    override def choose(choices: CallChoices): Unit = {
      told :+= "choose"
      chosen :+= choices
    }
  }

  private class FakeRoom(val feed: CallFeed) extends CallRoom {
    var told: Vector[String] = Vector.empty
    var chosen: Vector[CallChoices] = Vector.empty
    var hearing: Option[Double => Unit] = None
    var heard = false

    def connect(url: String, token: String, devices: Map[DeviceKind, String]): Unit = told :+= "connect"
    def leave(): Unit = ()
    def send(source: TrackSource, on: Boolean, device: Option[String], done: Option[MediaRefusal] => Unit): Unit = {
      told :+= "send " + source + " " + on
      feed.sending(source, on)
      done(None)
    }
    def device(kind: DeviceKind, id: Option[String]): Unit = ()
    def audible(on: Boolean): Unit = ()
    def startAudio(): Unit = ()
    def show(track: CallTrack, into: CallVideo): Unit = ()
    def hide(track: CallTrack, into: CallVideo): Unit = ()
    override def choose(choices: CallChoices): Unit = {
      told :+= "choose"
      chosen :+= choices
    }
    override def meter(level: Double => Unit): () => Unit = {
      hearing = Some(level)
      heard = true
      () => hearing = None
    }
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

  private val keys = List("noiseSuppression", "echoCancellation", "autoGain", "quality").map("everyui.call." + _)

  // every setting as it starts, and none left on the device
  private def reset(): Unit = {
    CallSettings.noiseSuppression @= true
    CallSettings.echoCancellation @= true
    CallSettings.autoGain @= true
    CallSettings.quality @= VideoQuality.Auto
    keys.foreach(k => Platform.saveSetting(k, ""))
  }

  private def click(c: Component): Unit = c.event.click @= PointerEvent(PointerKind.Click, 5.0, 5.0)

  // `c` and everything in it
  private def within(c: Component): Vector[Component] = c +: c.childComponents.flatMap(within)

  "Call settings" should {
    "keep each choice on the device, and read it back" in fx {
      reset()
      CallSettings.noiseSuppression @= false
      CallSettings.quality @= VideoQuality.Standard
      Platform.setting("everyui.call.noiseSuppression") shouldBe "false"
      Platform.setting("everyui.call.quality") shouldBe "360p"
      CallSettings.flag("noiseSuppression")() shouldBe false
      CallSettings.flag("autoGain")() shouldBe true
      VideoQuality.of(Platform.setting("everyui.call.quality")) shouldBe VideoQuality.Standard
      VideoQuality.of("") shouldBe VideoQuality.Auto
      VideoQuality.all.foreach(q => VideoQuality.of(q.name) shouldBe q)
      CallSettings.current() shouldBe CallChoices(MediaDevices.microphone(), MediaDevices.camera(),
        MediaDevices.speaker(), noiseSuppression = false, quality = VideoQuality.Standard)
      CallSettings.current().sameProcessing(CallChoices()) shouldBe false
      reset()
      CallSettings.current().sameProcessing(CallChoices()) shouldBe true
    }

    "map each quality to what is captured and published" in {
      val h180 = VideoLayer(320, 180, 160000, 20)
      val h360 = VideoLayer(640, 360, 450000, 20)
      val h720 = VideoLayer(1280, 720, 1700000, 30)
      VideoQuality.Auto.send shouldBe Some(VideoSend(h720, List(h180, h360), keepResolution = false))
      VideoQuality.High.send shouldBe Some(VideoSend(h720, List(h360), keepResolution = true))
      VideoQuality.Standard.send shouldBe Some(VideoSend(VideoLayer(640, 360, 500000, 30), List(h180),
        keepResolution = false))
      VideoQuality.AudioOnly.send shouldBe None
      VideoQuality.all.flatMap(_.send).foreach { v =>
        v.layers.map(_.height) shouldBe v.layers.map(_.height).sorted
        v.layers.foreach(l => l.height should be < v.top.height)
      }
      CallChoices(quality = VideoQuality.High).video shouldBe VideoQuality.High.send
    }

    "join with them, and follow a change made during the call" in fx {
      reset()
      CallSettings.noiseSuppression @= false
      CallSettings.quality @= VideoQuality.High
      val engine = new FakeEngine
      val call = new Call(engine)
      call.connect("wss://calls", "token", microphone = true, camera = true)
      val room = engine.rooms.head
      room.told shouldBe Vector("choose", "connect")
      room.chosen.last shouldBe CallSettings.current()
      room.feed.state(CallState.Connected)
      room.told.drop(2) shouldBe Vector("send Microphone true", "send Camera true")

      CallSettings.echoCancellation @= false
      room.chosen.last.echoCancellation shouldBe false
      CallSettings.quality @= VideoQuality.AudioOnly
      room.chosen.last.quality shouldBe VideoQuality.AudioOnly
      room.told should contain ("send Camera false")
      call.camera() shouldBe false
      // the camera turned on again leaves Audio only
      call.setCamera(true)
      CallSettings.quality() shouldBe VideoQuality.Auto
      room.chosen.last.quality shouldBe VideoQuality.Auto
      call.camera() shouldBe true

      call.leave()
      val told = room.chosen.size
      CallSettings.autoGain @= false
      room.chosen.size shouldBe told
      reset()
    }

    "join without the camera while Audio only is chosen" in fx {
      reset()
      CallSettings.quality @= VideoQuality.AudioOnly
      val engine = new FakeEngine
      val call = new Call(engine)
      call.connect("wss://calls", "token", microphone = true, camera = true)
      val room = engine.rooms.head
      room.feed.state(CallState.Connected)
      room.told.filter(_.startsWith("send")) shouldBe Vector("send Microphone true")
      call.camera() shouldBe false
      call.leave()
      reset()
    }

    "open a preview with them, and carry them into the call" in fx {
      reset()
      CallSettings.quality @= VideoQuality.Standard
      val engine = new FakeEngine
      val preview = new CallPreview(engine)
      val media = engine.media.head
      media.told shouldBe Vector("choose", "open Camera,Microphone")
      media.chosen.last.quality shouldBe VideoQuality.Standard
      media.answers.last(DeviceKind.Camera, None)
      media.answers.last(DeviceKind.Microphone, None)

      CallSettings.autoGain @= false
      media.chosen.last.autoGain shouldBe false
      CallSettings.quality @= VideoQuality.AudioOnly
      preview.cameraOn() shouldBe false
      preview.cameraState() shouldBe PreviewState.Off
      media.told should contain ("close Camera")
      // the camera turned on again leaves Audio only, and opens with Auto
      preview.cameraOn @= true
      CallSettings.quality() shouldBe VideoQuality.Auto
      media.chosen.last.quality shouldBe VideoQuality.Auto
      media.told.takeRight(2) shouldBe Vector("choose", "open Camera")
      media.answers.last(DeviceKind.Camera, None)

      CallSettings.quality @= VideoQuality.AudioOnly
      val call = new Call(engine)
      call.join("wss://calls", "token", preview)
      val room = engine.rooms.head
      room.chosen.last shouldBe CallSettings.current()
      room.feed.state(CallState.Connected)
      room.told.filter(_.startsWith("send")) shouldBe Vector("send Microphone true")
      // handed over, the preview follows nothing more
      val told = media.chosen.size
      CallSettings.noiseSuppression @= false
      media.chosen.size shouldBe told
      call.leave()
      preview.dispose()
      reset()
    }

    "start a preview's camera off while Audio only is chosen" in fx {
      reset()
      CallSettings.quality @= VideoQuality.AudioOnly
      val engine = new FakeEngine
      val preview = new CallPreview(engine)
      preview.cameraOn() shouldBe false
      engine.media.head.told shouldBe Vector("choose", "open Microphone")
      preview.dispose()
      reset()
    }

    "show them in a view that opens its own preview, follows and sets them, and lets go as it is taken apart" in fx {
      reset()
      val engine = new FakeEngine
      val view = new CallSettingsView(engine = engine)
      engine.media.size shouldBe 1
      val media = engine.media.head
      view.picture shouldBe a[CameraPreview]
      media.answers.last(DeviceKind.Camera, None)
      media.answers.last(DeviceKind.Microphone, None)
      media.feed.level(0.42)
      view.level() shouldBe 0.42
      view.meter.semantics().range.map(_.text) shouldBe Some("40%")

      click(view.noiseSuppression)
      CallSettings.noiseSuppression() shouldBe false
      media.chosen.last.noiseSuppression shouldBe false
      CallSettings.noiseSuppression @= true
      view.noiseSuppression.on() shouldBe true
      view.echoCancellation.semantics().label shouldBe CallWords.echoCancellation

      val chips = view.qualities.children().collect { case c: Choice => c }
      chips.map(_.semantics().label) shouldBe Vector(CallWords.qualityAuto, "720p", "360p", CallWords.audioOnly)
      chips.map(_.semantics().checked) shouldBe Vector(Some(true), Some(false), Some(false), Some(false))
      click(chips(2))
      CallSettings.quality() shouldBe VideoQuality.Standard
      chips.map(_.semantics().checked) shouldBe Vector(Some(false), Some(false), Some(true), Some(false))
      view.microphones.label() shouldBe CallWords.microphoneSwitch
      view.microphones.selected() shouldBe 0

      view.dispose()
      media.stopped shouldBe true
      val told = media.chosen.size
      CallSettings.quality @= VideoQuality.High
      media.chosen.size shouldBe told
      reset()
    }

    "show a preview's own devices, or a call's, opening none of its own" in fx {
      reset()
      val engine = new FakeEngine
      val preview = new CallPreview(engine)
      val fromPreview = new CallSettingsView(preview = Some(preview), engine = engine)
      engine.media.size shouldBe 1
      fromPreview.dispose()
      engine.media.head.stopped shouldBe false

      val call = new Call(engine)
      call.join("wss://calls", "token", preview)
      engine.rooms.head.feed.state(CallState.Connected)
      val inCall = new CallSettingsView(call = Some(call), engine = engine)
      engine.media.size shouldBe 1
      inCall.picture shouldBe a[VideoTile]
      val room = engine.rooms.head
      room.heard shouldBe true
      room.hearing.foreach(_(0.3))
      inCall.level() shouldBe 0.3
      inCall.dispose()
      room.hearing shouldBe None
      call.leave()
      preview.dispose()
      reset()
    }

    "put its dialog up as wide as a phone has room for, each part inside it, and take it down" in {
      fx(reset())
      val was = fx(ui.size.width())
      fx(ui.size.width @= 390.0)
      try {
        val panel = fx(CallSettings.open())
        (1 to 6).foreach(_ => fx(()))
        fx {
          Modal.showing shouldBe true
          val view = within(panel).collectFirst { case v: CallSettingsView => v }.get
          panel.size.width() should be <= 390.0 - 32.0 + 0.5
          view.size.width() should be < CallSettingsView.width
          view.picture.size.width() shouldBe view.size.width()
          view.picture.size.height() shouldBe math.min(view.size.width() * 9.0 / 16.0, CallSettingsView.tallest) +- 0.01
          List(view.picture, view.microphones, view.meter, view.cameras,
            view.qualities).foreach { c =>
            withClue(c.kind + ": ") {
              c.absoluteLeft should be >= panel.absoluteLeft
              c.absoluteLeft + c.size.width() should be <= panel.absoluteLeft + panel.size.width() - 8.0
            }
          }
        }
        // closed, it is taken apart a turn later, its own preview with it
        val view = fx(within(panel).collectFirst { case v: CallSettingsView => v }.get)
        fx(panel.close())
        (1 to 6).foreach(_ => fx(()))
        fx(view.takenApart) shouldBe true
      } finally fx {
        while (Modal.showing) Modal.close()
        ui.size.width @= was
        Focus.focused @= None
      }
    }
  }
}
