package everyui.component

/** What a video's captions are to be ([[MediaSupport.captions]]): the device's own caption setting
  * ([[everyui.text.Captions]]), none, or one text track by its id. Its cases' names are their own, since a Roku
  * channel's type names are global. */
sealed trait TextChoice

object TextChoice {
  /** As the device's caption setting has them: on, the track in its caption language, else the first; off, none. */
  case object TextFromDevice extends TextChoice
  /** None, whatever the device says. */
  case object TextOff extends TextChoice
  /** This track ([[MediaTrack.id]]), whatever the device says. */
  case class TextTrack(id: String) extends TextChoice

  /** The choice `MediaSupport.textTrack` says, as it always has: None the device's, Some("") none, Some(id) that
    * track. */
  def fromTrack(track: Option[String]): TextChoice = track match {
    case None => TextFromDevice
    case Some(id) => if (id.isEmpty) TextOff else TextTrack(id)
  }

  /** `choice` as `MediaSupport.textTrack` says it. */
  def asTrack(choice: TextChoice): Option[String] = choice match {
    case TextFromDevice => None
    case TextOff => Some("")
    case TextTrack(id) => Some(id)
  }
}
