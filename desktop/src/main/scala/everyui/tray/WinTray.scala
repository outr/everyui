package everyui.tray

import java.lang.foreign.MemorySegment

/** A `TrayIcon` on Windows: an icon in the notification area (`Shell_NotifyIconW`), reached with the FFM API
  * (`WinTrayIcon`, on a hidden window of its own, `WinWindow`). A notification is a balloon from the app's icon where
  * one is shown, which Windows 10 and 11 show as a toast; where none is, from an icon put there for it (Windows' own
  * application icon, as a balloon comes only from an icon that is shown) and taken out as the balloon goes. */
private[tray] object WinTray {
  // the icons shown, newest first
  @volatile private var shown: List[WinTrayIcon] = Nil

  /** Whether this is Windows, shell32 loads, and a window for the icon can be made. */
  lazy val supported: Boolean = TrayPlatform.current == TrayPlatform.Windows && (try {
    Win32.Shell_NotifyIconW
    val w = new WinWindow((_, _, _) => None)
    val made = w.made
    w.close()
    made
  } catch {
    case t: Throwable =>
      everyui.scribe.warn("tray: no notification area: " + t)
      false
  })

  def host(icon: TrayIcon): TrayHost = {
    val h = new WinTrayIcon(icon)
    synchronized { shown = h :: shown }
    h
  }

  private[tray] def forget(h: WinTrayIcon): Unit = synchronized { shown = shown.filterNot(_ eq h) }

  def notify(title: String, body: String, clicked: Option[() => Unit]): Unit = if (supported) {
    shown.find(_.made) match {
      case Some(h) => h.balloon(title, body, clicked)
      case None => alone(title, body, clicked)
    }
  }

  // a balloon from an icon of its own, taken out as it is clicked, times out, or after a minute where neither is said
  private def alone(title: String, body: String, clicked: Option[() => Unit]): Unit = {
    var window: WinWindow = null
    window = new WinWindow((msg, _, lParam) => {
      if (window == null) ()
      else if (msg == Win32.WM_TRAY) (lParam & 0xffff).toInt match {
        case Win32.NIN_BALLOONUSERCLICK =>
          clicked.foreach(c => javafx.application.Platform.runLater(() => c()))
          window.close()
        case Win32.NIN_BALLOONTIMEOUT | Win32.NIN_BALLOONHIDE => window.close()
        case _ => ()
      }
      else if (msg == Win32.WM_DESTROY) Win32.deleteIcon(window.hwnd)
      None
    })
    window.post(() => {
      val picture = (Win32.LoadIconW.invokeExact(MemorySegment.NULL, MemorySegment.ofAddress(Win32.IDI_APPLICATION)): MemorySegment)
      val shownNow = Win32.addIcon(window.hwnd, picture, title) &&
        Win32.balloon(window.hwnd, title, body, Win32.NIIF_INFO, MemorySegment.NULL)
      if (!shownNow) {
        everyui.scribe.warn("tray: the notification was not shown")
        window.close()
      }
    })
    val reaper = new Thread(() => {
      Thread.sleep(60000L)
      window.close()
    }, "everyui-tray-balloon")
    reaper.setDaemon(true)
    reaper.start()
  }
}
