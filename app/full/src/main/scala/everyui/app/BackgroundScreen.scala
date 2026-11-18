package everyui.app

import everyui.{PlatformKind, SettingsPage, SystemSettings}
import everyui.app.screen.{ScreenManager, UIScreen}
import everyui.async.Task
import everyui.background.{Work, WorkConstraints, WorkNetwork}
import everyui.component.{Button, ButtonStyle, Column, Component, Container, ScrollContainer, TextView}
import everyui.layout.FlowLayout
import everyui.net.*
import everyui.push.{NoticeImportance, Ongoing, Push, PushButton, PushChannel}
import everyui.render.Platform
import everyui.theme.Theme
import everyui.ui
import reactify.*

/** The background demo's screen (BackgroundDemo), at `/background`: work scheduled once in five seconds with a
  * network, once at once (expedited) and every 15 minutes, and cancelled; the status notice shown and cleared (its
  * Pause, Resume and Open run in ExampleBackground); each settings page opened, and whether unused-app restrictions
  * are on; a reminder and an approval pushed in three seconds (Push.deliver), whose Done, Approve and Deny run in
  * ExampleBackground with the app closed (Approve once the device is unlocked) and are heard by Push.acted with it
  * running; an agent's answer in Markdown, styled; and mail for three accounts (Push.deliver), each message stacked
  * under its account's summary on its account's channel, with a Reply typed into it; three channels declared up
  * front (Push.channels) at low, default and high importance, three more made by pushes asking each
  * (channelImportance), and the loud one's settings opened (Push.openChannelSettings). Under each, what
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
  private val channeled: Var[String] = Var("")
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
        new Button("An approval", () => push(approval)),
        new Button("Mail for three accounts", () => mail()),
        new Button("An answer in Markdown", () => push(answer))),
      section("Channels and their importance", channeled)(
        new Button("Declare quiet, normal and loud", () => declare()),
        new Button("Low, default and high pushes", () => importances()),
        new Button("Loud's settings", () => Push.openChannelSettings("example.loud").map { opened =>
          channeled @= "Loud's settings " + (if (opened) "opened, and back" else "not opened")
        }.start())),
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

  /** An agent's request: Approve and Deny with the app closed, Approve only once the device is unlocked (before
    * Android 12 it opens the app instead), Look opens it. */
  private def approval: Map[String, String] = Map("title" -> "Approve the deploy?", "body" -> "An agent asks to deploy",
    "path" -> "/background", "tag" -> "approval-7", "request" -> "7", "actions" -> PushButton.json(Vector(
      PushButton("approve", "Approve", background = true, requiresUnlock = true),
      PushButton("deny", "Deny", background = true),
      PushButton("look", "Look", path = Some("/background/opened")))))

  /** An agent's answer written in Markdown ("format": "markdown"): styled on Android, its title plain words. */
  private def answer: Map[String, String] = Map("title" -> "**Agent** finished `deploy`", "path" -> "/background",
    "tag" -> "answer-3", "format" -> "markdown", "body" -> ("I ran **3 checks** on *staging*:\n\n1. `compile` passed\n" +
      "2. `test` had ~~2~~ ***0 failures***\n3. lint: see [the report](https://ci.example.com/r/9)\n\n- [x] migrations\n" +
      "- [ ] smoke test\n\n> Next: watch `api-7`."))

  /** Mail for three accounts as a mail server pushes it, from three seconds on, half a second apart: each message on
    * its account's channel (under "Mail" in the settings) and in its account's group, with Reply (typed into it, its
    * "after" saying what ExampleBackground makes of the notification then) and Archive, followed by the group's summary
    * listing its messages, newest first, which makes no sound. The third account has one message. */
  private def mail(): Unit = {
    val accounts = Vector(("ann@example.com", "a", Vector("Lunch on Friday?", "The draft, attached", "Re: tickets")),
      ("work@example.com", "w", Vector("Standup moved to 10", "Build 42 failed", "Review requested")),
      ("solo@example.com", "s", Vector("Just the one")))
    val pushes = accounts.flatMap { (account, key, subjects) =>
      val channel = Map("group" -> account, "channel" -> ("mail." + key), "channelName" -> ("Mail: " + account),
        "channelGroup" -> "mail", "channelGroupName" -> "Mail", "path" -> "/background")
      subjects.indices.toVector.flatMap { i =>
        val sender = "Sender " + (i + 1).toString
        val message = channel ++ Map("title" -> sender, "body" -> subjects(i), "tag" -> (key + "-" + i.toString),
          "after" -> (if (key == "w") "close" else if (i == 0) "update" else "show"),
          "actions" -> PushButton.json(Vector(PushButton("reply", "Reply", input = true, hint = "Reply to " + sender),
            PushButton("archive", "Archive", background = true))))
        val summary = channel ++ Map("title" -> ((i + 1).toString + " new"), "body" -> account, "groupSummary" -> "true",
          "silent" -> "true", "lines" -> subjects.take(i + 1).reverse.map(s => "\"" + s + "\"").mkString("[", ",", "]"))
        Vector(message, summary)
      }
    }
    Platform.log("example background: mail in 3 s, " + pushes.size.toString + " pushes")
    pushes.zipWithIndex.foreach((p, i) => Platform.after(3.0 + i * 0.5, () => Push.deliver(p)))
  }

  /** Three channels declared before any push names them, under "Example", one at each of low, default and high. */
  private def declare(): Unit = {
    Push.channels(List(
      PushChannel("example.quiet", "Quiet", "example", "Example", NoticeImportance.Low, "Declared at low"),
      PushChannel("example.normal", "Normal", "example", "Example", NoticeImportance.Default, "Declared at default"),
      PushChannel("example.loud", "Loud", "example", "Example", NoticeImportance.High, "Declared at high")))
    channeled @= "Declared Quiet (low), Normal (default) and Loud (high)"
    Platform.log("example background: channels declared")
  }

  /** From three seconds on, a push into each of three channels under "Pushed" asking low, default and high, and then
    * one more into the high one asking low, which Android leaves high (said once, at debug). */
  private def importances(): Unit = {
    val asks = Vector("low", "default", "high", "low")
    asks.zipWithIndex.foreach { (importance, i) =>
      val into = if (i == 3) "high" else importance
      Platform.after(3.0 + i * 1.0, () => Push.deliver(Map("title" -> ("Asked " + importance),
        "body" -> ("Into the " + into + " channel"), "path" -> "/background", "tag" -> ("importance-" + i.toString),
        "channel" -> ("example.push." + into), "channelName" -> ("Pushed " + into), "channelGroup" -> "pushed",
        "channelGroupName" -> "Pushed", "channelImportance" -> importance)))
    }
    channeled @= "Pushing low, default, high, and low into high, from 3 s on"
  }

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
