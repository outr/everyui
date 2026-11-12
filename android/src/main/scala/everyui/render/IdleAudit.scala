package everyui.render

import android.os.{Handler, Looper, SystemClock}
import everyui.task.Frames

/** What keeps the app busy while nothing on the screen moves: a diagnostic, off unless the launch asks for it with
  * `--es idleAudit 1`, and costing nothing while off.
  *
  * On, it logs (tag `everyui`, `[idle]`) every five seconds:
  *
  *  - `frames`: how many frames of the app's loop ran, whether it is running now and for how many frames without
  *    idling, the holds out (`Frames.hold`), the frames still owed (`Frames.request`, `wake`), and whether the loop
  *    wants the next for itself, with what wants it (the screen manager's tasks, `ui`'s, the showing screen's), and
  *    how many frames it gave up waiting for (`lost`) and how many failed;
  *  - `motions`: how many nodes' motions (`Component.motion`) are running, each started and stopped also logged as
  *    it happens with what it is for;
  *  - `moving`: what each running motion is for now (its lineage as it stands), and whether its view is in the window,
  *    drawn and under the app's root;
  *  - `main`: every message the main thread ran, by who sent it (the Choreographer's frames, a Handler's Runnable, an
  *    animator), with how many ran and how long they took, the costliest first;
  *  - `wakes`: once the loop has run a second without idling, where each wake, request and hold came from;
  *  - `after`: where each `Platform.after` was asked for, a timer that asks again for itself showing as a steady count;
  *  - `threads`: each of the process's threads by the CPU it used, in ticks of 10 ms (main, RenderThread, OkHttp's).
  *
  * In a release build the names are the shrinker's; retrace them with the build's mapping. */
object IdleAudit {
  /** Whether the audit runs. */
  @volatile var on: Boolean = false

  private val every = 5000L
  private lazy val handler = new Handler(Looper.getMainLooper)

  private val main = new Tally
  private val wakes = new Tally
  private val afters = new Tally
  private var stepped = 0
  private var ticks: Map[String, Long] = Map.empty
  // the message the main thread is running, and when it began
  private var running: String = ""
  private var began = 0L

  /** Start it, where the launch asked for it. */
  def start(): Unit = if (!on && Platform.launchArg("idleAudit") == "1") {
    on = true
    Looper.getMainLooper.setMessageLogging(line => dispatched(line))
    Frames.watched = true
    Frames.watcher = kind => if (Frames.streak > 60) wakes.add(kind + " " + caller(), 0L)
    stepped = Frames.stepped
    ticks = threads()
    Platform.log("[idle] audit on: a report every " + (every / 1000).toString + "s")
    handler.postDelayed(() => report(), every)
  }

  /** A node's motion started or stopped. */
  def motion(started: Boolean, owner: () => String): Unit = if (on)
    Platform.log("[idle] motion " + (if (started) "started" else "stopped") + " (" + Motion.running.toString +
      " running): " + owner())

  /** `Platform.after` asked for, `seconds` from now. */
  def after(seconds: Double): Unit = if (on) afters.add(caller() + " in " + seconds.toString + "s", 0L)

  private def dispatched(line: String): Unit =
    if (line.startsWith(">>>>> Dispatching to ")) {
      running = line.substring(21).replaceAll("\\{[0-9a-f]+\\}|@[0-9a-f]+|/0x[0-9a-f]+|: -?\\d+$", "")
      began = SystemClock.elapsedRealtimeNanos()
    }
    else if (line.startsWith("<<<<< Finished to ") && running.nonEmpty) {
      main.add(running, SystemClock.elapsedRealtimeNanos() - began)
      running = ""
    }

  private def report(): Unit = {
    val ran = Frames.stepped - stepped
    stepped = Frames.stepped
    val wanting = everyui.app.screen.ScreenManager.running.map { m =>
      val screen = m.active().filter(_.needsFrames).map(s => ", screen " + s.getClass.getName).getOrElse("")
      " (ui " + everyui.ui.needsFrames.toString + screen + ")"
    }.getOrElse("")
    Platform.log("[idle] frames: " + ran.toString + " ran, " + (if (Frames.running) "running for " +
      Frames.streak.toString else "idle") + ", holds " + Frames.held.toString + ", owed " + Frames.owed.toString +
      ", wants " + Frames.wants.toString + wanting + ", lost " + Frames.lost.toString + ", failed " +
      Frames.failed.toString + "; motions running: " + Motion.running.toString)
    if (!Motion.live.isEmpty) Platform.log("[idle] moving: " +
      scala.jdk.CollectionConverters.SetHasAsScala(Motion.live).asScala.toList.take(6).map(_.state).mkString("; "))
    Platform.log("[idle] main: " + main.top(10))
    if (wakes.nonEmpty) Platform.log("[idle] wakes: " + wakes.top(6))
    if (afters.nonEmpty) Platform.log("[idle] after: " + afters.top(8))
    val now = threads()
    val used = now.toList.map { case (name, t) => name -> (t - ticks.getOrElse(name, 0L)) }.filter(_._2 > 0L)
      .sortBy(-_._2).take(8)
    ticks = now
    Platform.log("[idle] threads: " + used.map { case (name, t) => name + " " + t.toString }.mkString(", "))
    main.clear()
    wakes.clear()
    afters.clear()
    handler.postDelayed(() => report(), every)
  }

  /** The first frames of the stack that are the app's or the shared layer's, not the audit's, the loop's or reactify's
    * plumbing. */
  private def caller(): String = new Throwable().getStackTrace.iterator.map(_.toString)
    .filterNot(s => s.startsWith("everyui.render.IdleAudit") || s.startsWith("everyui.task.Frames") ||
      s.startsWith("everyui.render.Platform") || s.startsWith("reactify.") || s.startsWith("scala.") ||
      s.startsWith("java.") || s.startsWith("android."))
    .take(3).mkString(" < ")

  /** Each thread of the process by its name and id, and the CPU it has used so far: user and
    * system time, in clock ticks. */
  private def threads(): Map[String, Long] =
    Option(new java.io.File("/proc/self/task").listFiles).map(_.toList).getOrElse(Nil).flatMap { task =>
      try {
        val stat = new String(Host.fileBytes(new java.io.File(task, "stat")))
        val name = stat.substring(stat.indexOf('(') + 1, stat.lastIndexOf(')'))
        val fields = stat.substring(stat.lastIndexOf(')') + 2).split(' ')
        // after the name: state is the first, utime the twelfth, stime the thirteenth
        Some((name + "#" + task.getName) -> (fields(11).toLong + fields(12).toLong))
      } catch { case _: Throwable => None }
    }.toMap
}
