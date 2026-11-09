package everyui.background

import everyui.async.Task

/** Scheduled work run while the app is open, where the platform runs nothing with it closed (the web, the desktop, an
  * Apple TV, a Roku; Android where the build names no `BackgroundApp`): `Work` hands it what is scheduled, and it
  * runs each in `run` (the app's `BackgroundApp.onWork`) when it is due, with the same retry and backoff as Android.
  * What is scheduled, when each is next due and when it last ran are kept (`setting`, `save`: the app's settings), so
  * they outlast the app: work due while it was closed runs once as it starts again (`resume`), periodic work missed
  * several times among them, once, its next a period after.
  *
  * Of the constraints, the network is waited for where `online` says there is none (an unmetered one is any
  * network: whether a network is metered cannot be read); charging, the battery and the device idle cannot be read,
  * and are taken as met. Nothing runs until `resume`: the app registers its `BackgroundApp` first
  * (`Background.register`), so work never finds no one to run it.
  *
  * Its clock, its timers, its settings and the network are handed in, so a spec drives it with its own. */
final class WorkRunner(clock: () => Long,
                       after: (Double, () => Unit) => Unit,
                       setting: String => String,
                       save: (String, String) => Unit,
                       online: () => Boolean,
                       run: (String, Map[String, String]) => Task[WorkResult]) {
  private val indexKey = "everyui.work"
  private val recordKey = "everyui.work."
  private var armed = false
  // a timer is for the record as it was when it was set: one set before a change finds its generation gone
  private var generations = Map.empty[String, Int]
  private var running = Set.empty[String]

  /** How long a run has before it is taken as needing another try. */
  val deadline: Double = 570.0
  /** How often work waiting for a network looks again, in seconds. */
  val recheck: Double = 30.0

  /** The keys of the work scheduled, in the order it was scheduled. */
  def keys: Vector[String] = {
    val index = setting(indexKey)
    if (index.isEmpty) Vector.empty else index.split("\\|", -1).toVector.map(WorkRecord.unescape).filter(_.nonEmpty)
  }

  /** What is kept of work `key`, if it is scheduled. */
  def record(key: String): Option[WorkRecord] = {
    val text = setting(recordKey + key)
    if (text.isEmpty) None else WorkRecord.decode(text)
  }

  def pending(key: String): Boolean = record(key).nonEmpty

  /** `request` kept and, once running, timed: once after its delay, periodic at once (its first run, as Android's),
    * unless one already scheduled with its key is kept (`replace` false). */
  def schedule(request: WorkRequest): Boolean = {
    if (!request.replace && pending(request.key)) true
    else {
      val now = clock()
      keep(WorkRecord(request.key, request.every, request.constraints, request.backoff, request.data,
        if (request.periodic) now else now + (math.max(0.0, request.delay) * 1000.0).toLong,
        record(request.key).map(_.lastRun).getOrElse(0L), 0))
      arm(request.key)
      true
    }
  }

  def cancel(key: String): Unit = {
    generations += key -> (generations.getOrElse(key, 0) + 1)
    save(recordKey + key, "")
    save(indexKey, keys.filterNot(_ == key).map(WorkRecord.escape).mkString("|"))
  }

  /** Every work kept timed from now: what came due while the app was closed runs at once, once. */
  def resume(): Unit = if (!armed) {
    armed = true
    keys.foreach(arm)
  }

  private def keep(r: WorkRecord): Unit = {
    save(recordKey + r.key, r.encode)
    if (!keys.contains(r.key)) save(indexKey, (keys :+ r.key).map(WorkRecord.escape).mkString("|"))
  }

  private def arm(key: String): Unit = if (armed) record(key).foreach { r =>
    val generation = generations.getOrElse(key, 0) + 1
    generations += key -> generation
    val wait = math.max(0L, r.due - clock()).toDouble / 1000.0
    after(wait, () => if (generations.get(key).contains(generation)) due(key, generation))
  }

  private def due(key: String, generation: Int): Unit = record(key).foreach { r =>
    if (running.contains(key)) ()
    else if (r.constraints.network != WorkNetwork.NotNeeded && !online())
      after(recheck, () => if (generations.get(key).contains(generation)) due(key, generation))
    else {
      running += key
      var answered = false
      val answer = (result: WorkResult) => if (!answered) {
        answered = true
        running -= key
        ran(key, generation, result)
      }
      after(deadline, () => answer(WorkResult.Retry))
      try run(key, r.data).map(answer).start()
      catch {
        case t: Throwable =>
          everyui.render.Platform.log("work: " + key + " failed: " + t.toString)
          answer(WorkResult.Failed)
      }
    }
  }

  // the answer of a run: once done or failed, it is let go; periodic, due a period on; retried, after its backoff
  private def ran(key: String, generation: Int, result: WorkResult): Unit =
    if (generations.get(key).contains(generation)) record(key).foreach { r =>
      val now = clock()
      everyui.render.Platform.log("work: " + key + " ran in the app: " + result.toString)
      result match {
        case WorkResult.Retry =>
          val tries = r.retries + 1
          val first = r.backoff.millis
          val wait = if (r.backoff.exponential) first * (1L << math.min(20, tries - 1)) else first * tries
          keep(r.copy(due = now + math.min(wait, (WorkBackoff.most * 1000.0).toLong), lastRun = now, retries = tries))
          arm(key)
        case _ if r.every > 0 =>
          keep(r.copy(due = now + r.every.toLong * 60000L, lastRun = now, retries = 0))
          arm(key)
        case _ => cancel(key)
      }
    }
}
