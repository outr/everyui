package everyui.component

/** A subtitle file that goes with what is playing but is not inside it — downloaded, made from the sound, or re-timed
  * — for the player to offer beside the tracks the file carries: where it is, what a person is shown, and the
  * language it is in. */
case class MediaSidecar(url: String, label: String = "", language: String = "")
