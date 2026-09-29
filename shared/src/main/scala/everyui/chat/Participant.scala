package everyui.chat

import everyui.Color

/** Someone in a conversation: a person or an agent. `id` is what they are known by (messages are grouped by it, never
  * by the name, which two can share); `avatar` is a picture's URL, and without one they are drawn as the first letter of
  * their name on a colour of their own, the same every time.
  *
  * {{{
  *   val ada = Participant("u-17", "Ada", Some("https://example.com/ada.png"))
  *   val helper = Participant("agent-1", "Helper")
  * }}} */
case class Participant(id: String, name: String, avatar: Option[String] = None) {
  /** The letter they are drawn as without a picture. */
  def initial: String = if (name.isEmpty) "?" else name.substring(0, 1).toUpperCase

  /** Their own colour, from their id: the same on every device and every visit. */
  def colour: Color = Participant.colourOf(id)
}

object Participant {
  /** A colour for `id`: a hue of its own, deep enough that white letters read on it in a light theme and a dark one. */
  def colourOf(id: String): Color = {
    var hash = 7
    var i = 0
    while (i < id.length) {
      hash = (hash * 31 + id.charAt(i).toInt) % 100003
      i += 1
    }
    Color.fromHSB((hash % 360).toDouble / 360.0, 0.55, 0.62)
  }
}
