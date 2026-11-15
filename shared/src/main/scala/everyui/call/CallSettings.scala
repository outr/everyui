package everyui.call

import everyui.dialog.{DialogAction, DialogPanel}
import everyui.i18n.Words
import everyui.render.Platform
import everyui.ui
import reactify.*

/** How this person's calls sound and look on this device, kept on it as the devices chosen are (`MediaDevices`): the
  * microphone's processing (`noiseSuppression`, `echoCancellation`, `autoGain`, all on to begin with) and the camera's
  * `quality`. `current` is all of it with the devices, as a call and a preview send with it: a `Call` joins with it
  * and follows a change at once, as a [[CallPreview]] does, so what is set before joining is what the call sends.
  *
  * `open` puts up the dialog that sets it ([[CallSettingsView]]): the devices, the microphone's level, a test sound,
  * the processing switches, the quality, and the camera's picture. During a call it shows the call's own camera and
  * microphone, from a preview the preview's, and otherwise opens both itself until it closes.
  *
  * Audio only sends no camera: chosen, it turns a preview's and a call's camera off, and a call joined starts without
  * one; the camera turned on again (a preview's `cameraOn`, `Call.setCamera(true)`) goes back to Auto.
  *
  * {{{
  *   settings.event.click.attach(_ => CallSettings.open(Some(call)))   // the call bar's
  *   new CallSettingsButton(() => CallSettings.open(preview))         // a "ready to join?" page's
  *   CallSettings.current.attach(c => log("quality " + c.quality.name))
  * }}} */
object CallSettings {
  /** Whether the microphone's background noise is taken out as it records. */
  val noiseSuppression: Var[Boolean] = flag("noiseSuppression")
  /** Whether the others' sound coming back into the microphone from the speaker is taken out. */
  val echoCancellation: Var[Boolean] = flag("echoCancellation")
  /** Whether the microphone's level is evened out as it records (automatic gain control). */
  val autoGain: Var[Boolean] = flag("autoGain")
  /** How the camera is sent: Auto, 720p, 360p, or not at all. */
  val quality: Var[VideoQuality] = {
    val key = "everyui.call.quality"
    val v: Var[VideoQuality] = Var(VideoQuality.of(Option(Platform.setting(key)).getOrElse("")))
    v.attach(q => Platform.saveSetting(key, q.name))
    v
  }

  /** Everything chosen, the devices with it. */
  val current: Val[CallChoices] = Val(CallChoices(MediaDevices.microphone(), MediaDevices.camera(),
    MediaDevices.speaker(), noiseSuppression(), echoCancellation(), autoGain(), quality()))

  /** The settings dialog, put up over whatever is up: during `call` where it is under way (connecting or connected),
    * with its camera and microphone; otherwise with a camera and microphone of its own, open until it closes. Done or
    * Back closes it; every change is kept as it is made. */
  def open(call: Option[Call] = None): DialogPanel = show(new CallSettingsView(call = call))

  /** The settings dialog over a "ready to join?" page, with `preview`'s own camera and microphone (none opened
    * again). */
  def open(preview: CallPreview): DialogPanel = show(new CallSettingsView(preview = Some(preview)))

  private def show(view: CallSettingsView): DialogPanel = {
    // as wide as the dialog has room for on a narrow screen (a phone), in either direction
    view.size.width := math.min(CallSettingsView.width,
      ui.size.width() - 32.0 - DialogPanel.textSize() * 2.9)
    var panel: DialogPanel = null
    panel = new DialogPanel(CallWords.settings, List(view), List(DialogAction(Words.done, () => panel.close(),
      default = true)))
    panel.open()
    panel
  }

  /** A switch kept on the device under `everyui.call.<name>`, on until it is turned off. */
  private[call] def flag(name: String): Var[Boolean] = {
    val key = "everyui.call." + name
    val v: Var[Boolean] = Var(Option(Platform.setting(key)).getOrElse("") != "false")
    v.attach(on => Platform.saveSetting(key, on.toString))
    v
  }
}
