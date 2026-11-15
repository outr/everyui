package everyui.tray

import java.lang.foreign.{Arena, FunctionDescriptor, Linker, MemoryLayout, MemorySegment, StructLayout, SymbolLookup}
import java.lang.foreign.MemoryLayout.PathElement
import java.lang.foreign.ValueLayout.{ADDRESS, JAVA_BYTE, JAVA_CHAR, JAVA_INT, JAVA_LONG}
import java.lang.invoke.MethodHandle

/** What the Windows tray (`WinTray`) reaches of Win32, through the FFM API: user32's windows, messages and menus,
  * gdi32's bitmaps, shell32's `Shell_NotifyIconW`, with the layouts and numbers they take, as x64 has them. The
  * libraries are looked up as a call is first made, so the layouts and the conversions here (`bgra`, `mask`,
  * `logical`) can be had on any platform. Only what JDK 22 has of the FFM API is used, for a GraalVM native image. */
private[tray] object Win32 {
  // window messages
  val WM_NULL: Int = 0x0000
  val WM_DESTROY: Int = 0x0002
  val WM_SETTINGCHANGE: Int = 0x001a
  val WM_CONTEXTMENU: Int = 0x007b
  val WM_DISPLAYCHANGE: Int = 0x007e
  val WM_DPICHANGED: Int = 0x02e0
  val WM_APP: Int = 0x8000
  /** The icon's callback, from the shell. */
  val WM_TRAY: Int = WM_APP + 1
  /** Work for the window's thread, waiting in its queue. */
  val WM_WORK: Int = WM_APP + 2

  // what the shell says of the icon (the callback's low word, with NOTIFYICON_VERSION_4)
  val NIN_SELECT: Int = 0x0400
  val NIN_KEYSELECT: Int = 0x0401
  val NIN_BALLOONSHOW: Int = 0x0402
  val NIN_BALLOONHIDE: Int = 0x0403
  val NIN_BALLOONTIMEOUT: Int = 0x0404
  val NIN_BALLOONUSERCLICK: Int = 0x0405

  val NIM_ADD: Int = 0
  val NIM_MODIFY: Int = 1
  val NIM_DELETE: Int = 2
  val NIM_SETVERSION: Int = 4
  val NIF_MESSAGE: Int = 0x01
  val NIF_ICON: Int = 0x02
  val NIF_TIP: Int = 0x04
  val NIF_INFO: Int = 0x10
  val NIF_SHOWTIP: Int = 0x80
  val NIIF_INFO: Int = 0x01
  val NIIF_USER: Int = 0x04
  val NOTIFYICON_VERSION_4: Int = 4

  val MF_STRING: Int = 0x0000
  val MF_GRAYED: Int = 0x0001
  val MF_CHECKED: Int = 0x0008
  val MF_POPUP: Int = 0x0010
  val MF_SEPARATOR: Int = 0x0800
  val TPM_RIGHTBUTTON: Int = 0x0002
  val TPM_NONOTIFY: Int = 0x0080
  val TPM_RETURNCMD: Int = 0x0100

  val SM_CXSMICON: Int = 49
  val WS_EX_TOOLWINDOW: Int = 0x80
  val IDI_APPLICATION: Long = 32512L
  // DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2, a pseudo-handle
  val PerMonitorAware: Long = -4L

  /** NOTIFYICONDATAW as x64 lays it out, every field of it (Vista's GUID and balloon icon too): 976 bytes. */
  val NotifyIconData: StructLayout = MemoryLayout.structLayout(
    JAVA_INT.withName("cbSize"), MemoryLayout.paddingLayout(4),
    ADDRESS.withName("hWnd"),
    JAVA_INT.withName("uID"),
    JAVA_INT.withName("uFlags"),
    JAVA_INT.withName("uCallbackMessage"), MemoryLayout.paddingLayout(4),
    ADDRESS.withName("hIcon"),
    MemoryLayout.sequenceLayout(128, JAVA_CHAR).withName("szTip"),
    JAVA_INT.withName("dwState"),
    JAVA_INT.withName("dwStateMask"),
    MemoryLayout.sequenceLayout(256, JAVA_CHAR).withName("szInfo"),
    JAVA_INT.withName("uVersion"),   // a union with uTimeout
    MemoryLayout.sequenceLayout(64, JAVA_CHAR).withName("szInfoTitle"),
    JAVA_INT.withName("dwInfoFlags"),
    MemoryLayout.sequenceLayout(16, JAVA_BYTE).withName("guidItem"),
    ADDRESS.withName("hBalloonIcon"))

  /** Where `field` of NOTIFYICONDATAW is, in bytes. */
  def at(field: String): Long = NotifyIconData.byteOffset(PathElement.groupElement(field))

  /** `s` as fits a WCHAR array of `max` (its NUL last): cut short, never between the halves of a surrogate pair. */
  def fit(s: String, max: Int): String = {
    val cut = s.take(max - 1)
    if (cut.nonEmpty && cut.length < s.length && Character.isHighSurrogate(cut.last)) cut.dropRight(1) else cut
  }

  /** What the tooltip says: the app's `title`, then `tooltip` on a line of its own, where there is one. */
  def tipText(title: String, tooltip: String): String =
    if (tooltip.isEmpty) title else if (title.isEmpty) tooltip else title + "\n" + tooltip

  /** `image`'s pixels as a 32-bit top-down DIB holds them: B, G, R, A, its alpha straight (an icon's is not
    * premultiplied). */
  def bgra(image: TrayImage): Array[Byte] = {
    val out = new Array[Byte](image.argb.length * 4)
    var i = 0
    while (i < image.argb.length) {
      val p = image.argb(i)
      out(i * 4) = p.toByte
      out(i * 4 + 1) = (p >>> 8).toByte
      out(i * 4 + 2) = (p >>> 16).toByte
      out(i * 4 + 3) = (p >>> 24).toByte
      i += 1
    }
    out
  }

  /** `image`'s mask, as `CreateBitmap` takes one: a bit a pixel, the leftmost the highest, each row padded to a
    * whole 16 bits, set where the picture is clear. */
  def mask(image: TrayImage): Array[Byte] = {
    val row = (image.width + 15) / 16 * 2
    val out = new Array[Byte](row * image.height)
    var y = 0
    while (y < image.height) {
      var x = 0
      while (x < image.width) {
        if ((image.argb(y * image.width + x) >>> 24) == 0) {
          val b = y * row + x / 8
          out(b) = (out(b) | (0x80 >>> (x % 8))).toByte
        }
        x += 1
      }
      y += 1
    }
    out
  }

  /** A point in the desktop's physical pixels (the shell's), in JavaFX's logical ones: on the monitor that holds it
    * (`monitors`, each one's left, top, right and bottom in physical pixels), at the JavaFX screen that is that
    * monitor, by its scale. Monitors and screens are paired in order (by left, then top), JavaFX keeping them in the
    * order they are in, where there are as many of each; where not, a screen is taken to start at its logical place
    * times its scale (as a primary screen does). A point on none is divided by the first screen's scale. */
  def logical(x: Int, y: Int, monitors: List[(Int, Int, Int, Int)], screens: List[TrayScreen]): (Int, Int) = {
    def on(left: Double, top: Double, s: TrayScreen): (Int, Int) =
      (math.floor(s.minX + (x - left) / s.scale).toInt, math.floor(s.minY + (y - top) / s.scale).toInt)
    val paired = if (monitors.size != screens.size) Nil
      else monitors.sortBy(m => (m._1, m._2)).zip(screens.sortBy(s => (s.minX, s.minY)))
    paired.find { case ((l, t, r, b), _) => x >= l && x < r && y >= t && y < b }
      .map { case ((l, t, _, _), s) => on(l, t, s) }
      .orElse(screens.find(s => s.holds(x / s.scale, y / s.scale)).map(s => on(s.minX * s.scale, s.minY * s.scale, s)))
      .getOrElse(screens.headOption.map(s => on(0.0, 0.0, s)).getOrElse((x, y)))
  }

  // --- the calls -------------------------------------------------------------------------------------------------

  private lazy val linker: Linker = Linker.nativeLinker()
  private lazy val user32: SymbolLookup = SymbolLookup.libraryLookup("user32", Arena.global())
  private lazy val gdi32: SymbolLookup = SymbolLookup.libraryLookup("gdi32", Arena.global())
  private lazy val shell32: SymbolLookup = SymbolLookup.libraryLookup("shell32", Arena.global())
  private lazy val kernel32: SymbolLookup = SymbolLookup.libraryLookup("kernel32", Arena.global())

  private val P = ADDRESS
  private val I = JAVA_INT
  private val L = JAVA_LONG

  /** The window procedure's shape: (HWND, UINT, WPARAM, LPARAM) to LRESULT. */
  val WndProc: FunctionDescriptor = FunctionDescriptor.of(L, P, I, L, L)
  /** EnumDisplayMonitors' callback: (HMONITOR, HDC, LPRECT, LPARAM) to BOOL. */
  val MonitorProc: FunctionDescriptor = FunctionDescriptor.of(I, P, P, P, L)

  /** Every function called, by name, with its shape; a native image's metadata lists each shape (WinTraySpec). */
  val downcalls: Map[String, FunctionDescriptor] = Map(
    "Shell_NotifyIconW" -> FunctionDescriptor.of(I, I, P),
    "GetModuleHandleW" -> FunctionDescriptor.of(P, P),
    "RegisterClassExW" -> FunctionDescriptor.of(JAVA_CHAR, P),
    "CreateWindowExW" -> FunctionDescriptor.of(P, I, P, P, I, I, I, I, I, P, P, P, P),
    "DestroyWindow" -> FunctionDescriptor.of(I, P),
    "DefWindowProcW" -> FunctionDescriptor.of(L, P, I, L, L),
    "GetMessageW" -> FunctionDescriptor.of(I, P, P, I, I),
    "TranslateMessage" -> FunctionDescriptor.of(I, P),
    "DispatchMessageW" -> FunctionDescriptor.of(L, P),
    "PostMessageW" -> FunctionDescriptor.of(I, P, I, L, L),
    "PostQuitMessage" -> FunctionDescriptor.ofVoid(I),
    "RegisterWindowMessageW" -> FunctionDescriptor.of(I, P),
    "ChangeWindowMessageFilterEx" -> FunctionDescriptor.of(I, P, I, I, P),
    "SetThreadDpiAwarenessContext" -> FunctionDescriptor.of(P, P),
    "GetDpiForWindow" -> FunctionDescriptor.of(I, P),
    "GetSystemMetricsForDpi" -> FunctionDescriptor.of(I, I, I),
    "GetSystemMetrics" -> FunctionDescriptor.of(I, I),
    "EnumDisplayMonitors" -> FunctionDescriptor.of(I, P, P, P, L),
    "CreatePopupMenu" -> FunctionDescriptor.of(P),
    "AppendMenuW" -> FunctionDescriptor.of(I, P, I, L, P),
    "TrackPopupMenu" -> FunctionDescriptor.of(I, P, I, I, I, I, P, P),
    "DestroyMenu" -> FunctionDescriptor.of(I, P),
    "SetForegroundWindow" -> FunctionDescriptor.of(I, P),
    "CreateIconIndirect" -> FunctionDescriptor.of(P, P),
    "DestroyIcon" -> FunctionDescriptor.of(I, P),
    "LoadIconW" -> FunctionDescriptor.of(P, P, P),
    "CreateDIBSection" -> FunctionDescriptor.of(P, P, P, I, P, P, I),
    "CreateBitmap" -> FunctionDescriptor.of(P, I, I, I, I, P),
    "DeleteObject" -> FunctionDescriptor.of(I, P))

  /** Every callback's shape. */
  val upcalls: List[FunctionDescriptor] = List(WndProc, MonitorProc)

  // `name` from `lib`, where it has it (Windows 10's DPI calls are not on older ones)
  private def find(lib: SymbolLookup, name: String): Option[MethodHandle] = {
    val f = lib.find(name)
    if (f.isPresent) Some(linker.downcallHandle(f.get, downcalls(name))) else None
  }
  private def fn(lib: SymbolLookup, name: String): MethodHandle =
    find(lib, name).getOrElse(throw new UnsatisfiedLinkError(name))

  lazy val Shell_NotifyIconW: MethodHandle = fn(shell32, "Shell_NotifyIconW")
  lazy val GetModuleHandleW: MethodHandle = fn(kernel32, "GetModuleHandleW")
  lazy val RegisterClassExW: MethodHandle = fn(user32, "RegisterClassExW")
  lazy val CreateWindowExW: MethodHandle = fn(user32, "CreateWindowExW")
  lazy val DestroyWindow: MethodHandle = fn(user32, "DestroyWindow")
  lazy val DefWindowProcW: MethodHandle = fn(user32, "DefWindowProcW")
  lazy val GetMessageW: MethodHandle = fn(user32, "GetMessageW")
  lazy val TranslateMessage: MethodHandle = fn(user32, "TranslateMessage")
  lazy val DispatchMessageW: MethodHandle = fn(user32, "DispatchMessageW")
  lazy val PostMessageW: MethodHandle = fn(user32, "PostMessageW")
  lazy val PostQuitMessage: MethodHandle = fn(user32, "PostQuitMessage")
  lazy val RegisterWindowMessageW: MethodHandle = fn(user32, "RegisterWindowMessageW")
  lazy val ChangeWindowMessageFilterEx: Option[MethodHandle] = find(user32, "ChangeWindowMessageFilterEx")
  lazy val SetThreadDpiAwarenessContext: Option[MethodHandle] = find(user32, "SetThreadDpiAwarenessContext")
  lazy val GetDpiForWindow: Option[MethodHandle] = find(user32, "GetDpiForWindow")
  lazy val GetSystemMetricsForDpi: Option[MethodHandle] = find(user32, "GetSystemMetricsForDpi")
  lazy val GetSystemMetrics: MethodHandle = fn(user32, "GetSystemMetrics")
  lazy val EnumDisplayMonitors: MethodHandle = fn(user32, "EnumDisplayMonitors")
  lazy val CreatePopupMenu: MethodHandle = fn(user32, "CreatePopupMenu")
  lazy val AppendMenuW: MethodHandle = fn(user32, "AppendMenuW")
  lazy val TrackPopupMenu: MethodHandle = fn(user32, "TrackPopupMenu")
  lazy val DestroyMenu: MethodHandle = fn(user32, "DestroyMenu")
  lazy val SetForegroundWindow: MethodHandle = fn(user32, "SetForegroundWindow")
  lazy val CreateIconIndirect: MethodHandle = fn(user32, "CreateIconIndirect")
  lazy val DestroyIcon: MethodHandle = fn(user32, "DestroyIcon")
  lazy val LoadIconW: MethodHandle = fn(user32, "LoadIconW")
  lazy val CreateDIBSection: MethodHandle = fn(gdi32, "CreateDIBSection")
  lazy val CreateBitmap: MethodHandle = fn(gdi32, "CreateBitmap")
  lazy val DeleteObject: MethodHandle = fn(gdi32, "DeleteObject")

  // --- helpers over them ---------------------------------------------------------------------------------------

  /** `s` as a NUL-ended UTF-16 string in `arena`. */
  def wide(arena: Arena, s: String): MemorySegment = {
    val out = arena.allocate((s.length + 1) * 2L, 2L)
    var i = 0
    while (i < s.length) {
      out.set(JAVA_CHAR, i * 2L, s.charAt(i))
      i += 1
    }
    out
  }

  /** `s` written into the WCHAR array of `max` at `offset` of `to`, cut to fit (`fit`), its NUL after it. */
  def put(to: MemorySegment, offset: Long, s: String, max: Int): Unit = {
    val f = fit(s, max)
    var i = 0
    while (i < f.length) {
      to.set(JAVA_CHAR, offset + i * 2L, f.charAt(i))
      i += 1
    }
    to.set(JAVA_CHAR, offset + f.length * 2L, 0.toChar)
  }

  /** A NOTIFYICONDATAW in `arena` for icon 1 of `hwnd`, its callback `WM_TRAY`, saying `flags`. */
  def iconData(arena: Arena, hwnd: MemorySegment, flags: Int): MemorySegment = {
    val d = arena.allocate(NotifyIconData)
    d.set(JAVA_INT, at("cbSize"), NotifyIconData.byteSize.toInt)
    d.set(ADDRESS, at("hWnd"), hwnd)
    d.set(JAVA_INT, at("uID"), 1)
    d.set(JAVA_INT, at("uFlags"), flags)
    d.set(JAVA_INT, at("uCallbackMessage"), WM_TRAY)
    d
  }

  /** Shell_NotifyIconW(`op`, `data`): whether the shell took it. */
  def notifyIcon(op: Int, data: MemorySegment): Boolean = (Shell_NotifyIconW.invokeExact(op, data): Int) != 0

  /** Icon 1 of `hwnd` put in the notification area (again, after Explorer restarts), showing `icon` and saying `tip`,
    * then told to speak NOTIFYICON_VERSION_4. */
  def addIcon(hwnd: MemorySegment, icon: MemorySegment, tip: String): Boolean = {
    val arena = Arena.ofConfined()
    try {
      val d = iconData(arena, hwnd, NIF_MESSAGE | NIF_ICON | NIF_TIP | NIF_SHOWTIP)
      d.set(ADDRESS, at("hIcon"), icon)
      put(d, at("szTip"), tip, 128)
      val added = notifyIcon(NIM_ADD, d)
      d.set(JAVA_INT, at("uVersion"), NOTIFYICON_VERSION_4)
      added && notifyIcon(NIM_SETVERSION, d)
    } finally arena.close()
  }

  /** A notification from icon 1 of `hwnd`: `title` over `body`, with `kind`'s picture (NIIF_USER: `picture`). */
  def balloon(hwnd: MemorySegment, title: String, body: String, kind: Int, picture: MemorySegment): Boolean = {
    val arena = Arena.ofConfined()
    try {
      val d = iconData(arena, hwnd, NIF_INFO)
      put(d, at("szInfoTitle"), title, 64)
      put(d, at("szInfo"), body, 256)
      d.set(JAVA_INT, at("dwInfoFlags"), kind)
      d.set(ADDRESS, at("hBalloonIcon"), picture)
      notifyIcon(NIM_MODIFY, d)
    } finally arena.close()
  }

  /** Icon 1 of `hwnd` out of the notification area. */
  def deleteIcon(hwnd: MemorySegment): Boolean = {
    val arena = Arena.ofConfined()
    try notifyIcon(NIM_DELETE, iconData(arena, hwnd, 0)) finally arena.close()
  }

  /** The size a notification-area icon is drawn at for `hwnd`, by its monitor's DPI where Windows 10 says it (16 at
    * 100%, 20 at 125%, 24 at 150%, 32 at 200%), else by the system's. */
  def smallIcon(hwnd: MemorySegment): Int = {
    val byDpi = for {
      dpiOf <- GetDpiForWindow
      metric <- GetSystemMetricsForDpi
      dpi = (dpiOf.invokeExact(hwnd): Int)
      if dpi > 0
    } yield (metric.invokeExact(SM_CXSMICON, dpi): Int)
    val px = byDpi.getOrElse((GetSystemMetrics.invokeExact(SM_CXSMICON): Int))
    if (px > 0) px else 16
  }

  /** An HICON of `image`, `px` across: a 32-bit top-down DIB section of its pixels and a mask of where it is clear,
    * through CreateIconIndirect (the two bitmaps let go of after, the icon having its own copies). NULL where it
    * cannot be made; DestroyIcon it when done. */
  def makeIcon(image: TrayImage, px: Int): MemorySegment = {
    val sized = image.sized(px)
    val (w, h) = (sized.width, sized.height)
    val arena = Arena.ofConfined()
    try {
      val header = arena.allocate(40L, 4L)   // BITMAPINFOHEADER
      header.set(JAVA_INT, 0L, 40)
      header.set(JAVA_INT, 4L, w)
      header.set(JAVA_INT, 8L, -h)   // top-down
      header.set(JAVA_CHAR, 12L, 1.toChar)   // planes
      header.set(JAVA_CHAR, 14L, 32.toChar)   // bits a pixel; BI_RGB, and the rest, are 0
      val bits = arena.allocate(ADDRESS)
      val colour = (CreateDIBSection.invokeExact(MemorySegment.NULL, header, 0, bits, MemorySegment.NULL, 0): MemorySegment)
      val maskBits = arena.allocate(((w + 15) / 16 * 2 * h).toLong, 2L)
      MemorySegment.copy(mask(sized), 0, maskBits, JAVA_BYTE, 0L, maskBits.byteSize.toInt)
      val hbmMask = (CreateBitmap.invokeExact(w, h, 1, 1, maskBits): MemorySegment)
      try {
        if (colour.address == 0L || hbmMask.address == 0L) MemorySegment.NULL
        else {
          val pixels = bits.get(ADDRESS, 0L).reinterpret(w.toLong * h * 4L)
          MemorySegment.copy(bgra(sized), 0, pixels, JAVA_BYTE, 0L, w * h * 4)
          val info = arena.allocate(32L, 8L)   // ICONINFO
          info.set(JAVA_INT, 0L, 1)   // an icon, not a cursor
          info.set(ADDRESS, 16L, hbmMask)
          info.set(ADDRESS, 24L, colour)
          (CreateIconIndirect.invokeExact(info): MemorySegment)
        }
      } finally {
        if (colour.address != 0L) (DeleteObject.invokeExact(colour): Int)
        if (hbmMask.address != 0L) (DeleteObject.invokeExact(hbmMask): Int)
      }
    } finally arena.close()
  }

  /** `icon` let go of, where there is one. */
  def destroyIcon(icon: MemorySegment): Unit = if (icon.address != 0L) (DestroyIcon.invokeExact(icon): Int)

  /** `msg` posted to `hwnd`'s queue. */
  def post(hwnd: MemorySegment, msg: Int, wParam: Long = 0L, lParam: Long = 0L): Boolean =
    (PostMessageW.invokeExact(hwnd, msg, wParam, lParam): Int) != 0
}
