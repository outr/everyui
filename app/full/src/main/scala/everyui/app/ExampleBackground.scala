package everyui.app

import everyui.app.screen.ScreenManager
import everyui.async.Task
import everyui.background.{BackgroundApp, RestartReason, Work, WorkResult}
import everyui.Color
import everyui.font.MaterialIcon
import everyui.push.{NoticeAction, NoticeChannel, NoticeTap, Ongoing, OngoingNotice, Push, PushAction, ReplyOutcome}
import everyui.render.Platform

/** What the example runs with the app closed (build.sbt names this object, `everyuiBackground`; `demo=background`
  * drives it): each run of scheduled work noted with its time, whether the app was open, and how many runs there have
  * been; an ongoing notice ("status", its small icon sync or pause) whose Pause and Resume run here and whose Open
  * opens `/background/opened`, shown again when it is swiped away and after a restart; each restart noted with its
  * reason; a push notification's button that runs in the background noted with its push; a reply typed into a mail
  * push noted with its words, the push then shown with the reply, taken away or shown as sent as its data's "after"
  * says; a data push noted with its keys, synced at once, past its ten seconds ("slow"), or handed to expedited work ("long", "example.sync"), and one
  * delivered by work ("example.datapush") in a process started for it. What it notes is in the settings
  * ("example.bg.*"), which the demo reads, and in the log ("[ui] example background: ..."). */
object ExampleBackground extends BackgroundApp {
  val channel: NoticeChannel = NoticeChannel("example.status", "Status", "What the example does in the background")
  val workKeys: Vector[String] = Vector("example.once", "example.now", "example.every")

  override def prepare(): Unit = note("prepared")

  override def onWork(key: String, data: Map[String, String]): Task[WorkResult] = key match {
    // a data push delivered from a process WorkManager started, the app killed (as Firebase starts one)
    case "example.datapush" => Task {
      note("work example.datapush: delivering " + said(data) + " with the app " + open())
      Push.deliver(data)
      WorkResult.Done
    }
    // the long sync a data push handed over: twenty seconds, which a message's ten could not hold
    case "example.sync" =>
      Task(note("work example.sync started (" + said(data) + ")")).flatMap(_ => later(20.0)).map { _ =>
        note("work example.sync done after 20 s")
        Platform.saveSetting("example.bg.data", "long sync done at " + now())
        WorkResult.Done
      }
    case _ => ran(key, data)
  }

  private def ran(key: String, data: Map[String, String]): Task[WorkResult] = Task {
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

  override def onPushAction(action: PushAction): Task[Unit] = Task {
    note("push action " + action.id + " on " + action.message.title + " (" +
      action.message.data.toList.sorted.map((k, v) => k + "=" + v).mkString(" ") + ") with the app closed")
    Platform.saveSetting("example.bg.push", action.id + " on \"" + action.message.title + "\" at " + now() +
      " with the app closed")
  }

  override def onPushReply(action: PushAction): Task[ReplyOutcome] = Task {
    val open = if (ScreenManager.running.nonEmpty) "open" else "closed"
    note("push reply " + action.id + " \"" + action.text.getOrElse("") + "\" on " + action.message.title + " (" +
      action.message.body + ") with the app " + open)
    Platform.saveSetting("example.bg.push", "replied \"" + action.text.getOrElse("") + "\" to \"" + action.message.body +
      "\" at " + now() + " with the app " + open)
    action.message.data.getOrElse("after", "") match {
      case "close" => ReplyOutcome.ReplyClosed
      case "update" => ReplyOutcome.ReplyUpdated(action.message.copy(body = "Sent: " + action.text.getOrElse(""),
        actions = Vector.empty))
      case _ => ReplyOutcome.ReplyShown
    }
  }

  /** A data push with the app not in front: "sync" says what to sync. "mail" syncs at once; "slow" takes fifteen
    * seconds, past the ten a message has; "long" hands its twenty seconds to expedited work and answers. */
  override def onPush(data: Map[String, String]): Task[Unit] = {
    note("push data " + said(data) + " with the app " + open())
    Platform.saveSetting("example.bg.data", "onPush " + said(data) + " at " + now() + " with the app " + open())
    data.getOrElse("sync", "") match {
      case "slow" => later(15.0).map(_ => note("push data slow sync done after 15 s"))
      case "long" => Task {
        val scheduled = Work.once("example.sync", expedited = true, data = data)
        note("push data long sync handed to expedited work: " + scheduled.toString)
      }
      case _ => Task(note("push data synced"))
    }
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
      tap = Some(NoticeTap.OpenApp("/background")),
      noticeIcon = Some(if (paused) MaterialIcon.Pause else MaterialIcon.Sync),
      noticeColor = Some(Color.fromHex(if (paused) "#F57C00" else "#1E88E5"))))
  }

  /** The status notice taken away, and not shown again after a restart. */
  def clear(): Unit = {
    Platform.saveSetting("example.bg.ongoing", "")
    Ongoing.clear("status")
  }

  private def open(): String = if (ScreenManager.running.nonEmpty) "open" else "closed"

  private def said(data: Map[String, String]): String = data.toList.sorted.map((k, v) => k + "=" + v).mkString(" ")

  private def later(seconds: Double): Task[Unit] = Task.async[Unit](done => Platform.after(seconds, () => done(())))

  private def now(): String = Platform.localDateTime(System.currentTimeMillis())

  private def note(words: String): Unit = Platform.log("example background: " + words)
}
