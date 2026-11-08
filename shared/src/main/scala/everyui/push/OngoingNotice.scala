package everyui.push

/** A notice that stays while something goes on (places being followed, a sync paused until six): its key, which a
  * later one with the same key replaces and `Ongoing.clear` takes away; its title and body; its channel; its actions
  * (the first three: no more are shown); and what a tap on the notice itself does, if anything. */
case class OngoingNotice(key: String,
                         title: String,
                         body: String = "",
                         channel: NoticeChannel,
                         actions: Vector[NoticeAction] = Vector.empty,
                         tap: Option[NoticeTap] = None) {
  /** The actions shown: the first `OngoingNotice.mostActions`. */
  def shownActions: Vector[NoticeAction] = actions.take(OngoingNotice.mostActions)
}

object OngoingNotice {
  /** The most actions a notice shows. */
  val mostActions: Int = 3
}
