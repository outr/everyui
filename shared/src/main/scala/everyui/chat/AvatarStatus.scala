package everyui.chat

import everyui.Color
import everyui.font.MaterialIcon

/** What somebody is up to, shown on their avatar as a dot of `colour` at its corner (green for there, amber for away,
  * red for busy in a meeting or not to be disturbed), with `words` ("In a meeting · until 3:00 PM") what a pointer
  * resting on the face shows and a screen reader hears after their name. With an `icon` (a calendar for a meeting, a
  * plane for out of office) the dot is a badge of `colour` with the glyph on it, in white or black, whichever reads on
  * the colour; a face too small to read a glyph on (`Avatar.badges`) shows the plain dot. The icon is only drawn: a
  * screen reader hears the words. Set once for a person in `Avatar.statuses`, it is on every face of theirs that
  * EveryUI or the app draws; `Avatar.status` sets one face's own.
  * {{{
  *   Avatar.statuses @= Map(
  *     "u-17" -> AvatarStatus(Color.fromHex("#e53935"), "In a meeting · until 3:00 PM", Some(MaterialIcon.Event)),
  *     "u-18" -> AvatarStatus(Color.fromHex("#43a047"), "Online"))
  * }}} */
case class AvatarStatus(colour: Color, words: String, icon: Option[MaterialIcon] = None)
