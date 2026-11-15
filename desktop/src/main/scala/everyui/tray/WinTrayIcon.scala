package everyui.tray

import everyui.Color
import everyui.menu.MenuItem

import java.lang.foreign.{Arena, Linker, MemorySegment}
import java.lang.foreign.ValueLayout.{ADDRESS, JAVA_INT}
import java.lang.invoke.{MethodHandles, MethodType}

/** A `TrayIcon` on Windows: icon 1 of a hidden window of its own (`WinWindow`) in the notification area, put there
  * with `Shell_NotifyIconW` and spoken to as NOTIFYICON_VERSION_4. Its picture is drawn at the size the notification
  * area wants by the screen's DPI (16, 20, 24 or 32 pixels), again as that changes; its tooltip is the app's title over
  * the icon's. A left click (or Enter on it) tells the icon where, in JavaFX's logical pixels; a right click (or the
  * menu key) opens the menu, built afresh from the entries as they are, and runs the one chosen on the app's thread.
  * It comes back when Explorer starts again ("TaskbarCreated"). Windows has no way for an icon to ask to be looked at:
  * `attention` shows nothing, the badge being the way to say a status. A notification (`balloon`) comes from it. */
private[tray] class WinTrayIcon(icon: TrayIcon) extends TrayHost {
  // what it shows, set on the app's thread and read on the window's
  @volatile private var image: TrayImage = icon.icon()
  @volatile private var badge: Option[Color] = icon.badge()
  @volatile private var tipShown: String = Win32.tipText(icon.title(), icon.tooltip())
  @volatile private var shownMenu: WinMenu = WinMenu.of(Nil)
  @volatile private var balloonClicked: Option[() => Unit] = None
  // the ticks followed (on the app's thread), each with the way to stop following it
  private var watching: List[() => Unit] = Nil
  private var items: List[MenuItem] = Nil
  @volatile private var gone = false

  // the window's thread's own
  private var hIcon: MemorySegment = MemorySegment.NULL
  private var px = 0
  private lazy val taskbarCreated: Int = {
    val arena = Arena.ofConfined()
    try (Win32.RegisterWindowMessageW.invokeExact(Win32.wide(arena, "TaskbarCreated")): Int)
    finally arena.close()
  }

  private[tray] val window: WinWindow = new WinWindow(answer)
  window.post(() => {
    // heard even where the app runs elevated and Explorer does not
    Win32.ChangeWindowMessageFilterEx.foreach(f =>
      (f.invokeExact(window.hwnd, taskbarCreated, 1, MemorySegment.NULL): Int))
    add()
  })

  /** Whether its window was made, and the icon not closed. */
  def made: Boolean = window.made && !gone

  private def add(): Unit = {
    redraw()
    if (!Win32.addIcon(window.hwnd, hIcon, tipShown)) everyui.scribe.warn("tray: the notification area took no icon")
  }

  // the picture drawn again at the size wanted now, the old one let go of
  private def redraw(): Unit = {
    val old = hIcon
    px = Win32.smallIcon(window.hwnd)
    hIcon = Win32.makeIcon(badge.map(image.badged).getOrElse(image), px)
    Win32.destroyIcon(old)
  }

  private def modify(flags: Int)(fill: MemorySegment => Unit): Boolean = {
    val arena = Arena.ofConfined()
    try {
      val d = Win32.iconData(arena, window.hwnd, flags)
      fill(d)
      Win32.notifyIcon(Win32.NIM_MODIFY, d)
    } finally arena.close()
  }

  private def iconChanged(): Unit = {
    redraw()
    modify(Win32.NIF_ICON)(d => d.set(ADDRESS, Win32.at("hIcon"), hIcon))
  }

  private def answer(msg: Int, wParam: Long, lParam: Long): Option[Long] = {
    if (msg == Win32.WM_TRAY) {
      tray((lParam & 0xffff).toInt, (wParam & 0xffff).toShort.toInt, ((wParam >> 16) & 0xffff).toShort.toInt)
      Some(0L)
    }
    else if (msg == taskbarCreated && taskbarCreated != 0) {
      add()
      Some(0L)
    }
    else if (msg == Win32.WM_DPICHANGED || msg == Win32.WM_DISPLAYCHANGE || msg == Win32.WM_SETTINGCHANGE) {
      if (Win32.smallIcon(window.hwnd) != px) iconChanged()
      None
    }
    else if (msg == Win32.WM_DESTROY) {
      Win32.deleteIcon(window.hwnd)
      Win32.destroyIcon(hIcon)
      hIcon = MemorySegment.NULL
      None
    }
    else None
  }

  // what the shell says of the icon, at `x`, `y` in physical pixels
  private def tray(event: Int, x: Int, y: Int): Unit = event match {
    case Win32.NIN_SELECT | Win32.NIN_KEYSELECT =>
      val monitors = WinTrayIcon.monitors()
      javafx.application.Platform.runLater(() => {
        val (lx, ly) = Win32.logical(x, y, monitors, WinTrayIcon.screens())
        icon.clicked(lx, ly)
      })
    case Win32.WM_CONTEXTMENU => showMenu(x, y)
    case Win32.NIN_BALLOONUSERCLICK =>
      balloonClicked.foreach(c => javafx.application.Platform.runLater(() => c()))
      balloonClicked = None
    case Win32.NIN_BALLOONTIMEOUT => balloonClicked = None
    case _ => ()
  }

  private def showMenu(x: Int, y: Int): Unit = if (shownMenu.entries.nonEmpty) {
    val m = shownMenu
    val arena = Arena.ofConfined()
    val popup = WinTrayIcon.build(m.entries, arena)
    try {
      // the menu shuts as the focus leaves it only where its window is in front (Microsoft's note on TrackPopupMenu)
      (Win32.SetForegroundWindow.invokeExact(window.hwnd): Int)
      val chosen = (Win32.TrackPopupMenu.invokeExact(popup, Win32.TPM_RETURNCMD | Win32.TPM_RIGHTBUTTON |
        Win32.TPM_NONOTIFY, x, y, 0, window.hwnd, MemorySegment.NULL): Int)
      Win32.post(window.hwnd, Win32.WM_NULL)
      m.chosen(chosen).foreach(run => javafx.application.Platform.runLater(() => run()))
    } finally {
      (Win32.DestroyMenu.invokeExact(popup): Int)
      arena.close()
    }
  }

  def picture(i: TrayImage, b: Option[Color]): Unit = {
    image = i
    badge = b
    window.post(() => iconChanged())
  }

  def tip(title: String, tooltip: String): Unit = {
    tipShown = Win32.tipText(title, tooltip)
    val now = tipShown
    window.post(() => modify(Win32.NIF_TIP | Win32.NIF_SHOWTIP)(d => Win32.put(d, Win32.at("szTip"), now, 128)))
  }

  def attention(on: Boolean): Unit = ()

  def menu(items: List[MenuItem]): Unit = {
    this.items = items
    shownMenu = WinMenu.of(items)
    watch()
  }

  // the `on` of each ticked entry followed, so a tick changed elsewhere is right the next time the menu opens
  private def watch(): Unit = {
    watching.foreach(_())
    watching = WinMenu.of(items).items.toList.flatMap(_.on.toList).map { v =>
      val r = v.attach(_ => shownMenu = WinMenu.of(items))
      () => v.reactions -= r
    }
  }

  def popover(on: Boolean): Unit = ()

  /** A notification from the icon: `title` over `body`, with its own picture, `clicked` run where it is clicked. */
  def balloon(title: String, body: String, clicked: Option[() => Unit]): Unit = window.post(() => {
    balloonClicked = clicked
    if (!Win32.balloon(window.hwnd, title, body, Win32.NIIF_USER, hIcon))
      everyui.scribe.warn("tray: the notification was not shown")
  })

  def close(): Unit = if (!gone) {
    gone = true
    watching.foreach(_())
    watching = Nil
    WinTray.forget(this)
    window.close()
  }
}

