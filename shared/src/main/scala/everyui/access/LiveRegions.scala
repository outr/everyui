package everyui.access

import everyui.component.Component
import everyui.component.types.Live
import everyui.render.Platform
import reactify.*

/** The parts of the screen whose words are read out as they change (`Component.liveRegion`). What a part says is its
  * name to a screen reader (`Access.nameOf`: its label, else the words inside it), read out as it comes on the screen
  * with something to say and each time that changes after: politely, or at once where it is assertive. Not while it
  * is `updating` (a list being filled), and not while hidden; shown again, it is read again. The same on every
  * platform, through `Platform.announce` and `Platform.alert`, rather than ARIA's own live regions, which a browser
  * leaves unread when the region comes with its words already in it. Looked over a turn after the words or the
  * layout change, once however many change at a time, and not at all while there are no such parts. */
object LiveRegions {
  private var regions: List[Component] = Nil
  private var soon = false

  /** Everything read out, as it is, with whether it cut in: for an app's log and for tests. */
  val announced: Channel[Announcement] = Channel[Announcement]

  /** `words` read out by a screen reader: politely, or at once. */
  def say(words: String, how: Live = Live.Polite): Unit = if (words.nonEmpty && how != Live.Off) {
    if (how == Live.Assertive) Platform.alert(words) else Platform.announce(words)
    announced @= Announcement(words, how)
  }

  private[everyui] def watch(c: Component, how: Live): Unit = {
    if (how == Live.Off) forget(c)
    else if (!regions.exists(_ eq c)) {
      regions = c :: regions
      c.onDispose(() => forget(c))
    }
    changed()
  }

  private def forget(c: Component): Unit = {
    regions = regions.filterNot(_ eq c)
    c.liveSaid = ""
  }

  /** Words or the layout changed: the parts are looked over on a turn of their own. */
  private[everyui] def changed(): Unit = if (regions.nonEmpty && !soon) {
    soon = true
    Platform.later(() => {
      soon = false
      look()
    })
  }

  private def look(): Unit = regions.reverse.foreach { c =>
    val now = if (shown(c)) Access.nameOf(c).trim else ""
    if (!c.updating()) {
      if (now != c.liveSaid) say(now, c.liveRegion())
      c.liveSaid = now
    }
  }

  // on the screen: under the app's root, with nothing over it hidden
  private def shown(c: Component): Boolean = {
    var at: Option[Component] = Some(c)
    var showing = true
    var top: Component = c
    while (showing && at.nonEmpty) {
      val part = at.get
      showing = part.notHidden && part.opacity() > 0.0
      top = part
      at = part.parent()
    }
    showing && (top eq everyui.ui)
  }
}
