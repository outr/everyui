package everyui.tray

import java.lang.foreign.MemorySegment
import java.lang.invoke.{MethodHandles, MethodType}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Notifications on macOS, through the user notification centre (UserNotifications), which serves only an app run
  * from a .app bundle with an identifier: run any other way (a plain `java`, a native image's bare executable),
  * `notify` does nothing. The first asks the user whether the app may show them; each is shown at once, its actions
  * as buttons (a category of its own, the last 16 kept), shown too while the app is in front. Where one is clicked, or
  * one of its buttons, what that does runs on the app's thread. */
private[tray] object MacNotifier {
  import ObjC.*

  private val DefaultAction = "com.apple.UNNotificationDefaultActionIdentifier"
  // UNAuthorizationOptionSound | Alert; UNNotificationPresentationOptionSound | List | Banner
  private val Asked = 2L | 4L
  private val Present = 2L | 8L | 16L
  private val Kept = 16

  private val numbered = new AtomicInteger(0)
  // what each notification's actions do, by its identifier, and the key of each ("default" for a click on it)
  private val waiting = new ConcurrentHashMap[String, Map[String, () => Unit]]()
  // on the main thread only: the centre, and the categories registered with it, newest first
  private var center: MemorySegment = NULL
  private var categories: List[MemorySegment] = Nil

  /** Whether the app runs from a .app bundle with an identifier, as the notification centre needs. */
  lazy val bundled: Boolean = try pooled {
    val b = id(cls("NSBundle"), "mainBundle")
    !isNil(b) && !isNil(id(b, "bundleIdentifier")) && text(id(b, "bundlePath")).endsWith(".app")
  } catch { case _: Throwable => false }

  def notify(title: String, body: String, actions: List[(String, () => Unit)], clicked: Option[() => Unit]): Unit =
    if (bundled) ObjC.main(post(title, body, actions, clicked))

  private def post(title: String, body: String, actions: List[(String, () => Unit)], clicked: Option[() => Unit]): Unit = {
    if (isNil(center)) start()
    val key = "everyui-" + numbered.incrementAndGet()
    val content = make("UNMutableNotificationContent")
    id(content, "setTitle:", str(title))
    id(content, "setBody:", str(body))
    if (actions.nonEmpty) {
      val buttons = actions.zipWithIndex.map { case ((label, _), i) =>
        send(SendPPJ, cls("UNNotificationAction"), "actionWithIdentifier:title:options:", str("a" + i), str(label), 0L)
          .asInstanceOf[MemorySegment]
      }
      val category = retain(send(SendPPPJ, cls("UNNotificationCategory"),
        "categoryWithIdentifier:actions:intentIdentifiers:options:", str(key), array(buttons*), array(), 0L)
        .asInstanceOf[MemorySegment])
      categories.drop(Kept - 1).foreach(release)
      categories = category :: categories.take(Kept - 1)
      id(center, "setNotificationCategories:", id(cls("NSSet"), "setWithArray:", array(categories*)))
      id(content, "setCategoryIdentifier:", str(key))
    }
    val runs = actions.zipWithIndex.map { case ((_, run), i) => ("a" + i) -> run }.toMap ++
      clicked.map(c => "default" -> c)
    if (runs.nonEmpty) waiting.put(key, runs)
    waiting.remove("everyui-" + (numbered.get - 64))
    val request = id(cls("UNNotificationRequest"), "requestWithIdentifier:content:trigger:", str(key), content, NULL)
    id(center, "addNotificationRequest:withCompletionHandler:", request, NULL)
    release(content)
  }

  // the centre, told of this app's delegate, and the user asked whether it may show notifications
  private def start(): Unit = {
    load("UserNotifications")
    center = retain(id(cls("UNUserNotificationCenter"), "currentNotificationCenter"))
    id(center, "setDelegate:", delegate)
    send(SendJP, center, "requestAuthorizationWithOptions:completionHandler:", Asked, granted)
  }

  /** The delegate's two methods, on the main thread: a notification about to show while the app is in front (shown),
    * and one answered (what it does run). Each calls the completion handler it is given. */
  def answer(self: MemorySegment, cmd: MemorySegment, centre: MemorySegment, about: MemorySegment,
             handler: MemorySegment): Unit =
    try {
      if (cmd.address == sel(WillPresent).address) callBack(handler, Present)
      else {
        try {
          val action = text(id(about, "actionIdentifier"))
          val key = text(id(id(id(about, "notification"), "request"), "identifier"))
          val runs = Option(waiting.remove(key)).getOrElse(Map.empty)
          runs.get(if (action == DefaultAction) "default" else action)
            .foreach(run => javafx.application.Platform.runLater(() => run()))
        }
        finally callBack(handler)
      }
    }
    catch { case t: Throwable => everyui.scribe.warn("tray: notification: " + t) }

  /** Whether the user lets the app show notifications, as the centre says once asked. */
  def allowed(block: MemorySegment, yes: Boolean, error: MemorySegment): Unit =
    if (!yes) everyui.scribe.info("tray: notifications are not allowed for this app")

  private val WillPresent = "userNotificationCenter:willPresentNotification:withCompletionHandler:"
  private val Received = "userNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:"

  private lazy val delegate: MemorySegment = {
    val m = MethodType.methodType(Void.TYPE, classOf[MemorySegment], classOf[MemorySegment], classOf[MemorySegment],
      classOf[MemorySegment], classOf[MemorySegment])
    val imp = upcall(MethodHandles.lookup().findVirtual(getClass, "answer", m).bindTo(this), Method3)
    val c = subclass("EveryUINotificationDelegate", List(WillPresent, Received).map(s => (s, "v@:@@@?", imp)),
      List("UNUserNotificationCenterDelegate"))
    id(id(c, "alloc"), "init")
  }

  private lazy val granted: MemorySegment = block(upcall(MethodHandles.lookup().findVirtual(getClass, "allowed",
    MethodType.methodType(Void.TYPE, classOf[MemorySegment], java.lang.Boolean.TYPE, classOf[MemorySegment]))
    .bindTo(this), Granted))
}
