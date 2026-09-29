package everyui.render

import everyui.text.CaptionPreference

/** The device's caption setting (`everyui.text.Captions`). JavaFX tells an app nothing of the system's, so there is
  * none, and no change of it is ever heard. */
object CaptionAccess {
  def supported(): Boolean = false

  def device(): Option[CaptionPreference] = None

  def listen(changed: () => Unit): () => Unit = () => ()
}
