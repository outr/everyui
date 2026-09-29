package everyui.list

import everyui.Color
import everyui.chat.Participant
import everyui.font.MaterialIcon

/** How a `RichList` shows one item: its `title`, with a second line of `detail` under it (a snippet, an address), led
  * by `who`'s avatar (their picture, or their initial on their colour) or else an `icon`, and at its end `time` (epoch
  * milliseconds, said as how long ago: "now", "5m", "3h", "2d", "9/16"), a `count` badge ("99+" past 99, none at 0) and
  * a small `mark` (a pin, a lock). An `unread` one is in bold, its time lit. */
case class RichLook(title: String,
                    detail: Option[String] = None,
                    who: Option[Participant] = None,
                    icon: Option[MaterialIcon] = None,
                    iconColor: Option[Color] = None,
                    time: Option[Long] = None,
                    count: Int = 0,
                    unread: Boolean = false,
                    mark: Option[MaterialIcon] = None)
