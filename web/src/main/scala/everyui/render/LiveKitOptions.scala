package everyui.render

import everyui.call.CallChoices

import scala.scalajs.js

/** A call's settings (`everyui.call.CallChoices`) as LiveKit's client takes them: the microphone's capture options
  * (its device, and the browser's `noiseSuppression`, `echoCancellation` and `autoGainControl`), the camera's (its
  * device and `resolution`), and the camera's publish options (its top layer's `videoEncoding`, the `simulcast` layers
  * beside it as LiveKit's `VideoPreset`s, and `degradationPreference` "maintain-resolution" where the size is kept).
  * The processing of a microphone already open is changed in place where the browser lets it (`process`). */
object LiveKitOptions {
  /** The microphone's capture options, on `device` where one is given. */
  def audio(choices: CallChoices, device: Option[String]): js.Dynamic = {
    val o = js.Dynamic.literal(noiseSuppression = choices.noiseSuppression,
      echoCancellation = choices.echoCancellation, autoGainControl = choices.autoGain)
    device.foreach(id => o.deviceId = id)
    o
  }

  /** The camera's capture options, on `device` where one is given, at the size its quality is captured at. */
  def video(choices: CallChoices, device: Option[String]): js.Dynamic = {
    val o = js.Dynamic.literal()
    device.foreach(id => o.deviceId = id)
    choices.video.foreach { v =>
      o.resolution = js.Dynamic.literal(width = v.top.width, height = v.top.height, frameRate = v.top.frameRate)
    }
    o
  }

  /** How the camera is published: at its quality's top layer, with its lower layers beside it. */
  def publish(choices: CallChoices): js.Dynamic = {
    val o = js.Dynamic.literal()
    choices.video.foreach { v =>
      o.videoEncoding = js.Dynamic.literal(maxBitrate = v.top.maxBitrate, maxFramerate = v.top.frameRate)
      o.simulcast = v.layers.nonEmpty
      o.videoSimulcastLayers = js.Array(v.layers.map(l => js.Dynamic.newInstance(LiveKitScript.client.VideoPreset)(
        l.width, l.height, l.maxBitrate, l.frameRate))*)
      if (v.keepResolution) o.degradationPreference = "maintain-resolution"
    }
    o
  }

  /** The device `track` (a LiveKit LocalTrack) records from or films with now, where the browser says. */
  def device(track: js.Dynamic): Option[String] = {
    val id = track.mediaStreamTrack.getSettings().deviceId
    if (LiveKitRoom.defined(id) && String.valueOf(id).nonEmpty) Some(String.valueOf(id)) else None
  }

  /** The microphone's processing as `choices` say, on `track` (LiveKit's LocalAudioTrack, open): changed in place
    * (`applyConstraints`) where the browser does so, which its `getSettings` then says, else by opening the
    * microphone again with it (`restartTrack`, which the call's sender takes in place of the old track, nothing
    * published again). `done` hears which: "live" or "restarted" (or "failed"). */
  def process(track: js.Dynamic, choices: CallChoices, chosen: Option[String], done: String => Unit): Unit = {
    val wanted = js.Dynamic.literal(noiseSuppression = choices.noiseSuppression,
      echoCancellation = choices.echoCancellation, autoGainControl = choices.autoGain)
    val restart = () => LiveKitRoom.after(track.restartTrack(audio(choices, chosen.orElse(device(track)))))(
      _ => done("restarted"), e => {
        Platform.log("[call] could not record with the new processing: " + WebErrors.message(e))
        done("failed")
      })
    LiveKitRoom.after(track.applyConstraints(wanted))(_ => if (honoured(track, choices)) done("live") else restart(),
      _ => restart())
  }

  // whether the open track records as `choices` say, so far as the browser tells (one it does not tell of is taken
  // as done)
  private def honoured(track: js.Dynamic, choices: CallChoices): Boolean = {
    val now = track.mediaStreamTrack.getSettings()
    def is(name: String, on: Boolean): Boolean = {
      val v = now.selectDynamic(name)
      !LiveKitRoom.defined(v) || js.typeOf(v) != "boolean" || v.asInstanceOf[Boolean] == on
    }
    is("noiseSuppression", choices.noiseSuppression) && is("echoCancellation", choices.echoCancellation) &&
      is("autoGainControl", choices.autoGain)
  }
}
