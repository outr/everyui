package everyui.tray

/** Whose tray an app is in: the operating system it runs on. */
private[tray] enum TrayPlatform {
  case Linux, Mac, Windows
}

private[tray] object TrayPlatform {
  /** The one this runs on, by `os.name`; anything not macOS or Windows is taken for Linux (the BSDs speak D-Bus too). */
  lazy val current: TrayPlatform = {
    val os = System.getProperty("os.name", "").toLowerCase
    if (os.startsWith("mac") || os.contains("darwin")) Mac else if (os.startsWith("windows")) Windows else Linux
  }
}
