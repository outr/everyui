package everyui.chat

import everyui.font.MaterialIcon

/** Something the app does to one message waiting in a [[Composer]]'s queue (`Composer.queuedActions`), from a button on
  * its chip before Edit and Remove: an icon, what it does (its tooltip, and what a screen reader says of it), and the
  * doing, which answers whether the message is done with and leaves the queue ("Send now", sent by the app: true) or
  * stays where it is (false). */
case class QueuedAction(icon: MaterialIcon, label: String, action: () => Boolean)
