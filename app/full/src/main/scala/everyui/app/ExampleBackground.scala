package everyui.app

import everyui.app.screen.ScreenManager
import everyui.async.Task
import everyui.background.{BackgroundApp, RestartReason, WorkResult}
import everyui.push.{NoticeAction, NoticeChannel, NoticeTap, Ongoing, OngoingNotice}
import everyui.render.Platform

/** What the example runs with the app closed (build.sbt names this object, `everyuiBackground`; `demo=background`
  * drives it): each run of scheduled work noted with its time, whether the app was open, and how many runs there have
  * been; an ongoing notice ("status") whose Pause and Resume run here and whose Open opens `/background/opened`, shown
  * again when it is swiped away and after a restart; each restart noted with its reason. What it notes is in the
  * settings ("example.bg.*"), which the demo reads, and in the log ("[ui] example background: ..."). */
object ExampleBackground extends BackgroundApp {
  val channel: NoticeChannel = NoticeChannel("example.status", "Status", "What the example does in the background")
  val workKeys: Vector[String] = Vector("example.once", "example.now", "example.every")

  override def prepare(): Unit = note("prepared")

  override def onWork(key: String, data: Map[String, String]): Task[WorkResult] = Task {
    val runs = Platform.setting("example.bg.runs." + key).toIntOption.getOrElse(0) + 1
    Platform.saveSetting("example.bg.runs." + key, runs.toString)
    Platform.saveSetting("example.bg.work." + key, "run " + runs.toString + " at " + now() + " with the app " +
      (if (ScreenManager.running.nonEmpty) "open" else "closed") +
      (if (data.isEmpty) "" else ", " + data.toList.sorted.map((k, v) => k + "=" + v).mkString(" ")))
    note("work " + key + " ran (" + runs.toString + "), app " + (if (ScreenManager.running.nonEmpty) "open"
      else "closed"))
    WorkResult.Done
  }

  override def onNoticeAction(notice: String, action: String, args: Map[String, String]): Task[Unit] = Task {
    note("notice " + notice + " action " + action + " with the app " +
      (if (ScreenManager.running.nonEmpty) "open" else "closed"))
    Platform.saveSetting("example.bg.action", action + " at " + now())
    if (action == "pause") show(paused = true)
    else if (action == "resume") show(paused = false)
  }

  override def onNoticeDismissed(notice: String): Task[Unit] = Task {
    note("notice " + notice + " dismissed: shown again")
    val state = Platform.setting("example.bg.ongoing")
    if (state.nonEmpty) show(paused = state == "paused")
  }

  override def onRestart(reason: RestartReason): Task[Unit] = Task {
    note("restart " + reason.toString)
    Platform.saveSetting("example.bg.restart", reason.toString + " at " + now())
    val state = Platform.setting("example.bg.ongoing")
    if (state.nonEmpty) show(paused = state == "paused")
  }

  /** The status notice, running or paused, shown or brought up to date; whether it shows. */
  def show(paused: Boolean): Boolean = {
    Platform.saveSetting("example.bg.ongoing", if (paused) "paused" else "running")
    val toggle = if (paused) NoticeAction("Resume", NoticeTap.RunAction("resume"))
      else NoticeAction("Pause", NoticeTap.RunAction("pause"))
    Ongoing.show(OngoingNotice("status", if (paused) "Paused" else "Running",
      (if (paused) "Paused at " else "Running since ") + now(), channel,
      Vector(toggle, NoticeAction("Open", NoticeTap.OpenApp("/background/opened"))),
      tap = Some(NoticeTap.OpenApp("/background"))))
  }

  /** The status notice taken away, and not shown again after a restart. */
  def clear(): Unit = {
    Platform.saveSetting("example.bg.ongoing", "")
    Ongoing.clear("status")
  }

  private def now(): String = Platform.localDateTime(System.currentTimeMillis())

  private def note(words: String): Unit = Platform.log("example background: " + words)
}
