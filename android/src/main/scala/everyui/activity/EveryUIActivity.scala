package everyui.activity

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.{KeyEvent, ViewGroup, WindowInsets, WindowInsetsController, WindowManager}
import everyui.render.{Host, NodeGroup}

/** The Android entry point an app's activity extends: a full-screen window whose content view is the render root,
  * then `run` (the app's `main`).
  *
  * tools/check.sh launches it with `--es freeze 0.434` to freeze a frame, and `--es viewport 1920x1080` to lay out at
  * that size whatever the screen — so a phone emulator produces the same frame as a 1080p TV (and shows its top-left
  * corner). */
abstract class EveryUIActivity extends Activity {
  /** The app: build its screens. */
  protected def run(): Unit

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    Host.activity = this
    everyui.network.NetworkBackend.init()   // rapid on a thread pool, before anything starts a Task
    Host.freeze = extra("freeze").flatMap(_.toDoubleOption).getOrElse(-1.0)
    Host.path = extra("path").getOrElse("")
    extra("viewport").map(_.split('x')).foreach {
      case Array(w, h) =>
        Host.viewportWidth = w.toDouble
        Host.viewportHeight = h.toDouble
      case _ => ()
    }

    // The process can outlive its activity: Android takes an activity away to make room (a 4K film in another app, on a
    // SHIELD) and makes a new one when the app is returned to, in the same process, with the app still running and its
    // screens built. The new activity takes over the view the app is drawing into. Starting the app a second time into
    // an empty one left nothing but the launch logo on the screen, while the first went on drawing where nobody saw it.
    val adopted = Host.root != null
    if (adopted) adopt() else build()

    // what has been downloaded is kept on disk, as the image servers' own headers allow: a cold start shows the rails'
    // posters from the device rather than fetching every one of them again
    try android.net.http.HttpResponseCache.install(new java.io.File(getCacheDir, "http"), 64L * 1024L * 1024L)
    catch { case t: java.io.IOException => android.util.Log.w("everyui", "no disk cache: " + t.getMessage) }
    val window = getWindow
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.setDecorFitsSystemWindows(false)
    val attributes = window.getAttributes
    attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    window.setAttributes(attributes)
    val insets = window.getInsetsController
    if (insets != null) {
      insets.hide(WindowInsets.Type.systemBars())
      insets.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
    }
    // the frames it draws, measured again for this window where the app is listening (FrameTimings)
    everyui.render.FrameTimings.attach(this)

    if (!adopted) run()
  }

  /** The render root, made. */
  private def build(): Unit = {
    val root = new NodeGroup(this)
    // The render root is a viewport: content past its edges is clipped, as on every other backend.
    root.setClipChildren(true)
    // see-through until the app paints its own ground, so the window's launch background (an app's splash) shows
    // from the moment the process starts rather than a white view over it
    root.setBackgroundColor(Color.TRANSPARENT)
    def extent(size: Double): Int = if (size > 0.0) size.toInt else ViewGroup.LayoutParams.MATCH_PARENT
    setContentView(root, new ViewGroup.LayoutParams(extent(Host.viewportWidth), extent(Host.viewportHeight)))
    Host.root = root
    if (Host.viewportWidth <= 0.0) {
      Host.scale = getResources.getDisplayMetrics.density.toDouble
      root.setPivotX(0f)
      root.setPivotY(0f)
      root.setScaleX(Host.scale.toFloat)
      root.setScaleY(Host.scale.toFloat)
    }
  }

  /** The render root a previous activity of this process made, moved into this one, as it is. */
  private def adopt(): Unit = {
    val root = Host.root
    root.getParent match {
      case parent: ViewGroup => parent.removeView(root)
      case _ => ()
    }
    setContentView(root, root.getLayoutParams)
  }

  /** Whether the app took the Back being held, so its release is the app's too. */
  private var backTaken = false

  /** Every key to the shared layer's hook, by the web's name. Back goes there first, and is Android's (leaving the app)
    * only when the app has nowhere to go back to; a Back the app took keeps its release, or Android would act on that. */
  override def dispatchKeyEvent(e: KeyEvent): Boolean = {
    val hook = Host.key
    if (hook == null || e.getAction == KeyEvent.ACTION_MULTIPLE) super.dispatchKeyEvent(e)
    else if (e.getKeyCode == KeyEvent.KEYCODE_BACK) {
      if (e.getAction == KeyEvent.ACTION_DOWN) {
        if (e.getRepeatCount == 0) backTaken = hook("Back", true)
        backTaken || super.dispatchKeyEvent(e)
      }
      else {
        hook("Back", false)
        val taken = backTaken
        backTaken = false
        taken || super.dispatchKeyEvent(e)
      }
    }
    else hook(Host.keyName(e), e.getAction == KeyEvent.ACTION_DOWN) || super.dispatchKeyEvent(e)
  }

  // the system's answer to a permission asked for (Permissions.ask)
  override def onRequestPermissionsResult(code: Int, permissions: Array[String], results: Array[Int]): Unit = {
    super.onRequestPermissionsResult(code, permissions, results)
    everyui.render.Permissions.answered(code,
      results.nonEmpty && results.forall(_ == android.content.pm.PackageManager.PERMISSION_GRANTED))
  }

  /** Somebody is in front of it again: the app was away and is back, which is a sign of use like any key. Not the
    * first time it is in front, which Android also calls resuming: a launch is not a return, and an app that read
    * everything again on its way back read it all twice on the way in. */
  override def onResume(): Unit = {
    super.onResume()
    if (stopped) {
      stopped = false
      everyui.render.Platform.resumed()
    }
  }

  /** Whether it has been out of sight since it was last in front. */
  private var stopped = false

  /** Nobody can see it now — another app has the screen, or the device is going to sleep. Stop rather than pause,
    * which a picture-in-picture or a dialog over the top also brings, and neither means nobody is watching. */
  override def onStop(): Unit = {
    stopped = true
    everyui.render.Platform.away()
    super.onStop()
  }

  /** The shared layer's singletons (`ui`, the app) live as long as the process, as they live as long as a page or a
    * channel, so leaving the app ends the process and the next launch starts fresh. */
  override def onDestroy(): Unit = {
    super.onDestroy()
    if (isFinishing) android.os.Process.killProcess(android.os.Process.myPid())
  }

  private def extra(name: String): Option[String] = Option(getIntent.getStringExtra(name))
}
