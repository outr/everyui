package everyui.render

import android.content.Context
import androidx.work.{ListenableWorker, WorkerParameters}
import com.google.common.util.concurrent.ListenableFuture

/** Where WorkManager runs the app's scheduled work (`everyui.background.Work`), with the app open or closed: each run
  * is the app's `BackgroundApp.onWork`, started on the main thread as WorkManager starts a ListenableWorker, and held
  * to its deadline (`BackgroundAccess.jobDeadline`). WorkManager makes it by name, so the shrinker keeps it
  * (sbt-everyui's background rules). */
class BackgroundWorker(context: Context, params: WorkerParameters) extends ListenableWorker(context, params) {
  override def startWork(): ListenableFuture[ListenableWorker.Result] = BackgroundWork.started(this)

  override def onStopped(): Unit = BackgroundWork.stopped(this)
}
