package everyui.render

import everyui.component.MediaTrack
import everyui.text.{CaptionMode, CaptionPreference}
import everyui.tvos.{uiCaptionsLanguage, uiCaptionsListen, uiCaptionsMode, uiCaptionsUnlisten}

/** The device's caption setting (`everyui.text.Captions`) from MediaAccessibility (Settings, Accessibility, Subtitles
  * and Captioning): Closed Captions and SDH on (CaptionsOn), Automatic, or off, and the first language the viewer
  * prefers them in. A change is its settings-changed notification. */
object CaptionAccess {
  def supported(): Boolean = true

  def device(): Option[CaptionPreference] = CaptionMode.named(uiCaptionsMode()).map { mode =>
    val tag = MediaTrack.languageTag(uiCaptionsLanguage())
    CaptionPreference(mode, if (tag.isEmpty) None else Some(tag))
  }

  def listen(changed: () => Unit): () => Unit = {
    val id = uiCaptionsListen(changed)
    () => uiCaptionsUnlisten(id)
  }
}
