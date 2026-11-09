package everyui.app

import everyui.{PlatformKind, SettingsPage, SystemSettings}
import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.background.{Work, WorkConstraints, WorkNetwork}
import everyui.component.{Button, ButtonStyle, Column, Component, Container, ScrollContainer, TextView}
import everyui.layout.FlowLayout
import everyui.net.*
import everyui.push.{Ongoing, Push, PushButton}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** The background demo's screen (BackgroundDemo), at `/background`: work scheduled once in five seconds with a
  * network, once at once (expedited) and every 15 minutes, and cancelled; the status notice shown and cleared (its
  * Pause, Resume and Open run in ExampleBackground); each settings page opened, and whether unused-app restrictions
  * are on; a reminder and an approval pushed in three seconds (Push.deliver), whose Done, Approve and Deny run in
  * ExampleBackground with the app closed and are heard by Push.acted with it running. Under each, what
  * ExampleBackground has noted, read again every two seconds and as the app comes back. */
class BackgroundScreen extends UIScreen {
  override def manager: ScreenManager = BackgroundDemo
  override def title: String = "In the background"
  override def path: URLPath = path"/background"

  private val width = math.min(720.0, ui.size.width() - 32.0)
  private val worked: Var[String] = Var("")
  private val noticed: Var[String] = Var("")
  private val settled: Var[String] = Var("")
  private val restarted: Var[String] = Var("")
  private val pushed: Var[String] = Var("")
  private var answered = ""

  private val once = new Button("Once in 5 s, with a network", () => {
    Work.once("example.once", WorkConstraints(network = WorkNetwork.Connected), delay = 5.0,
      data = Map("asked" -> now()))
    read()
  }, ButtonStyle.Filled)

  override def defaultFocus(): Component = once

  override def createUI(): Task[Unit] = Task {
    val column = Column(20.0, padding = 16.0)(
      TextView("In the background", 22.0),
      TextView((if (Work.inBackground) "Work runs with the app closed here"
        else "Work runs while the app is open here") + " · " + Platform.kind.toString, 12.0, Theme.muted()),
      section("Scheduled work", worked)(
        once,
        new Button("Now, expedited", () => {
          Work.once("example.now", expedited = true, data = Map("asked" -> now()))
          read()
        }),
        new Button("Every 15 minutes", () => {
          Work.every("example.every", 15, data = Map("asked" -> now()))
          read()
        }),
        new Button("Cancel all", () => {
          ExampleBackground.workKeys.foreach(Work.cancel)
          read()
        })),
      section("The status notice", noticed)(
        new Button("Allow notices", () => Platform.askNotificationPermission()),
        new Button("Show it", () => {
          ExampleBackground.show(paused = false)
          read()
        }, ButtonStyle.Filled),
        new Button("Clear it", () => {
          ExampleBackground.clear()
          read()
        })),
      section("Settings", settled)(
        page("The app", SettingsPage.App),
        page("Notifications", SettingsPage.AppNotifications),
        page("Location", SettingsPage.AppLocation),
        page("Unused app restrictions", SettingsPage.UnusedAppRestrictions),
        page("Device location", SettingsPage.DeviceLocation)),
      section("Push notifications with buttons, in 3 s", pushed)(
        new Button("A reminder", () => push(reminder)),
        new Button("An approval", () => push(approval))),
      section("Restarts", restarted)())
    val scroll = new ScrollContainer {
      size.width := ui.size.width()
      size.height := ui.size.height()
    }
    scroll.content.children += column
    container.children += scroll
    Platform.onResume(() => read())
    tick()
  }

  override protected def activate(): Task[Unit] = super.activate().map { _ =>
    Platform.log("example background: at " + Platform.path())
    read()
  }

  private def tick(): Unit = Platform.after(2.0, () => {
    read()
    tick()
  })

  private def page(label: String, page: SettingsPage): Button = new Button(label, () =>
    SystemSettings.open(page).map { opened =>
      answered = page.toString + (if (opened) " opened, and back" else " not opened")
      Platform.log("example background: settings " + answered)
      read()
    }.start())

  /** What ExampleBackground has noted, and what is scheduled, said again. */
  private def read(): Unit = {
    worked @= ExampleBackground.workKeys.map { k =>
      val ran = Platform.setting("example.bg.work." + k)
      k + ": " + (if (Work.pending(k)) "scheduled" else "not scheduled") + (if (ran.isEmpty) "" else "; " + ran)
    }.mkString("\n")
    val state = Platform.setting("example.bg.ongoing")
    val action = Platform.setting("example.bg.action")
    noticed @= (if (Ongoing.supported) "" else "No ongoing notices here. ") +
      (if (state.isEmpty) "Not shown" else "Shown, " + state) + (if (action.isEmpty) "" else "; last action " + action)
    SystemSettings.unusedAppRestrictions.map { on =>
      settled @= "Unused app restrictions " + (if (on) "on" else "off") +
        (if (answered.isEmpty) "" else "; " + answered)
    }.start()
    val restart = Platform.setting("example.bg.restart")
    restarted @= (if (restart.isEmpty) "None heard yet" else "Last: " + restart)
    val chosen = Platform.setting("example.bg.push")
    pushed @= (if (Platform.kind == PlatformKind.Android) "" else "Delivered only on Android. ") +
      (if (chosen.isEmpty) "No button chosen yet" else "Last: " + chosen)
  }

  /** A task's reminder: Done ticks it off with the app closed, Open opens it. */
  private def reminder: Map[String, String] = Map("title" -> "Take pills", "body" -> "8:00", "path" -> "/background",
    "tag" -> "reminder-1", "task" -> "pills", "actions" -> PushButton.json(Vector(
      PushButton("done", "Done", background = true), PushButton("open", "Open", path = Some("/background/opened")))))

  /** An agent's request: Approve and Deny with the app closed, Look opens it. */
  private def approval: Map[String, String] = Map("title" -> "Approve the deploy?", "body" -> "An agent asks to deploy",
    "path" -> "/background", "tag" -> "approval-7", "request" -> "7", "actions" -> PushButton.json(Vector(
      PushButton("approve", "Approve", background = true), PushButton("deny", "Deny", background = true),
      PushButton("look", "Look", path = Some("/background/opened")))))

  /** A push delivered in three seconds as the push service would (Push.deliver), time to leave the app first. */
  private def push(data: Map[String, String]): Unit = {
    Platform.log("example background: a push in 3 s: " + data.getOrElse("title", ""))
    Platform.after(3.0, () => Push.deliver(data))
  }

  private def now(): String = Platform.localDateTime(System.currentTimeMillis())

  /** A section: its title, its buttons (wrapping on a narrow screen) where it has any, and what it last said. */
  private def section(title: String, answer: Var[String])(buttons: Component*): Container = {
    val row = new Container {
      layout @= Some(FlowLayout(10.0, 10.0))
      size.width @= width
      children ++= buttons
    }
    val rows = if (buttons.isEmpty) Nil else List(row)
    Column(8.0)((TextView(title, 16.0) :: rows ::: List(TextView.paragraph(answer(), width, 13.0, Theme.muted())))*)
  }
}
