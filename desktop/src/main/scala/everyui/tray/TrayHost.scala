package everyui.tray

import everyui.Color
import everyui.menu.MenuItem

/** What shows a `TrayIcon` on one platform: a StatusNotifierItem over D-Bus on Linux (`SniTray`), an NSStatusItem on
  * macOS (`MacTray`), a notification-area icon on Windows (`WinTray`). The icon tells it each change as it is made, on
  * the app's thread; it tells the icon of a click with `TrayIcon.clicked`, on the app's thread too, the point in
  * JavaFX's screen coordinates (logical pixels), and runs a menu entry's action there. */
private[tray] trait TrayHost {
  /** The picture to show, and the dot to draw at its corner, where there is one. */
  def picture(icon: TrayImage, badge: Option[Color]): Unit
  /** What a pointer resting on the icon says: the app's `title` over `tooltip`. */
  def tip(title: String, tooltip: String): Unit
  /** Whether the icon asks to be looked at, where the platform has a way to say so. */
  def attention(on: Boolean): Unit
  /** The menu's entries, as they are now. */
  def menu(items: List[MenuItem]): Unit
  /** Whether a left click opens a popover (`TrayIcon.popover`): where it does not, a platform whose clicks all open the
    * menu (macOS) opens it on a left click too. */
  def popover(on: Boolean): Unit
  /** Where the icon is, as its click would say it, told to `found` on the app's thread: None where the platform has no
    * way to ask (a StatusNotifierItem's panel tells only a click). */
  def locate(found: Option[(Int, Int)] => Unit): Unit
  /** The icon out of the tray, for good. */
  def close(): Unit
}
