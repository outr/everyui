package everyui.text

import everyui.component.{MediaTrack, TextChoice}
import everyui.render.CaptionAccess

/** The device's own caption setting: whether the viewer wants captions, and in what language. A Roku says it through
  * roDeviceInfo (its mode: On, Off, On replay, When mute, and its preferred caption language), Android through its
  * CaptioningManager (on or off, and its locale), an Apple TV through MediaAccessibility (Always On, Automatic or off,
  * and its preferred languages). A browser and the desktop tell an app nothing of the kind (`supported` false, `device`
  * None).
  *
  * A [[everyui.component.VideoView]] follows it by itself: until the app chooses (`captions`, or a text language
  * through `languages`), what it shows is the device's choice, as [[choose]] decides it. A Roku lets an app write it
  * too (`writable`, `setDevice`), for the caption menu its certification asks for.
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

  /** The text track to show, or None for none. What the app chose wins (`TextOff`, `TextTrack`); left to the device
    * (`TextFromDevice`), its track ([[deviceTrack]]) where its captions are on, none where they are off or it does not
    * say. A mode that shows captions only at times (On replay, When mute) is the platform's to follow, where its player
    * can: this answers None for it. */
  def choose(choice: TextChoice, device: Option[CaptionPreference], tracks: List[MediaTrack]): Option[String] =
    choice match {
      case TextChoice.TextTrack(id) => Some(id).filter(_.nonEmpty)
      case TextChoice.TextOff => None
      case TextChoice.TextFromDevice => if (device.exists(_.on)) deviceTrack(device, tracks) else None
    }

  /** Whether the app may change the device's own caption setting here: a Roku's, which its options menu and a
    * channel's caption menu both write. Android, an Apple TV, a browser and the desktop keep theirs to the viewer. */
  def writable: Boolean = CaptionAccess.writable()

  /** The device's caption mode set to `mode`, for an app's own caption menu (Roku's certification, 4.8, asks for On,
    * Off, On instant replay and On mute there): true where it was written, false where the platform does not let an
    * app write it or has no such mode (Automatic on a Roku). It is the viewer's choice made through the app, so what
    * `listen` hears, and a video goes back to the device's captions (`TextFromDevice`). */
  def setDevice(mode: CaptionMode): Boolean = CaptionAccess.setDevice(mode)
}
