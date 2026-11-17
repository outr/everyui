package everyui.push

/** How much a channel's notices interrupt: Min (folded away, no icon in the status bar), Low (shown, silent), Default
  * (with the device's sound), High (with the sound, and shown over what is on the screen), or Urgent (as High: Android
  * makes no channel above it, and before Android 8 the notice's priority is the highest). An ongoing notice is Low
  * unless the app says otherwise; a push's channel is Default unless it says otherwise ("channelImportance"). */
enum NoticeImportance {
  case Min, Low, Default, High, Urgent

  /** The word a push's data names it by: "min", "low", "default", "high" or "urgent". */
  def word: String = this match {
    case Min => "min"
    case Low => "low"
    case Default => "default"
    case High => "high"
    case Urgent => "urgent"
  }
}

object NoticeImportance {
  /** The importance `word` names (`NoticeImportance.word`, in any case), Default for any other word or none. */
  def named(word: String): NoticeImportance = word.trim.toLowerCase match {
    case "min" => Min
    case "low" => Low
    case "high" => High
    case "urgent" => Urgent
    case _ => Default
  }
}
