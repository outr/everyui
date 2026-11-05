package everyui.access

import everyui.component.types.Live

/** Words read out to a screen reader (`LiveRegions.say`), and whether they cut in on what it was reading. */
case class Announcement(words: String, how: Live)
