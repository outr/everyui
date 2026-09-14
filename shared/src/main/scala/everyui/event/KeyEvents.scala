package everyui.event

import reactify.Channel

/** The keys while the component has the focus. */
class KeyEvents {
  lazy val down: Channel[KeyEvent] = Channel[KeyEvent]
  lazy val up: Channel[KeyEvent] = Channel[KeyEvent]
}
