package everyui.render

import android.content.Context
import android.os.Build
import androidx.work.{BackoffPolicy, Configuration, Constraints, Data, ExistingPeriodicWorkPolicy, ExistingWorkPolicy,
  ListenableWorker, NetworkType, OneTimeWorkRequest, OutOfQuotaPolicy, PeriodicWorkRequest, WorkManager}
import com.google.common.util.concurrent.{ListenableFuture, SettableFuture}
import everyui.activity.ServiceRuntime
import everyui.background.{Background, WorkNetwork, WorkRequest, WorkResult}
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import scala.jdk.CollectionConverters.*

/** Scheduled work (`everyui.background.Work`) on WorkManager: unique work by key (`enqueueUniqueWork`, replacing or
  * keeping as the request says; `enqueueUniquePeriodicWork`, updating its period and constraints in place), run by
  * `BackgroundWorker` in the app's `BackgroundApp.onWork`, kept by WorkManager through a restart and an update.
  * WorkManager schedules through JobScheduler: `adb shell cmd jobscheduler run -f <package> <id>` runs one now, its id
  * the SystemJobService job `dumpsys jobscheduler <package>` lists; its own diagnostics answer
  * `adb shell am broadcast -a androidx.work.diagnostics.REQUEST_DIAGNOSTICS -p <package>` in the log.
  *
  * WorkManager is made ready as the process starts (`BackgroundStart`, which the build declares with the rest of what
  * runs with the app closed), so a job heard in a process started for it finds it ready; `ready` makes it ready where
  * that has not happened. */
object BackgroundWork {
  private val keyData = "everyui.work.key"
  private val dataPrefix = "everyui.work.data."
  // the runs under way, by WorkManager's id: one stopped is not answered by its late result
  private val running = new ConcurrentHashMap[UUID, SettableFuture[ListenableWorker.Result]]

  /** WorkManager made ready with EveryUI's configuration, once a process. */
  def ready(context: Context): Unit = synchronized {
    if (!WorkManager.isInitialized) WorkManager.initialize(context.getApplicationContext,
      new Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build())
  }

  private def manager: WorkManager = {
    val context = ServiceRuntime.context
    ready(context)
    WorkManager.getInstance(context)
  }

  def schedule(request: WorkRequest): Boolean = try {
    val data = new Data.Builder().putString(keyData, request.key)
    request.data.foreach((k, v) => data.putString(dataPrefix + k, v))
    val c = request.constraints
    val constraints = new Constraints.Builder()
      .setRequiredNetworkType(c.network match {
        case WorkNetwork.NotNeeded => NetworkType.NOT_REQUIRED
        case WorkNetwork.Connected => NetworkType.CONNECTED
        case WorkNetwork.Unmetered => NetworkType.UNMETERED
      })
      .setRequiresCharging(c.charging)
      .setRequiresBatteryNotLow(c.batteryNotLow)
      .setRequiresDeviceIdle(c.idle)
      .build()
    val policy = if (request.backoff.exponential) BackoffPolicy.EXPONENTIAL else BackoffPolicy.LINEAR
    val id =
      if (request.periodic) {
        val builder = new PeriodicWorkRequest.Builder(classOf[BackgroundWorker], request.every.toLong, TimeUnit.MINUTES)
          .setConstraints(constraints).setInputData(data.build())
        // WorkManager refuses a backoff for work that waits for the device to be idle: it is tried at the next idle
        if (!c.idle) builder.setBackoffCriteria(policy, request.backoff.millis, TimeUnit.MILLISECONDS)
        val work = builder.build()
        manager.enqueueUniquePeriodicWork(request.key, ExistingPeriodicWorkPolicy.UPDATE, work)
        work.getId
      }
      else {
        val builder = new OneTimeWorkRequest.Builder(classOf[BackgroundWorker])
          .setConstraints(constraints).setInputData(data.build())
        if (!c.idle) builder.setBackoffCriteria(policy, request.backoff.millis, TimeUnit.MILLISECONDS)
        if (request.delay > 0.0) builder.setInitialDelay((request.delay * 1000.0).toLong, TimeUnit.MILLISECONDS)
        // a job from Android 12; before, WorkManager would run it in the foreground, with a notice of its own
        if (request.expedited && Build.VERSION.SDK_INT >= 31)
          builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        val work = builder.build()
        manager.enqueueUniqueWork(request.key,
          if (request.replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, work)
        work.getId
      }
    Platform.log("work: " + request.key + " enqueued (" + id.toString + ")" +
      (if (request.periodic) ", every " + request.every.toString + " minutes" else ", once") +
      (if (!request.periodic && !request.replace) ", or the one already scheduled kept" else ""))
    true
  }
  catch {
    case t: Throwable =>
      Platform.logError("work: " + request.key + " not scheduled", t)
      false
  }

  def cancel(key: String): Unit = try {
    manager.cancelUniqueWork(key)
    Platform.log("work: " + key + " cancelled")
  }
  catch { case t: Throwable => Platform.logError("work: " + key + " not cancelled", t) }

  /** Whether work `key` is scheduled and not finished: waiting, blocked or running. */
  def pending(key: String): Boolean = try {
    manager.getWorkInfosForUniqueWork(key).get(2, TimeUnit.SECONDS).asScala.exists(!_.getState.isFinished)
  }
  catch { case _: Throwable => false }

  /** A run begun by `BackgroundWorker`: the app's `onWork` with its key and data, answered as the app answers (Retry
    * runs it again after its backoff), or as Retry once the deadline passes. */
  def started(worker: ListenableWorker): ListenableFuture[ListenableWorker.Result] = {
    val future = SettableFuture.create[ListenableWorker.Result]()
    BackgroundAccess.start(worker.getApplicationContext)
    val input = worker.getInputData
    val key = Option(input.getString(keyData)).getOrElse("")
    val data = input.getKeyValueMap.asScala.toMap.collect {
      case (k, v: String) if k.startsWith(dataPrefix) => k.stripPrefix(dataPrefix) -> v
    }
    running.put(worker.getId, future)
    BackgroundAccess.run("work " + key, Background.work(key, data), BackgroundAccess.jobDeadline,
      WorkResult.Retry) { result =>
      Platform.log("work: " + key + " ran (" + worker.getId.toString + "): " + result.toString)
      running.remove(worker.getId)
      future.set(result match {
        case WorkResult.Done => ListenableWorker.Result.success()
        case WorkResult.Retry => ListenableWorker.Result.retry()
        case WorkResult.Failed => ListenableWorker.Result.failure()
      })
    }
    future
  }

  /** A run WorkManager stopped (a constraint no longer holds, its time ran out, it was cancelled): WorkManager runs it
    * again where it should, and its late answer goes nowhere. */
  def stopped(worker: ListenableWorker): Unit = {
    Option(running.remove(worker.getId)).foreach(_.cancel(false))
    Platform.log("work: " + worker.getId.toString + " stopped by WorkManager")
  }
}
