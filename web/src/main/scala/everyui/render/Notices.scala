package everyui.render

import everyui.Permission
import everyui.push.Notice
import org.scalajs.dom
import reactify.*

import scala.collection.mutable
import scala.scalajs.js

/** Notifications the page shows itself (`Platform.showNotice`), through the Notifications API: by the page's service
  * worker where it has one (the one `everyui.sbt.WebApp.write` installs), so one still shows and is still answered
  * while the tab is in the background, else as a `Notification` of the page's own. A click on one shown by the worker
  * reaches the worker, which focuses the window that showed it (or opens the app when none is open) and hands it the
  * click: `{everyui: "notice", key}`. The worker tells the page which window it is (`{everyui: "client"}`, answered
  * with its id), so a click goes back to the page that showed it rather than to any of the app's windows. */
object Notices {
  private def window: js.Dynamic = dom.window.asInstanceOf[js.Dynamic]
  private def workers: js.Dynamic = dom.window.navigator.asInstanceOf[js.Dynamic].serviceWorker
  private def hasWorkers: Boolean = !js.isUndefined(workers)

  def supported(): Boolean = js.typeOf(window.Notification) == "function"

  /** The browser's answer for the page, followed: the Permissions API's change where the browser has it, and read again
    * whenever the page comes back into sight, for one that does not say. */
  lazy val permission: Var[Permission] = {
    val state = Var[Permission](read())
    if (supported()) {
      val permissions = dom.window.navigator.asInstanceOf[js.Dynamic].permissions
      if (!js.isUndefined(permissions)) permissions.query(js.Dynamic.literal(name = "notifications"))
        .asInstanceOf[js.Promise[js.Dynamic]]
        .`then`[Unit]((status: js.Dynamic) => status.onchange = (_: js.Any) => state @= read())
        .`catch`[Unit]((_: Any) => ())
      dom.document.addEventListener("visibilitychange", (_: dom.Event) => state @= read())
      dom.window.addEventListener("focus", (_: dom.Event) => state @= read())
      listen()
    }
    state
  }

  private def read(): Permission =
    if (!supported()) Permission.Refused
    else String.valueOf(window.Notification.permission) match {
      case "granted" => Permission.Granted
      case "denied" => Permission.Refused
      case _ => Permission.NotAsked
    }

  /** The browser's prompt: only from a click or a key, or the browser refuses without asking (and may stop asking). */
  def ask(): Unit = if (supported()) {
    val answered = () => permission @= read()
    window.Notification.requestPermission().asInstanceOf[js.Promise[String]]
      .`then`[Unit]((_: String) => answered())
      .`catch`[Unit]((_: Any) => answered())
  }

  /** What a click on each notification shown does, by its key (its tag, or a number for one without), the latest kept. */
  private val clicks = mutable.LinkedHashMap.empty[String, () => Unit]
  private var shown = 0
  private var client = ""
  private var listening = false

  def show(notice: Notice, onClick: () => Unit): Unit =
    if (supported() && read() == Permission.Granted && !(notice.onlyWhenHidden && inSight())) {
      listen()
      shown += 1
      val key = if (notice.tag.nonEmpty) "tag:" + notice.tag else "n" + shown
      clicks.remove(key)
      clicks(key) = onClick
      while (clicks.size > 100) clicks.remove(clicks.head._1)
      val options = js.Dynamic.literal(body = notice.body,
        data = js.Dynamic.literal(everyui = "notice", key = key, client = client))
      if (notice.tag.nonEmpty) {
        options.tag = notice.tag
        options.renotify = true
      }
      if (notice.icon.nonEmpty) options.icon = notice.icon
      registration {
        case Some(r) => r.showNotification(notice.title, options).asInstanceOf[js.Promise[Any]]
          .`catch`[Any]((e: Any) => Platform.log("notification not shown: " + WebErrors.message(e)))
        case None => try {
          val shownHere = js.Dynamic.newInstance(window.Notification)(notice.title, options)
          shownHere.onclick = (_: js.Any) => {
            window.focus()
            shownHere.close()
            clicked(key)
          }
        } catch { case e: Throwable => Platform.log("notification not shown: " + e.getMessage) }
      }
    }

  /** The page in sight and its window with the focus: the person is looking at it. */
  private def inSight(): Boolean = dom.document.visibilityState == "visible" && dom.document.hasFocus()

  private def clicked(key: String): Unit = clicks.remove(key).foreach(f => f())

  /** The worker's messages to the page, heard from the first notification: which window this is, and a click. */
  private def listen(): Unit = if (!listening && hasWorkers) {
    listening = true
    workers.addEventListener("message", (e: js.Dynamic) => {
      val data = e.data
      if (data != null && !js.isUndefined(data) && !js.isUndefined(data.everyui)) {
        val kind = String.valueOf(data.everyui)
        if (kind == "client") client = String.valueOf(data.id)
        else if (kind == "notice") clicked(String.valueOf(data.key))
      }
    })
    workers.addEventListener("controllerchange", (_: js.Any) => whoAmI())
    whoAmI()
  }

  private def whoAmI(): Unit = {
    val controller = workers.controller
    if (controller != null && !js.isUndefined(controller)) controller.postMessage(js.Dynamic.literal(everyui = "client"))
  }

  /** The page's service worker registration once it is running, or None where the page registered none. */
  private def registration(found: Option[js.Dynamic] => Unit): Unit =
    if (!hasWorkers) found(None)
    else workers.getRegistration().asInstanceOf[js.Promise[js.UndefOr[js.Dynamic]]]
      .`then`[Unit] { (r: js.UndefOr[js.Dynamic]) =>
        if (r.isEmpty) found(None)
        else workers.ready.asInstanceOf[js.Promise[js.Dynamic]].`then`[Unit]((ready: js.Dynamic) => found(Some(ready)))
      }
      .`catch`[Unit]((_: Any) => found(None))
}
