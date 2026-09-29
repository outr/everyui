package everyui.text

import everyui.component.MediaTrack
import everyui.render.CaptionAccess

/** The device's own caption setting: whether the viewer wants captions, and in what language. A Roku says it through
  * roDeviceInfo (its mode: On, Off, On replay, When mute, and its preferred caption language), Android through its
  * CaptioningManager (on or off, and its locale), an Apple TV through MediaAccessibility (Always On, Automatic or off,
  * and its preferred languages). A browser and the desktop tell an app nothing of the kind (`supported` false, `device`
  * None).
  *
  * A [[everyui.component.VideoView]] follows it by itself: until the app chooses a text track (`textTrack`, or a text
  * language through `languages`), what it shows is the device's choice, as [[choose]] decides it.
  *
  * {{{
  *   Captions.device match {
  *     case Some(p) if p.on => Platform.log("captions wanted, in " + p.language.getOrElse("any language"))
  *     case _ => ()
  *   }
  *   val stop = Captions.listen(p => menu.refresh())
  * }}} */
object Captions {
  /** Whether this platform says what the viewer's caption setting is. */
  def supported: Boolean = CaptionAccess.supported()

  /** The device's caption setting now, or None where the platform has none to tell. */
  def device: Option[CaptionPreference] = CaptionAccess.device()

  /** `changed` with the setting each time the viewer changes it, until the function it answers is called. Where the
    * platform says nothing of a change (a browser, the desktop), it is never heard. */
  def listen(changed: Option[CaptionPreference] => Unit): () => Unit = CaptionAccess.listen(() => changed(device))

  /** The text track the device's setting would read of `tracks`: the first in its language, else the first there is;
    * None where the setting is off or unknown, or there is no text track. */
  def deviceTrack(device: Option[CaptionPreference], tracks: List[MediaTrack]): Option[String] =
    device.filterNot(_.off).flatMap { p =>
      val texts = tracks.filter(_.kind == MediaTrack.Text)
      val tag = p.language.map(l => MediaTrack.languageTag(l)).filter(_.nonEmpty)
      tag.flatMap(t => texts.find(x => MediaTrack.languageTag(x.language) == t)).orElse(texts.headOption).map(_.id)
    }

  /** The text track to show, or None for none. A track the app chose (`chosen`, with `choice` its id, None or "" for
    * none) wins; with nothing chosen, the device's: its track ([[deviceTrack]]) where its captions are on, none where
    * they are off or it does not say. A mode that shows captions only at times (On replay, When mute) is the platform's
    * to follow, where its player can: this answers None for it. */
  def choose(chosen: Boolean, choice: Option[String], device: Option[CaptionPreference],
             tracks: List[MediaTrack]): Option[String] =
    if (chosen) choice.filter(_.nonEmpty)
    else if (device.exists(_.on)) deviceTrack(device, tracks)
    else None
}
