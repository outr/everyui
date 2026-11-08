package everyui.push

import everyui.render.{BackgroundAccess, Platform}

/** Notices that stay while something goes on, with actions that run in the background or open the app: shown,
  * updated in place by their key, and cleared, from the app or from its `BackgroundApp` with the app closed. They are
  * quiet (a channel of low importance unless the app says otherwise) and not swiped away; from Android 14 the person
  * may swipe one away all the same, and `BackgroundApp.onNoticeDismissed` hears it.
  *
  * Android has them (Android 13 and later once the person allows notifications:
  * `Platform.askNotificationPermission`); an action that runs in the background needs the build to name a
  * `BackgroundApp` (`everyuiBackground`). The web, the desktop, an Apple TV and a Roku have none: `supported` is false
  * and `show` shows nothing.
  *
  * {{{
  *   val channel = NoticeChannel("places", "Places", "While places are being noted")
  *   Ongoing.show(OngoingNotice("places", "Noting places", "Since 9:12", channel,
  *     Vector(NoticeAction("Pause", NoticeTap.RunAction("pause")),
  *       NoticeAction("Open", NoticeTap.OpenApp("/places")))))
  * }}} */
object Ongoing {
  /** Whether this platform shows ongoing notices. */
  def supported: Boolean = BackgroundAccess.ongoingSupported()

  /** `notice` shown, or put in place of the one showing with its key without a sound. Whether it is showing: not
    * where the platform has none or the person has not allowed notices. */
  def show(notice: OngoingNotice): Boolean = {
    if (notice.actions.size > OngoingNotice.mostActions)
      Platform.log("ongoing: " + notice.key + " shows its first " + OngoingNotice.mostActions.toString +
        " actions of " + notice.actions.size.toString)
    supported && BackgroundAccess.showOngoing(notice)
  }

  /** The notice with `key` taken away, if it is showing. */
  def clear(key: String): Unit = if (supported) BackgroundAccess.clearOngoing(key)
}
