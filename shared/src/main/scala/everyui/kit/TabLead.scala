package everyui.kit

import everyui.chat.Participant
import everyui.font.MaterialIcon

/** What leads a tab's label (`TabBar.add`): nothing, somebody's avatar (a conversation with them, which turns a ring
  * while the tab is busy), or an icon (a file's type). */
enum TabLead {
  case None
  case Person(who: Participant)
  case Icon(icon: MaterialIcon)
}
