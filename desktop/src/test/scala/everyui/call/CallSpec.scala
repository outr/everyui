package everyui.call

import everyui.FxSpec

/** A call (`Call`) over an engine standing in for LiveKit's: ended at once where there is no engine; joined, sent
  * what was asked for once in the room, and left; why a device could not be had, a new device followed while in the
  * room, the others' sound silenced and let play; a camera's tile drawing the picture or the face, the name, a muted
  * microphone and a ring while speaking; and a screen shared, whole, by whoever shares it. */
class CallSpec extends FxSpec {
  private class FakeRoom(val feed: CallFeed) extends CallRoom {
    var joined: Option[(String, String, Map[DeviceKind, String])] = None
    var sent: Vector[(TrackSource, Boolean, Option[String])] = Vector.empty
    // what turning a source on answers
    var answer: Option[MediaRefusal] = None
    var devices: Vector[(DeviceKind, Option[String])] = Vector.empty
    var hearing: Vector[Boolean] = Vector.empty
    var started = 0
    var left = false
    var shown: Vector[(String, CallVideo)] = Vector.empty
    var hidden: Vector[(String, CallVideo)] = Vector.empty

    def connect(url: String, token: String, devices: Map[DeviceKind, String]): Unit =
      joined = Some((url, token, devices))
    def leave(): Unit = left = true
    def send(source: TrackSource, on: Boolean, device: Option[String], done: Option[MediaRefusal] => Unit): Unit = {
      sent :+= ((source, on, device))
      val why = if (on) answer else None
      if (why.isEmpty) feed.sending(source, on)
      done(why)
    }
    def device(kind: DeviceKind, id: Option[String]): Unit = devices :+= ((kind, id))
    def audible(on: Boolean): Unit = hearing :+= on
    def startAudio(): Unit = {
      started += 1
      feed.audioBlocked(false)
    }
    def show(track: CallTrack, into: CallVideo): Unit = shown :+= ((track.id, into))
    def hide(track: CallTrack, into: CallVideo): Unit = hidden :+= ((track.id, into))
  }

  private class FakeEngine extends CallEngine {
    var rooms: Vector[FakeRoom] = Vector.empty
    def name: String = "fake"
    def supported: Boolean = true
    def room(feed: CallFeed): CallRoom = {
      val r = new FakeRoom(feed)
      rooms :+= r
      r
    }
  }

  private def joined(microphone: Boolean = true, camera: Boolean = false): (Call, FakeRoom) = {
    val engine = new FakeEngine
    val call = new Call(engine)
    call.connect("wss://calls.example.test", "token-1", microphone, camera)
    val room = engine.rooms.head
    room.feed.state(CallState.Connected)
    (call, room)
  }

  private val me = CallParticipant("u-me", "Me", local = true, microphone = true)
  private val ada = CallParticipant("u-ada", "Ada")

