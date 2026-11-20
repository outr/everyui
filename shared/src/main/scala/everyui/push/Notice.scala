package everyui.push

import everyui.Color
import everyui.font.MaterialIcon

/** A notification the app shows itself (`Platform.showNotice`), as a mail app tells of a new message: `title` and `body`;
  * `tag` names it, so a later one with the same tag replaces it rather than standing beside it (one per conversation),
  * "" for none; `icon` a picture's URL to show with it, "" for the platform's own (on the web the browser shows the
  * site's); `onlyWhenHidden` (the default) shows it only while the person is not looking at the app: on the web, while
  * the page is hidden or another window has the focus, on Android while the app is not in front. `noticeIcon` is the
  * Material icon Android draws its small icon from, and its large icon on a circle of `noticeColor` (`LargeIcon`), as
  * a push's "noticeIcon" and "noticeColor" are, the app's `everyui_notice` and no large icon where it has none.
  * `conversation` makes it a message of a conversation, as a push's "noticeConversation" does (`PushConversation`): on
  * Android stacked with the conversation's others and headed by the sender's face, on the web titled with the sender
  * and the conversation (`PushConversation.heading`). */
case class Notice(title: String, body: String = "", tag: String = "", icon: String = "", onlyWhenHidden: Boolean = true,
                  noticeIcon: Option[MaterialIcon] = None, noticeColor: Option[Color] = None,
                  conversation: Option[PushConversation] = None)
