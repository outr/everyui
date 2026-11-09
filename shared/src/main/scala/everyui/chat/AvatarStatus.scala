package everyui.chat

import everyui.Color

/** What somebody is up to, shown on their avatar as a dot of `colour` at its corner (green for there, amber for away,
  * red for busy in a meeting or not to be disturbed), with `words` ("In a meeting · until 3:00 PM") what a pointer
  * resting on the face shows and a screen reader hears after their name. Set once for a person in `Avatar.statuses`,
  * it is on every face of theirs that EveryUI or the app draws; `Avatar.status` sets one face's own.
  * {{{
  *   Avatar.statuses @= Map("u-17" -> AvatarStatus(Color.fromHex("#e53935"), "In a meeting · until 3:00 PM"))
  * }}} */
case class AvatarStatus(colour: Color, words: String)
