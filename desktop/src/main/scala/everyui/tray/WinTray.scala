package everyui.tray

/** A `TrayIcon` on Windows: an icon in the notification area (`Shell_NotifyIconW`), reached with the FFM API. */
private[tray] object WinTray {
  def supported: Boolean = false

  def host(icon: TrayIcon): TrayHost = throw new UnsupportedOperationException("not yet")

  def notify(title: String, body: String, clicked: Option[() => Unit]): Unit = ()
}
