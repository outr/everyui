package everyui.file

/** How a save (`Files.save`, `Files.saveUrl`) ended: saved, cancelled by the person, or failed. Its cases' names are
  * their own (not "Failed"), since a Roku channel's type names are global.
  *
  * {{{
  *   Files.save(csv.getBytes("UTF-8"), "report.csv").map {
  *     case SaveResult.Saved(name) => Toast.show("Saved " + name.getOrElse("report.csv"))
  *     case SaveResult.Cancelled => ()
  *     case SaveResult.SaveFailed(why) => Toast.show("Not saved: " + why)
  *   }.start()
  * }}} */
sealed trait SaveResult

object SaveResult {
  /** Saved, as `name` where the platform says what it became: the name the person gave it, the desktop's whole path. A
    * browser without a save picker takes the file into its downloads and says no more, so there it is None. */
  case class Saved(name: Option[String]) extends SaveResult

  /** The person closed the picker without choosing where. */
  case object Cancelled extends SaveResult

  /** Not saved: nowhere to save on this platform (a Roku, an Apple TV, an Android TV), or the file could not be read
    * or written, as the platform put it. */
  case class SaveFailed(message: String) extends SaveResult
}
