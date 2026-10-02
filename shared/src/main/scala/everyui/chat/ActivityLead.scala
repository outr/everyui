package everyui.chat

import everyui.font.MaterialIcon

/** What leads a tool call's line in the transcript (`ChatEntry.Activity.lead`): nothing (the status mark alone, a
  * spinner, a tick or a cross), somebody's avatar (a message sent to them, turning a ring while it goes), or an icon
  * (what kind of call it is: a search, a command). An avatar and an icon carry the status as a badge on their
  * corner. */
enum ActivityLead {
  case None
  case Person(who: Participant)
  case Icon(icon: MaterialIcon)
}
