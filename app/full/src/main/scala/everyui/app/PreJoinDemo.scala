package everyui.app

import everyui.call.*
import everyui.chat.Participant
import everyui.component.{Button, ButtonStyle, Column, Row, Select, TextView}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** A meeting's "ready to join?" page, then the call it joins: `demo=prejoin`. The camera's picture (a CameraPreview,
  * mirrored, rounded as the call's tiles), the microphone's level (MicLevel), the microphone and camera switches
  * (MediaToggle), the camera to use, and where each stands; Join joins `livekit=<wss url>` with `token=<jwt>` from the
  * preview, its tracks handed over and its mute carried in, and shows this person's own tile and what the call sends.
  * Each change is logged: `[ui] prejoin camera Live`, `prejoin microphone Off`, `prejoin call Connected camera=true
  * microphone=false`. Calls run in a browser only; elsewhere both say Unavailable. */
object PreJoinDemo {
  def run(): Unit = {
    ui.backgroundColor := Theme.background()
    val preview = new CallPreview
    val picture = new CameraPreview(preview)
    picture.size.width @= 480.0
    picture.size.height @= 270.0
    picture.face @= Some(Participant("guest", "Guest"))
    val meter = MicLevel(preview, 200.0, 8.0)
    val mic = new MediaToggle(preview.micOn, DeviceKind.Microphone)
    val cam = new MediaToggle(preview.cameraOn, DeviceKind.Camera)
    // the cameras by label (or "Camera 2" before the browser gives labels), the system's own first
    val cameras = new Select("Default camera" :: MediaDevices.cameras().toList.map(_.shown))
    cameras.size.width @= 260.0
    cameras.size.height @= 36.0
    cameras.selected.attach(i => MediaDevices.camera @= MediaDevices.cameras().lift(i - 1).map(_.id))
    val status = TextView.bound("Camera: " + preview.cameraState().toString + " · Microphone: " +
      preview.micState().toString, 13.0, Theme.muted())
    preview.cameraState.attach(s => Platform.log("prejoin camera " + s))
    preview.micState.attach(s => Platform.log("prejoin microphone " + s))

    val call = new Call
    val joining = new Button("Join", () => {
      call.join(Platform.launchArg("livekit"), Platform.launchArg("token"), preview)
      Platform.log("prejoin joined camera=" + preview.cameraOn() + " microphone=" + preview.micOn())
    }, ButtonStyle.Filled)
    val sent = TextView.bound("Call: " + call.state().toString + " · camera " + call.camera().toString +
      " · microphone " + call.microphone().toString, 13.0, Theme.muted())
    call.state.attach(s => Platform.log("prejoin call " + s + " camera=" + call.camera() + " microphone=" +
      call.microphone()))
    call.camera.attach(on => Platform.log("prejoin call camera=" + on))

    val page = Column(16.0, padding = 24.0)(
      TextView("Ready to join?", 22.0),
      picture,
      Row(12.0, middle = true)(mic, cam, meter),
      cameras,
      status,
      Row(12.0, middle = true)(joining, sent)
    )
    ui.children += page

    // in the call: this person's own tile, from the very track the preview opened
    call.state.attach {
      case CallState.Connected if page.children().size < 7 =>
        val tile = new VideoTile(call, call.local().map(_.identity).getOrElse(""))
        tile.size.width @= 240.0
        tile.size.height @= 135.0
        page.children += tile
      case _ =>
    }
  }
}