  "A call" should {
    "end as it connects where there is no engine" in fx {
      val call = new Call
      call.supported shouldBe false
      call.connect("wss://calls.example.test", "token-1")
      call.state() shouldBe CallState.Ended(CallEnd.Unavailable)
      var why: Option[MediaRefusal] = None
      call.setMicrophone(true, w => why = w)
      why shouldBe Some(MediaRefusal.Unavailable)
    }

    "join, send what was asked for once in the room, and leave" in fx {
      val engine = new FakeEngine
      val call = new Call(engine)
      call.connect("wss://calls.example.test", "token-1", microphone = true, camera = true)
      call.state() shouldBe CallState.Connecting
      val room = engine.rooms.head
      room.joined.map(j => (j._1, j._2)) shouldBe Some(("wss://calls.example.test", "token-1"))
      room.sent shouldBe empty
      room.feed.state(CallState.Connected)
      room.sent shouldBe Vector((TrackSource.Microphone, true, None), (TrackSource.Camera, true, None))
      call.microphone() shouldBe true
      call.camera() shouldBe true
      room.feed.people(Some(me), Vector(ada))
      call.everyone().map(_.identity) shouldBe Vector("u-me", "u-ada")
      call.participants().map(_.identity) shouldBe Vector("u-ada")
      call.setMicrophone(false)
      room.sent.last shouldBe ((TrackSource.Microphone, false, None))
      call.microphone() shouldBe false
      room.feed.state(CallState.Reconnecting)
      call.live shouldBe true
      room.feed.state(CallState.Connected)
      call.leave()
      room.left shouldBe true
      call.state() shouldBe CallState.Ended(CallEnd.Left)
      call.everyone() shouldBe empty
      call.camera() shouldBe false
      // what the room says after is not heard
      room.feed.state(CallState.Connected)
      room.feed.people(Some(me), Vector(ada))
      call.state() shouldBe CallState.Ended(CallEnd.Left)
      call.everyone() shouldBe empty
    }

    "say why a device could not be had, follow a new one, and silence the others and let them play" in fx {
      val (call, room) = joined(microphone = false)
      room.sent shouldBe empty
      room.answer = Some(MediaRefusal.ByPerson)
      var why: Option[MediaRefusal] = None
      call.setCamera(true, w => why = w)
      why shouldBe Some(MediaRefusal.ByPerson)
      call.camera() shouldBe false
      MediaDevices.cameraAccess() shouldBe MediaPermission.refused(MediaRefusal.ByPerson)
      MediaDevices.refused(DeviceKind.Camera, MediaRefusal.Unavailable)

      val was = MediaDevices.microphone()
      try {
        MediaDevices.microphone @= Some("mic-2")
        room.devices shouldBe Vector((DeviceKind.Microphone, Some("mic-2")))
        room.answer = None
        call.setMicrophone(true)
        room.sent.last shouldBe ((TrackSource.Microphone, true, Some("mic-2")))
      }
      finally MediaDevices.microphone @= was

      call.audible @= false
      room.hearing.last shouldBe false
      room.feed.audioBlocked(true)
      call.audioBlocked() shouldBe true
      call.startAudio()
      room.started shouldBe 1
      call.audioBlocked() shouldBe false

      // asked for while connecting, and the call ended before the room was reached
      val engine = new FakeEngine
      val early = new Call(engine)
      early.connect("wss://calls.example.test", "token-2", microphone = false)
      var screen: Option[MediaRefusal] = None
      early.setScreen(true, w => screen = w)
      engine.rooms.head.sent shouldBe empty
      early.leave()
      screen shouldBe Some(MediaRefusal.Unavailable)
    }

    "draw a camera's picture or the face, the name, a muted microphone, and a ring while speaking" in fx {
      val (call, room) = joined()
      val mine = CallTrack("c-me", TrackSource.Camera, "u-me")
      room.feed.people(Some(me.copy(camera = Some(mine))), Vector(ada))
      val myTile = new VideoTile(call, "u-me")
      val adaTile = new VideoTile(call, "u-ada")
      List(myTile, adaTile).foreach { t =>
        t.size.width @= 320.0
        t.size.height @= 180.0
      }
      room.shown shouldBe Vector(("c-me", myTile.video))
      myTile.video.mirror() shouldBe true
      myTile.avatar.visible() shouldBe false
      myTile.muted() shouldBe false
      adaTile.avatar.visible() shouldBe true
      adaTile.muted() shouldBe true
      adaTile.label.visible() shouldBe true
      adaTile.semantics().label shouldBe "Ada, " + CallWords.muted
      adaTile.borderWidth() shouldBe 0.0

      room.feed.people(Some(me.copy(camera = Some(mine))), Vector(ada.copy(speaking = true, microphone = true)))
      adaTile.borderWidth() shouldBe 3.0
      adaTile.semantics().label shouldBe "Ada, " + CallWords.speaking

      val hers = CallTrack("c-ada", TrackSource.Camera, "u-ada")
      room.feed.people(Some(me.copy(camera = Some(mine))), Vector(ada.copy(camera = Some(hers))))
      room.shown.last shouldBe (("c-ada", adaTile.video))
      adaTile.video.mirror() shouldBe false
      adaTile.avatar.visible() shouldBe false
      room.feed.people(Some(me.copy(camera = Some(mine))), Vector(ada))
      room.hidden.last shouldBe (("c-ada", adaTile.video))
      adaTile.avatar.visible() shouldBe true

      myTile.dispose()
      room.hidden.last shouldBe (("c-me", myTile.video))
      adaTile.dispose()
    }

    "show whoever shares a screen, whole, and nothing while nobody does" in fx {
      val (call, room) = joined()
      room.feed.people(Some(me), Vector(ada))
      val stage = new ScreenShareView(call)
      stage.sharing() shouldBe false
      stage.label.visible() shouldBe false
      stage.avatar.visible() shouldBe false
      room.shown shouldBe empty

      val screen = CallTrack("s-ada", TrackSource.Screen, "u-ada")
      room.feed.people(Some(me), Vector(ada.copy(screen = Some(screen))))
      stage.sharing() shouldBe true
      room.shown shouldBe Vector(("s-ada", stage.video))
      stage.video.cover() shouldBe false
      stage.label.visible() shouldBe true
      stage.semantics().label shouldBe CallWords.screenOf("Ada")
      stage.avatar.visible() shouldBe false
      stage.borderWidth() shouldBe 0.0

      room.feed.people(Some(me), Vector(ada))
      stage.sharing() shouldBe false
      room.hidden shouldBe Vector(("s-ada", stage.video))
      stage.dispose()
    }
  }
}
