package everyui.tray

/** A `TrayIcon` on macOS: an NSStatusItem in the menu bar, reached through AppKit with the FFM API. */
private[tray] object MacTray {
  def supported: Boolean = false

  def host(icon: TrayIcon): TrayHost = throw new UnsupportedOperationException("not yet")

  def notify(title: String, body: String, actions: List[(String, () => Unit)], clicked: Option[() => Unit]): Unit = ()
}
