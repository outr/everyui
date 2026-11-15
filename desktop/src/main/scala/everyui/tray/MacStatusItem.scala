package everyui.tray

import everyui.Color
import everyui.menu.MenuItem
import java.lang.foreign.MemorySegment

/** A `TrayIcon` on macOS (`MacTray`): an NSStatusItem in the menu bar, its picture an NSImage (`MacImage`), its menu an
  * NSMenu (`MacMenu`). Each change is made on the main thread, where AppKit is to be spoken to (`ObjC.main`), in the
  * order it came. With a popover, a left click on it tells the icon, at the foot of the menu bar under the item; a
  * right click or a control-click opens the menu. With none, the menu is the item's, and any click opens it, as macOS's
  * own do. A choice in the menu runs on the app's thread.
  *
  * A template picture is drawn in the menu bar's colour by macOS. A badge, which a template could not show, is drawn on
  * the picture inked white or black as the menu bar's words are (`MacImage.inked`), again as the appearance changes. */
private[tray] class MacStatusItem(icon: TrayIcon) extends TrayHost {
  import ObjC.*

  @volatile private var shown: TrayImage = icon.icon()
  @volatile private var badge: Option[Color] = None
  @volatile private var items: List[MenuItem] = icon.menu()
  @volatile private var popping = false
  @volatile private var gone = false
  // AppKit's, made and touched on the main thread only
  private var item: MemorySegment = NULL
  private[tray] var button: MemorySegment = NULL
  private[tray] var target: MemorySegment = NULL
  private[tray] var menuShown: Option[MacMenu] = None

  ObjC.main {
    item = retain(send(SendD, id(cls("NSStatusBar"), "systemStatusBar"), "statusItemWithLength:", -1.0)
      .asInstanceOf[MemorySegment])
    button = id(item, "button")
    target = MacTray.target(this)
    val m = new MacMenu(target)
    m.fill(items)
    menuShown = Some(m)
    id(button, "setTarget:", target)
    id(button, "setAction:", sel("clicked:"))
    withLong(button, "sendActionOn:", MacTray.LeftMouseUpMask | MacTray.RightMouseUpMask)
    id(item, "setMenu:", m.ns)
  }

  def picture(i: TrayImage, b: Option[Color]): Unit = {
    shown = i
    badge = b
    ObjC.main(draw())
  }

  // the picture set, inked for the menu bar as it is now where it has a badge
  private[tray] def draw(): Unit = if (!gone && !isNil(button)) {
    val drawn = badge.map(c => MacImage.inked(shown, darkBar).badged(c)).getOrElse(shown)
    val image = MacImage.ns(drawn)
    id(button, "setImage:", image)
    release(image)
  }

  /** Whether a badge is drawn, and so the picture inked again as the menu bar's appearance changes. */
  private[tray] def badged: Boolean = badge.nonEmpty

  // whether the menu bar under the item is dark (its words white)
  private def darkBar: Boolean = {
    val appearance = id(button, "effectiveAppearance")
    !isNil(appearance) && text(id(appearance, "bestMatchFromAppearancesWithNames:",
      array(str("NSAppearanceNameAqua"), str("NSAppearanceNameDarkAqua")))) == "NSAppearanceNameDarkAqua"
  }

  def tip(title: String, tooltip: String): Unit = ObjC.main {
    if (!gone && !isNil(button)) id(button, "setToolTip:", str(if (tooltip.isEmpty) title else title + "\n" + tooltip))
  }

  // macOS's menu bar has no way for an item to ask to be looked at, short of changing its picture, which is the app's
  def attention(on: Boolean): Unit = ()

  def menu(items: List[MenuItem]): Unit = {
    this.items = items
    ObjC.main(if (!gone) menuShown.foreach(_.fill(items)))
  }

  def popover(on: Boolean): Unit = {
    popping = on
    ObjC.main(if (!gone && !isNil(item)) id(item, "setMenu:", if (on) NULL else menuShown.map(_.ns).getOrElse(NULL)))
  }

  /** The menu filled again, as it is about to open. */
  private[tray] def opening(): Unit = if (!gone) menuShown.foreach(_.fill(items))

  /** A click on the item, as AppKit tells it (it has a menu only while there is no popover): a right click or a
    * control-click opens the menu, a left click tells the icon where the item is. */
  private[tray] def clicked(): Unit = if (!gone) {
    val event = id(id(cls("NSApplication"), "sharedApplication"), "currentEvent")
    val right = !isNil(event) &&
      (long(event, "type") == MacTray.RightMouseUp || (long(event, "modifierFlags") & MacTray.ControlKey) != 0L)
    if (right || !popping) popMenu()
    else {
      val window = id(button, "window")
      val primary = id(id(cls("NSScreen"), "screens"), "firstObject")
      if (!isNil(window) && !isNil(primary)) {
        val f = rect(window, "frame")
        val (x, y) = MacTray.javafxPoint(f(0), f(1), f(2), rect(primary, "frame")(3))
        javafx.application.Platform.runLater(() => icon.clicked(x, y))
      }
    }
  }

  // the menu opened under the item, then the item's again only where it has no popover
  private[tray] def popMenu(): Unit = menuShown.foreach { m =>
    id(item, "setMenu:", m.ns)
    id(button, "performClick:", NULL)
    if (popping) id(item, "setMenu:", NULL)
  }

  /** An entry of the menu chosen, by its tag: its action run on the app's thread. */
  private[tray] def chosen(tag: Long): Unit = if (!gone) menuShown.flatMap(_.entry(tag)).foreach { m =>
    javafx.application.Platform.runLater(() => m.action())
  }

  def close(): Unit = if (!gone) {
    gone = true
    ObjC.main {
      if (!isNil(item)) {
        id(id(cls("NSStatusBar"), "systemStatusBar"), "removeStatusItem:", item)
        release(item)
      }
      menuShown.foreach(_.release())
      menuShown = None
      if (!isNil(target)) {
        MacTray.forget(target)
        release(target)
      }
      item = NULL
      button = NULL
      target = NULL
    }
  }
}
