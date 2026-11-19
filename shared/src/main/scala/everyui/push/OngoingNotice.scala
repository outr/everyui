package everyui.push

import everyui.font.MaterialIcon

/** A notice that stays while something goes on (places being followed, a sync paused until six): its key, which a
  * later one with the same key replaces and `Ongoing.clear` takes away; its title and body; its channel; its actions
  * (the first three: no more are shown); what a tap on the notice itself does, if anything; and the Material icon its
  * small icon is drawn from on Android (`noticeIcon`), the app's `everyui_notice` where it has none. */
case class OngoingNotice(key: String,
                         title: String,
                         body: String = "",
                         channel: NoticeChannel,
                         actions: Vector[NoticeAction] = Vector.empty,
                         tap: Option[NoticeTap] = None,
                         noticeIcon: Option[MaterialIcon] = None) {
  /** The actions shown: the first `OngoingNotice.mostActions`. */
  def shownActions: Vector[NoticeAction] = actions.take(OngoingNotice.mostActions)
}

object OngoingNotice {
  /** The most actions a notice shows. */
  val mostActions: Int = 3
}
