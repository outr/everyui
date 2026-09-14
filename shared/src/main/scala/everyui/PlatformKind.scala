package everyui

/** Which platform the app is running on — `Platform.kind`. What it can do is asked separately (`Platform.touchSupport`,
  * `pointerSupport`, `remoteNavigation`, `keyboardSupport`), since a TV browser is Web with a remote and an Android
  * tablet is Android with touch. */
enum PlatformKind {
  case Web, Desktop, Android, Roku, TvOS
}
