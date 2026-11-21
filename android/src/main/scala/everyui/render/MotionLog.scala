package everyui.render

import android.app.Activity
import android.os.{Bundle, Handler, Looper}

/** The motions' pacing as it goes, for a freeze that shows only on a device: off unless the launch asks for it with
  * `--es motionLog 1`, and costing nothing while off.
  *
  * On, it logs (tag `everyui`, `[motion]`) each second how many steps ran in it, whether a step is asked for and
  * whether the app is away (`MotionPacer`), how many motions run and how many are set in a window, the window's
  * focus and visibility, and for each motion set but not running why (`Motion.state`); and each of the activities'
  * lifecycle callbacks (with which activity: a new one is one made again), its window's focus and the app's away
  * and return as they come. */
object MotionLog {
  @volatile var on: Boolean = false
  /** Steps run since the last line. */
  var steps: Int = 0
  private lazy val handler = new Handler(Looper.getMainLooper)

  /** Start it, where the launch asked for it. */
  def start(activity: Activity): Unit = if (!on && Platform.launchArg("motionLog") == "1") {
    on = true
    activity.getApplication.registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks {
      override def onActivityCreated(a: Activity, b: Bundle): Unit = say("onCreate " + id(a))
      override def onActivityStarted(a: Activity): Unit = say("onStart " + id(a))
      override def onActivityResumed(a: Activity): Unit = say("onResume " + id(a))
      override def onActivityPaused(a: Activity): Unit = say("onPause " + id(a))
      override def onActivityStopped(a: Activity): Unit = say("onStop " + id(a))
      override def onActivitySaveInstanceState(a: Activity, b: Bundle): Unit = ()
      override def onActivityDestroyed(a: Activity): Unit = say("onDestroy " + id(a))
    })
    Platform.onFocus(() => say("window focus true"))
    Platform.onBlur(() => say("window focus false"))
    Platform.onAway(() => say("away"))
    Platform.onResume(() => say("resumed"))
    say("on: a line each second")
    handler.postDelayed(() => line(), 1000L)
  }

  // which activity: a new one is the activity made again
  private def id(a: Activity): String = Integer.toHexString(System.identityHashCode(a))

  /** Something that happened, logged as it does. */
  def say(what: String): Unit = if (on) Platform.log("[motion] " + what)

  private def line(): Unit = {
    val decor = Host.activity.getWindow.getDecorView
    say("steps " + steps.toString + ", " + Motion.pacing + ", window focus " + decor.hasWindowFocus.toString +
      ", window visibility " + decor.getWindowVisibility.toString + ", shown " + decor.isShown.toString)
    steps = 0
    Motion.waiting.foreach(m => say("  waiting: " + m))
    handler.postDelayed(() => line(), 1000L)
  }
}
