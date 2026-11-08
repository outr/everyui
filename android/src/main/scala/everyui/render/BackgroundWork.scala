package everyui.render

import android.app.job.{JobInfo, JobParameters, JobScheduler, JobService}
import android.content.{ComponentName, Context}
import android.os.{Build, PersistableBundle}
import everyui.activity.ServiceRuntime
import everyui.background.{Background, WorkNetwork, WorkRequest, WorkResult}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.*

/** Scheduled work (`everyui.background.Work`) on JobScheduler: a job for each key, whose id is kept by the key in the
  * app's preferences ("everyui-work") so the key keeps it from run to run, persisted through a restart of the device,
  * run by `BackgroundJobs` in the app's `BackgroundApp.onWork`.
  *
  * JobScheduler rather than WorkManager, which schedules through it on every Android EveryUI runs on: WorkManager
  * brings Kotlin, its coroutines, Room and some thirty more libraries, and reads a resource of its own through a class
  * an app built without Gradle does not have. Forcing a run on a device: `adb shell cmd jobscheduler run -f
  * <package> <id>`, with the id logged as the work is scheduled. */
object BackgroundWork {
  private val keyExtra = "everyui.work.key"
  private val dataPrefix = "everyui.work.data."
  private val nextId = "everyui.next"
  // the runs under way, by job id: one stopped by the system is not finished again by its late answer
  private val running = new ConcurrentHashMap[Integer, AtomicBoolean]

  private def ids(context: Context) = context.getSharedPreferences("everyui-work", Context.MODE_PRIVATE)

  private def scheduler(context: Context): JobScheduler = context.getSystemService(classOf[JobScheduler])

  /** The job id of work `key`, given one where it has none and `make`. */
  private def idOf(context: Context, key: String, make: Boolean): Option[Int] = synchronized {
    val kept = ids(context)
    val id = kept.getInt("key." + key, 0)
    if (id != 0) Some(id)
    else if (!make) None
    else {
      val next = math.max(1, kept.getInt(nextId, 1))
      kept.edit().putInt("key." + key, next).putInt(nextId, next + 1).commit()
      Some(next)
    }
  }

  def schedule(request: WorkRequest): Boolean = try {
    val context = ServiceRuntime.context
    val id = idOf(context, request.key, make = true).get
    val jobs = scheduler(context)
    if (!request.replace && jobs.getPendingJob(id) != null) {
      Platform.log("work: " + request.key + " is already scheduled (job " + id.toString + "), and kept")
      true
    }
    else {
      val extras = new PersistableBundle
      extras.putString(keyExtra, request.key)
      request.data.foreach((k, v) => extras.putString(dataPrefix + k, v))
      val c = request.constraints
      val builder = new JobInfo.Builder(id, new ComponentName(context, classOf[BackgroundJobs]))
        .setExtras(extras)
        .setPersisted(true)
        .setRequiredNetworkType(c.network match {
          case WorkNetwork.NotNeeded => JobInfo.NETWORK_TYPE_NONE
          case WorkNetwork.Connected => JobInfo.NETWORK_TYPE_ANY
          case WorkNetwork.Unmetered => JobInfo.NETWORK_TYPE_UNMETERED
        })
        .setRequiresCharging(c.charging)
        .setRequiresBatteryNotLow(c.batteryNotLow)
        .setRequiresDeviceIdle(c.idle)
      // Android refuses a backoff for work that waits for the device to be idle: it is tried again at the next idle
      if (!c.idle) builder.setBackoffCriteria(request.backoff.millis,
        if (request.backoff.exponential) JobInfo.BACKOFF_POLICY_EXPONENTIAL else JobInfo.BACKOFF_POLICY_LINEAR)
      if (request.periodic) builder.setPeriodic(request.every.toLong * 60000L)
      else {
        if (request.delay > 0.0) builder.setMinimumLatency((request.delay * 1000.0).toLong)
        if (request.expedited && Build.VERSION.SDK_INT >= 31) builder.setExpedited(true)
      }
      val scheduled = jobs.schedule(builder.build()) == JobScheduler.RESULT_SUCCESS
      Platform.log("work: " + request.key + (if (scheduled) " scheduled as job " else " not scheduled, job ") +
        id.toString + (if (request.periodic) ", every " + request.every.toString + " minutes" else ", once"))
      scheduled
    }
  }
  catch {
    case t: Throwable =>
      Platform.logError("work: " + request.key + " not scheduled", t)
      false
  }

  def cancel(key: String): Unit = try {
    val context = ServiceRuntime.context
    idOf(context, key, make = false).foreach { id =>
      scheduler(context).cancel(id)
      Platform.log("work: " + key + " cancelled (job " + id.toString + ")")
    }
  }
  catch { case t: Throwable => Platform.logError("work: " + key + " not cancelled", t) }

  def pending(key: String): Boolean = try {
    val context = ServiceRuntime.context
    idOf(context, key, make = false).exists(id => scheduler(context).getPendingJob(id) != null)
  }
  catch { case _: Throwable => false }

  /** A job begun by `BackgroundJobs`: the app's `onWork` run with its key and data, and the job finished as it
    * answers (Retry asks for another try after the backoff), or as Retry once the deadline passes. */
  def started(service: JobService, params: JobParameters): Boolean = {
    BackgroundAccess.start(service)
    val extras = params.getExtras
    val key = Option(extras.getString(keyExtra)).getOrElse("")
    val data = extras.keySet.asScala.toList.filter(_.startsWith(dataPrefix))
      .flatMap(k => Option(extras.getString(k)).map(k.stripPrefix(dataPrefix) -> _)).toMap
    val over = new AtomicBoolean(false)
    running.put(params.getJobId, over)
    BackgroundAccess.run("work " + key, Background.work(key, data), BackgroundAccess.jobDeadline,
      WorkResult.Retry) { result =>
      Platform.log("work: " + key + " ran (job " + params.getJobId.toString + "): " + result.toString)
      running.remove(params.getJobId, over)
      if (over.compareAndSet(false, true)) service.jobFinished(params, result == WorkResult.Retry)
    }
    true
  }

  /** A job the system stopped (a constraint no longer holds, its time ran out): to be run again, and its late
    * answer let go. */
  def stopped(params: JobParameters): Boolean = {
    Option(running.remove(params.getJobId)).foreach(_.set(true))
    Platform.log("work: job " + params.getJobId.toString + " stopped by the system, to run again")
    true
  }
}
