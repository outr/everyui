package everyui.list

import everyui.Color
import everyui.chat.Participant
import everyui.component.Component
import everyui.font.MaterialIcon
import everyui.tooltip.TipContext

/** How a `RichList` shows one item: its `title`, with a second line of `detail` under it (a snippet, an address), led
  * by `who`'s avatar (their picture, or their initial on their colour) or else an `icon`, and at its end `time` (epoch
  * milliseconds, said as how long ago: "now", "5m", "3h", "2d", "9/16"), a `count` badge ("99+" past 99, none at 0) and
  * a small `mark` (a pin, a lock). An `unread` one is in bold, its time lit. A `busy` one (an agent at work) has a ring
  * turning around its avatar (`Avatar.busy`), whether or not it is unread too, and `busyDetail` ("Running bash") is
  * what a pointer resting on the avatar says, and said to a screen reader with the row. `action` is a small button at
  * its end, before the time (or the ⋮), that does a thing to it without choosing it (a star), shown while the pointer
  * or the cursor is on the row, or always where `actionShown` (a starred one's filled star) or the action's own `shown`.
  * A `live` one is held somewhere else in the app (open in a tab, playing, connected, pinned to a pane): a faint tint
  * of the accent behind it and a thin accent bar at its start edge, apart from the list's own choice (`selected`), which
  * stays the stronger; `liveLabel` ("open in a tab") is said to a screen reader with the row while it is live. A
  * `meter` (`RichMeter`: a quota's use) is a bar on a line of its own under the title and the detail, taking the room
  * its words at its end leave, which are never cut, its colour turning as it fills; its words are said with the row.
  *
  * `tip` is a view of the item's own for its row's tip (`Component.tipView`: a `RichTip`, say, with a face, the last
  * message's first words and who asks), made as it opens (the pointer resting on the row, the cursor on it, a finger
  * held where that opens no menu) and taken apart as it closes; a row handed another item closes the one showing and
  * shows that item's. None by default. */
case class RichLook(title: String,
                    detail: Option[String] = None,
                    who: Option[Participant] = None,
                    icon: Option[MaterialIcon] = None,
                    iconColor: Option[Color] = None,
                    time: Option[Long] = None,
                    count: Int = 0,
                    unread: Boolean = false,
                    mark: Option[MaterialIcon] = None,
                    busy: Boolean = false,
                    busyDetail: String = "",
                    action: Option[RichAction] = None,
                    actionShown: Boolean = false,
                    live: Boolean = false,
                    liveLabel: Option[String] = None,
                    meter: Option[RichMeter] = None,
                    tip: Option[TipContext => Component] = None)
