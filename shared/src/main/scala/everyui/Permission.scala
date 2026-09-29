package everyui

/** Where a permission the platform asks the person for stands (`Push.permission`, `Location.permission`): given,
  * refused, or not asked yet, when asking shows the platform's prompt. Refused may still be asked again on some
  * platforms, but the person usually has to change it in the settings. */
sealed trait Permission

object Permission {
  /** Given: what needs it works. */
  case object Granted extends Permission
  /** Refused by the person or the platform's policy, or there is nothing here to give. */
  case object Refused extends Permission
  /** Not asked yet: asking (`permit()`) shows the prompt. */
  case object NotAsked extends Permission
}
