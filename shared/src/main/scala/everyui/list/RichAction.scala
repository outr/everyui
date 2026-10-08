package everyui.list

import everyui.font.MaterialIcon

/** A thing a `RichList`'s section does from its header ("New conversation"): `icon` drawn as a small button in the
  * header, `label` its tooltip and what a screen reader calls it, and `run` what it does, without opening or closing
  * the section. */
case class RichAction(icon: MaterialIcon, label: String, run: () => Unit)
