package everyui.event

/** A key, pressed or released, the platform's own name for it ("a", "F5", "MediaTrackNext"), and the modifier keys held
  * with it. A remote has no modifiers: on the Roku and tvOS they are always false. */
case class KeyEvent(key: Key, name: String, ctrl: Boolean = false, meta: Boolean = false, alt: Boolean = false,
                    shift: Boolean = false)
