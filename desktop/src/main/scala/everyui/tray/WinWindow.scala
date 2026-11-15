package everyui.tray

import java.lang.foreign.{Arena, Linker, MemorySegment}
import java.lang.foreign.ValueLayout.{ADDRESS, JAVA_INT}
import java.lang.invoke.{MethodHandles, MethodType}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, CountDownLatch, TimeUnit}

/** A hidden window on a thread of its own, for a tray icon on Windows to be the shell's: a top-level one (not a
  * message-only one, which never hears a broadcast such as Explorer's "TaskbarCreated"), made and served there by a
  * GetMessageW loop, every Win32 call for it made there too. Work from elsewhere (the app's thread) goes across with
  * `post`, queued and woken for with a `WM_WORK` message. What the window is sent goes to `answer` (its message,
  * wParam and lParam), which says what it returns, or None for Windows' own handling. `close` destroys it and ends
  * its loop. */
private[tray] class WinWindow(answer: (Int, Long, Long) => Option[Long]) {
  private val work = new ConcurrentLinkedQueue[() => Unit]()
  private val started = new CountDownLatch(1)
  @volatile private var open = false
  @volatile private var handle: MemorySegment = MemorySegment.NULL

  private val thread = new Thread(() => run(), "everyui-tray-window")
  thread.setDaemon(true)
  thread.start()
  started.await(10, TimeUnit.SECONDS)

  /** Whether the window was made, and is not closed. */
  def made: Boolean = open

  /** The window's HWND (NULL until it is made). */
  def hwnd: MemorySegment = handle

  /** `f` run on the window's thread, after what was posted before it; nothing once it is closed. */
  def post(f: () => Unit): Unit = if (open) {
    work.add(f)
    Win32.post(handle, Win32.WM_WORK)
  }

  /** `f` run on the window's thread, then the window destroyed and its loop ended. */
  def close(f: () => Unit = () => ()): Unit = post { () =>
    f()
    open = false
    (Win32.DestroyWindow.invokeExact(handle): Int)
  }

  private def run(): Unit = {
    val arena = Arena.ofConfined()
    try {
      // the shell's points, and the DPI asked for, in physical pixels on every monitor
      Win32.SetThreadDpiAwarenessContext.foreach(f =>
        (f.invokeExact(MemorySegment.ofAddress(Win32.PerMonitorAware)): MemorySegment))
      val made = WinWindow.className.map(name => (Win32.CreateWindowExW.invokeExact(Win32.WS_EX_TOOLWINDOW, name,
        name, 0, 0, 0, 0, 0, MemorySegment.NULL, MemorySegment.NULL, WinWindow.instance,
        MemorySegment.NULL): MemorySegment)).getOrElse(MemorySegment.NULL)
      if (made.address != 0L) {
        handle = made
        WinWindow.windows.put(made.address, this)
        open = true
      }
      else everyui.scribe.warn("tray: no window made for the notification area")
      started.countDown()
      if (open) {
        val msg = arena.allocate(48L, 8L)   // MSG
        while ((Win32.GetMessageW.invokeExact(msg, MemorySegment.NULL, 0, 0): Int) > 0) {
          (Win32.TranslateMessage.invokeExact(msg): Int)
          (Win32.DispatchMessageW.invokeExact(msg): Long)
        }
      }
    } catch {
      case t: Throwable => everyui.scribe.warn("tray: the window's thread failed: " + t)
    } finally {
      open = false
      WinWindow.windows.remove(handle.address)
      work.clear()
      started.countDown()
      arena.close()
    }
  }

  private[tray] def message(msg: Int, wParam: Long, lParam: Long): Option[Long] = msg match {
    case Win32.WM_WORK =>
      var f = work.poll()
      while (f != null) {
        try f() catch { case t: Throwable => everyui.scribe.warn("tray: " + t) }
        f = work.poll()
      }
      Some(0L)
    case Win32.WM_DESTROY =>
      answer(msg, wParam, lParam)
      (Win32.PostQuitMessage.invokeExact(0): Unit)
      Some(0L)
    case _ => answer(msg, wParam, lParam)
  }
}

private[tray] object WinWindow {
  private val windows = new ConcurrentHashMap[Long, WinWindow]()

  /** Every window's procedure, one upcall kept for the life of the process: the message handed to the window it is
    * for, and Windows' own handling for the rest. Nothing thrown gets out (it would take the JVM down). */
  def proc(hwnd: MemorySegment, msg: Int, wParam: Long, lParam: Long): Long = try {
    val w = windows.get(hwnd.address)
    val mine = if (w == null) None else w.message(msg, wParam, lParam)
    mine.getOrElse((Win32.DefWindowProcW.invokeExact(hwnd, msg, wParam, lParam): Long))
  } catch {
    case t: Throwable =>
      everyui.scribe.warn("tray: message " + msg + " failed: " + t)
      0L
  }

  private[tray] lazy val instance: MemorySegment =
    (Win32.GetModuleHandleW.invokeExact(MemorySegment.NULL): MemorySegment)

  // the window class, registered once, its procedure `proc`; None where it could not be
  private[tray] lazy val className: Option[MemorySegment] = {
    val name = Win32.wide(Arena.global(), "EveryUITray")
    val target = MethodHandles.lookup().findVirtual(WinWindow.getClass, "proc",
      MethodType.methodType(classOf[Long], classOf[MemorySegment], classOf[Int], classOf[Long], classOf[Long]))
      .bindTo(WinWindow)
    val stub = Linker.nativeLinker().upcallStub(target, Win32.WndProc, Arena.global())
    val arena = Arena.ofConfined()
    try {
      val wc = arena.allocate(80L, 8L)   // WNDCLASSEXW
      wc.set(JAVA_INT, 0L, 80)
      wc.set(ADDRESS, 8L, stub)
      wc.set(ADDRESS, 24L, instance)
      wc.set(ADDRESS, 64L, name)
      val atom = (Win32.RegisterClassExW.invokeExact(wc): Char)
      if (atom == 0) {
        everyui.scribe.warn("tray: the window class was not registered")
        None
      }
      else Some(name)
    } finally arena.close()
  }
}
