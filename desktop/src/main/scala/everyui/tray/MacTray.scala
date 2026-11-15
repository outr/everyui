package everyui.tray

import java.lang.foreign.MemorySegment
import java.lang.invoke.{MethodHandles, MethodType}
import java.util.concurrent.ConcurrentHashMap

/** A `TrayIcon` on macOS: an NSStatusItem in the menu bar (`MacStatusItem`), reached through AppKit with the FFM API
  * (`ObjC`), on the JVM and in a native image alike. What AppKit tells an item (a click, a choice in its menu, its menu
  * about to open, the appearance changed) comes to an object of a class made here at run time, one for each item
  * (`target`), whose methods are calls back into the item. Notifications go through the notification centre
  * (`MacNotifier`). */
private[tray] object MacTray {
  import ObjC.*

  /** NSEventTypeRightMouseUp, the masks of a left and a right mouse up, and the control key among modifierFlags. */
  val RightMouseUp: Long = 4L
  val LeftMouseUpMask: Long = 1L << 2
  val RightMouseUpMask: Long = 1L << 4
  val ControlKey: Long = 1L << 18

  /** Whether there is a menu bar to show an icon in: on macOS, where AppKit loads and has NSStatusBar. */
  lazy val supported: Boolean = TrayPlatform.current == TrayPlatform.Mac && {
    try {
      load("AppKit")
      !isNil(cls("NSStatusBar"))
    }
    catch { case t: Throwable =>
      everyui.scribe.warn("tray: no menu bar to show an icon in: " + t)
      false
    }
  }

  def host(icon: TrayIcon): TrayHost = new MacStatusItem(icon)

  def notify(title: String, body: String, actions: List[(String, () => Unit)], clicked: Option[() => Unit]): Unit =
    if (supported) MacNotifier.notify(title, body, actions, clicked)

  /** Where JavaFX has the foot of an item whose window's frame starts at `x`, `y` (from the primary screen's lower
    * left, as Cocoa has it) and is `width` wide: its middle, at the bottom of the menu bar, counted from the primary
    * screen's top (`primaryHeight` tall). */
  def javafxPoint(x: Double, y: Double, width: Double, primaryHeight: Double): (Int, Int) =
    (math.round(x + width / 2.0).toInt, math.round(primaryHeight - y).toInt)

  // each target, by its address, with the item it speaks for
  private val hosts = new ConcurrentHashMap[Long, MacStatusItem]()
  private val Theme = "AppleInterfaceThemeChangedNotification"

  /** A message to a target, on the main thread: `cmd` the selector, `sender` what sent it. */
  def answer(self: MemorySegment, cmd: MemorySegment, sender: MemorySegment): Unit =
    try {
      val h = hosts.get(self.address)
      if (h != null) {
        val c = cmd.address
        if (c == sel("clicked:").address) h.clicked()
        else if (c == sel("chosen:").address) h.chosen(long(sender, "tag"))
        else if (c == sel("menuNeedsUpdate:").address) h.opening()
        // the appearance takes a moment to change after the notice
        else if (c == sel("appearanceChanged:").address && h.badged) ObjC.main(h.draw())
      }
    }
    catch { case t: Throwable => everyui.scribe.warn("tray: " + t) }

  private lazy val targetClass: MemorySegment = {
    val imp = upcall(MethodHandles.lookup().findVirtual(getClass, "answer", MethodType.methodType(Void.TYPE,
      classOf[MemorySegment], classOf[MemorySegment], classOf[MemorySegment])).bindTo(this), Method)
    subclass("EveryUITrayTarget",
      List("clicked:", "chosen:", "menuNeedsUpdate:", "appearanceChanged:").map(s => (s, "v@:@", imp)),
      List("NSMenuDelegate"))
  }

  /** A target for `host`'s item, its menu and its menu's entries (+1: `forget`, then release it), told when the
    * appearance changes. On the main thread. */
  def target(host: MacStatusItem): MemorySegment = {
    val t = id(id(targetClass, "alloc"), "init")
    hosts.put(t.address, host)
    id(id(cls("NSDistributedNotificationCenter"), "defaultCenter"), "addObserver:selector:name:object:", t,
      sel("appearanceChanged:"), str(Theme), NULL)
    t
  }

  /** A target no longer told of anything. On the main thread. */
  def forget(t: MemorySegment): Unit = {
    id(id(cls("NSDistributedNotificationCenter"), "defaultCenter"), "removeObserver:", t)
    hosts.remove(t.address)
  }
}
