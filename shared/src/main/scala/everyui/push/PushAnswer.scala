package everyui.push

/** How `Push.subscribe` ended. Its cases' names are their own, since a Roku channel's type names are global. */
sealed trait PushAnswer

object PushAnswer {
  /** Subscribed: `token` for the server, sent on to it (and again whenever `Push.tokens` hears a new one). */
  case class Subscribed(token: PushToken) extends PushAnswer
  /** The person refused notifications (or has before). */
  case object PushDenied extends PushAnswer
  /** No push here: the desktop, a Roku, an Apple TV, a browser without the Push API, an Android TV or an Android app
    * built without Firebase Cloud Messaging. */
  case object PushUnsupported extends PushAnswer
  /** Anything else, as the platform put it: no service worker on the page, the push service out of reach, no Firebase
    * configuration in the app. */
  case class PushFailed(message: String) extends PushAnswer
}
