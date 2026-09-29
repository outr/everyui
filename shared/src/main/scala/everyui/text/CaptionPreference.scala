package everyui.text

/** What the device's own caption setting says ([[Captions.device]]): when captions are shown, and the language they
  * are wanted in, as a two-letter tag ("en"), where the device keeps one. */
case class CaptionPreference(mode: CaptionMode, language: Option[String] = None) {
  /** Whether captions are always shown. */
  def on: Boolean = mode == CaptionMode.CaptionsOn

  /** Whether they are never shown. */
  def off: Boolean = mode == CaptionMode.CaptionsOff
}
