package everyui.location

/** The next step towards the location with the app closed on Android (`Location.permitBackground`), as the platform's
  * version and what the person has given already decide it. Its cases' names are their own, since a Roku channel's
  * type names are global. */
sealed trait BackgroundAsk

object BackgroundAsk {
  /** Given already: before Android 10 with the location itself, from 10 on as "Allow all the time". */
  case object BackgroundGiven extends BackgroundAsk
  /** The location itself first: Android gives it with the app closed only on top of it. */
  case object ForegroundFirst extends BackgroundAsk
  /** Android 10's prompt, which offers "Allow all the time". */
  case object BackgroundPrompt extends BackgroundAsk
  /** Android 11 and later: the app's location page in the settings, where the person chooses "Allow all the time". */
  case object BackgroundSettings extends BackgroundAsk
  /** The manifest does not declare ACCESS_BACKGROUND_LOCATION (`everyuiLocation`), so it cannot be given. */
  case object BackgroundUndeclared extends BackgroundAsk

  /** The step on Android `sdk`, the background permission `declared` in the manifest or not, with the location itself
    * (`foreground`) and with the app closed (`background`) held or not. */
  def step(sdk: Int, declared: Boolean, foreground: Boolean, background: Boolean): BackgroundAsk =
    if (sdk < 29) { if (foreground) BackgroundGiven else ForegroundFirst }
    else if (background && foreground) BackgroundGiven
    else if (!declared) BackgroundUndeclared
    else if (!foreground) ForegroundFirst
    else if (sdk == 29) BackgroundPrompt
    else BackgroundSettings
}
