package everyui.render

import android.app.job.{JobParameters, JobService}

/** Where JobScheduler runs the app's scheduled work (`everyui.background.Work`), with the app open or closed: each
  * job is the app's `BackgroundApp.onWork`, held to its deadline (`BackgroundAccess.jobDeadline`). sbt-everyui
  * declares it where the build names a `BackgroundApp` (`everyuiBackground`). */
class BackgroundJobs extends JobService {
  override def onStartJob(params: JobParameters): Boolean = BackgroundWork.started(this, params)

  override def onStopJob(params: JobParameters): Boolean = BackgroundWork.stopped(params)
}
