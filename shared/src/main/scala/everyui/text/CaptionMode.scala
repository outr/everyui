package everyui.text

/** When the device's own caption setting says captions are shown ([[Captions.device]]). Its cases' names are their
  * own, since a Roku channel's type names are global. */
sealed trait CaptionMode

object CaptionMode {
  /** Always. */
  case object CaptionsOn extends CaptionMode
  /** Never. */
  case object CaptionsOff extends CaptionMode
  /** Only while a moment is played again (a Roku's "On replay", its instant replay). */
  case object CaptionsOnReplay extends CaptionMode
  /** Only while the sound is muted (a Roku TV's "When mute"). */
  case object CaptionsWhenMuted extends CaptionMode
  /** Where the sound is not in the viewer's language, or cannot be heard (an Apple TV's "Automatic"). */
  case object CaptionsAutomatic extends CaptionMode

  /** The mode a Roku names ("On", "Off", "Instant replay", "When mute"), or None for a name it does not know. */
  def named(name: String): Option[CaptionMode] = name.trim.toLowerCase match {
    case "on" => Some(CaptionsOn)
    case "off" => Some(CaptionsOff)
    case "instant replay" => Some(CaptionsOnReplay)
    case "when mute" => Some(CaptionsWhenMuted)
    case "automatic" => Some(CaptionsAutomatic)
    case _ => None
  }
}
