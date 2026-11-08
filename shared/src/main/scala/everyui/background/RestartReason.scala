package everyui.background

/** Why the app's `BackgroundApp.onRestart` is called: the device started again, or the app was updated in place. */
enum RestartReason {
  case Booted, Updated
}
