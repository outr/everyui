package everyui.chat

import everyui.font.MaterialIcon

/** Something an app does from a [[Composer]]'s paused queue (`Composer.queueActions`): what it says, what it does,
  * and an icon before its words. It shows after the composer's own (Resume, Send next, Clear queue), or in their ⋮
  * where the line has no room for it. */
case class QueueAction(label: String, action: () => Unit, icon: Option[MaterialIcon] = None)
