package everyui.render

import everyui.component.MediaTrack
import everyui.roku.{uiCaptionsLanguage, uiCaptionsListen, uiCaptionsMode, uiCaptionsSetMode, uiCaptionsUnlisten}
import everyui.text.{CaptionMode, CaptionPreference}

/** The device's caption setting (`everyui.text.Captions`) from roDeviceInfo: its mode (GetCaptionsMode: On, Off,
  * Instant replay, When mute) and its preferred caption language (GetPreferredCaptionLanguage, where the box has it).
  * A change is the roDeviceInfoEvent the channel's Main hears (isCaptionModeChanged), handed to the scene; one the
  * channel made itself for a video's captions is not passed on, one made through `setDevice` is. */
object CaptionAccess {
  def supported(): Boolean = true

  def writable(): Boolean = true

  /** The mode written as its own options menu writes it (SetCaptionsMode), and heard as the viewer's change. A Roku has
    * no Automatic. */
  def setDevice(mode: CaptionMode): Boolean = mode match {
    case CaptionMode.CaptionsOn => uiCaptionsSetMode("On")
    case CaptionMode.CaptionsOff => uiCaptionsSetMode("Off")
    case CaptionMode.CaptionsOnReplay => uiCaptionsSetMode("Instant replay")
    case CaptionMode.CaptionsWhenMuted => uiCaptionsSetMode("When mute")
    case CaptionMode.CaptionsAutomatic => false
  }

  def device(): Option[CaptionPreference] =
    CaptionMode.named(uiCaptionsMode()).map(mode => CaptionPreference(mode, language(uiCaptionsLanguage())))

  /** Roku's own codes, as its documentation lists them: two letters, but "sp" for Spanish and "rm" for Romanian, in
    * either case. */
  private def language(code: String): Option[String] = {
    val lower = code.trim.toLowerCase
    val tag = if (lower == "sp") "es" else if (lower == "rm") "ro" else MediaTrack.languageTag(lower)
    if (tag.isEmpty) None else Some(tag)
  }

  def listen(changed: () => Unit): () => Unit = {
    val id = uiCaptionsListen(changed)
    () => uiCaptionsUnlisten(id)
  }
}