private[tray] object WinTrayIcon {
  /** The menu's `entries` as a popup menu (a submenu a popup of its own, under its entry), the words kept in
    * `arena` till it is gone. DestroyMenu takes its submenus with it. */
  def build(entries: List[WinMenu.Entry], arena: Arena): MemorySegment = {
    val menu = (Win32.CreatePopupMenu.invokeExact(): MemorySegment)
    entries.foreach { e =>
      val (item, words) =
        if ((e.flags & Win32.MF_SEPARATOR) != 0) (0L, MemorySegment.NULL)
        else if ((e.flags & Win32.MF_POPUP) != 0) (build(e.submenu, arena).address, Win32.wide(arena, e.label))
        else (e.id.toLong, Win32.wide(arena, e.label))
      (Win32.AppendMenuW.invokeExact(menu, e.flags, item, words): Int)
    }
    menu
  }

  // the monitors' physical bounds, as the last enumeration found them (on the window's thread)
  private var found: List[(Int, Int, Int, Int)] = Nil

  /** Each monitor's left, top, right and bottom, in physical pixels. */
  def monitors(): List[(Int, Int, Int, Int)] = synchronized {
    found = Nil
    (Win32.EnumDisplayMonitors.invokeExact(MemorySegment.NULL, MemorySegment.NULL, enumerated, 0L): Int)
    found.reverse
  }

  def monitor(hmonitor: MemorySegment, hdc: MemorySegment, rect: MemorySegment, data: Long): Int = {
    try {
      val r = rect.reinterpret(16L)
      found = (r.get(JAVA_INT, 0L), r.get(JAVA_INT, 4L), r.get(JAVA_INT, 8L), r.get(JAVA_INT, 12L)) :: found
    } catch { case t: Throwable => everyui.scribe.warn("tray: a monitor not read: " + t) }
    1
  }

  // EnumDisplayMonitors' callback, `monitor`, kept for the life of the process
  private lazy val enumerated: MemorySegment = Linker.nativeLinker().upcallStub(
    MethodHandles.lookup().findVirtual(WinTrayIcon.getClass, "monitor", MethodType.methodType(classOf[Int],
      classOf[MemorySegment], classOf[MemorySegment], classOf[MemorySegment], classOf[Long])).bindTo(WinTrayIcon),
    Win32.MonitorProc, Arena.global())

  /** JavaFX's screens, in logical pixels, each with its scale (on the app's thread). */
  def screens(): List[TrayScreen] = {
    val all = javafx.stage.Screen.getScreens
    (0 until all.size).toList.map(all.get).map(s =>
      TrayScreen(s.getBounds.getMinX, s.getBounds.getMinY, s.getBounds.getWidth, s.getBounds.getHeight,
        s.getOutputScaleX))
  }
}
