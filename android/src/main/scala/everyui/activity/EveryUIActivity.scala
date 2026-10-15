package everyui.activity

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.{InputDevice, KeyEvent, MotionEvent, ViewGroup, WindowInsets, WindowInsetsController, WindowManager}
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
    // a frame the loop asked for and never had is asked for again, and logged with where the loop was woken from
    everyui.task.Frames.guarded = true
    everyui.task.Frames.origin = () => new everyui.render.WokeFrom
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
    // before anything goes out over HTTPS: the Let's Encrypt roots, on Android too old to know them
    everyui.render.LegacyTrust.install()
    val window = getWindow
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    immersive(window)
    // the frames it draws, measured again for this window where the app is listening (FrameTimings)
    everyui.render.FrameTimings.attach(this)

    // Back through the dispatcher, where the system sends it nowhere else
    BackCallback.register(this)

    if (!adopted) run()
    // what keeps it busy while nothing moves, logged where the launch asks (`--es idleAudit 1`)
    everyui.render.IdleAudit.start()
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
    // laid out in the density's units, times the size the app is drawn at (everyui.theme.UiScale)
    if (Host.viewportWidth <= 0.0) Host.density = getResources.getDisplayMetrics.density.toDouble
    Host.scaleRoot()
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
  /** Whether a Back nothing took is held to ask before the app is left (LeaveBack). */
  private var leaveHeld = false

  /** A touch read over the scale the app is drawn at as it began (Host.touchScale), whatever it changes as it is heard. */
  override def dispatchTouchEvent(e: MotionEvent): Boolean = {
    Host.touchScale = Host.scale
    super.dispatchTouchEvent(e)
  }
  /** A mouse's moves and wheel, read over the scale they began at, as a touch is. */
  override def dispatchGenericMotionEvent(e: MotionEvent): Boolean = {
    Host.touchScale = Host.scale
    super.dispatchGenericMotionEvent(e)
  }

  /** Every key to the shared layer's hook, by the web's name. Back goes there first, and is Android's (leaving the app)
    * only when the app has nowhere to go back to (asked first while it is guarded: LeaveBack); a Back the app took
    * keeps its release, or Android would act on that. A mouse's back button (its BUTTON_BACK, which Android sends as KEYCODE_BACK from the mouse) is BrowserBack: Back
    * all the same, but told from a remote's by its source, so a keymap's chord can take it alone; its forward button
    * (KEYCODE_FORWARD) is BrowserForward. */
  override def dispatchKeyEvent(e: KeyEvent): Boolean = {
    val hook = Host.key
    Host.modifiers = everyui.event.Modifiers(e.isCtrlPressed, e.isMetaPressed, e.isAltPressed, e.isShiftPressed)
    // a key is an embed's while its web view has the focus, but a remote's Back is always the app's: a web view does
    // nothing with it, and passed on it would leave the app (and a mouse's back and forward buttons are the app's too)
    Host.embedded = getCurrentFocus match {
      case embed: everyui.render.EmbedView if e.getKeyCode != KeyEvent.KEYCODE_BACK && e.getKeyCode != KeyEvent.KEYCODE_FORWARD =>
        embed.name
      case _ => ""
    }
    if (hook == null || e.getAction == KeyEvent.ACTION_MULTIPLE) super.dispatchKeyEvent(e)
    else if (e.getKeyCode == KeyEvent.KEYCODE_BACK) {
      val back = if (e.isFromSource(InputDevice.SOURCE_MOUSE)) "BrowserBack" else "Back"
      if (e.getAction == KeyEvent.ACTION_DOWN) {
        if (e.getRepeatCount == 0) {
          backTaken = hook(back, true)
          leaveHeld = !backTaken && LeaveBack.holds()
        }
        backTaken || leaveHeld || super.dispatchKeyEvent(e)
      }
      else {
        hook(back, false)
        val taken = backTaken || leaveHeld
        if (leaveHeld) LeaveBack.ask(this)
        backTaken = false
        leaveHeld = false
        taken || super.dispatchKeyEvent(e)
      }
    }
    else {
      val down = e.getAction == KeyEvent.ACTION_DOWN
      Host.repeat = down && e.getRepeatCount > 0
      val taken = try hook(Host.keyName(e), down) finally Host.repeat = false
      taken || super.dispatchKeyEvent(e)
    }
  }

  /** Edge to edge with the system bars hidden, by whichever means this Android has.
    *
    * The inset controller is Android 11's; the cutout mode, Android 9's. Called unguarded on anything older they
    * were a crash before the first frame — the app runs back to Android 6, whose televisions are still in living
    * rooms. There the older flags do the same job: laid out under the bars, bars hidden, and back again after a
    * swipe shows them. A television has no bars to speak of either way; a phone does. */
  private def immersive(window: android.view.Window): Unit =
    if (android.os.Build.VERSION.SDK_INT >= 30) {
      window.setDecorFitsSystemWindows(false)
      val attributes = window.getAttributes
      attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
      window.setAttributes(attributes)
      val insets = window.getInsetsController
      if (insets != null) {
        insets.hide(WindowInsets.Type.systemBars())
        insets.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
      }
    }
    else {
      if (android.os.Build.VERSION.SDK_INT >= 28) {
        val attributes = window.getAttributes
        attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.setAttributes(attributes)
      }
      window.getDecorView.setSystemUiVisibility(
        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
          android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
          android.view.View.SYSTEM_UI_FLAG_FULLSCREEN | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

  // the system's answer to a permission asked for (Permissions.ask)
  override def onRequestPermissionsResult(code: Int, permissions: Array[String], results: Array[Int]): Unit = {
    super.onRequestPermissionsResult(code, permissions, results)
    everyui.render.Permissions.answered(code,
      results.nonEmpty && results.forall(_ == android.content.pm.PackageManager.PERMISSION_GRANTED))
  }

  // the app, already running, launched again: by a tap on one of its push notifications (everyui.push.Push) or its
  // notices (Platform.showNotice), or with extras of its own (Platform.onLaunch), which launchArg reads from now on
  override def onNewIntent(intent: android.content.Intent): Unit = {
    super.onNewIntent(intent)
    setIntent(intent)
    everyui.render.PushAccess.tapped(intent)
    everyui.render.Notices.tapped(intent)
    everyui.render.Platform.launched(extras(intent))
  }

  /** An intent's string extras, by name. */
  private def extras(intent: android.content.Intent): Map[String, String] = Option(intent.getExtras) match {
    case Some(bundle) =>
      import scala.jdk.CollectionConverters.*
      bundle.keySet.asScala.toList.flatMap(key => bundle.get(key) match {
        case s: String => Some(key -> s)
        case _ => None
      }).toMap
    case None => Map.empty
  }

  // what an activity started for its answer said: the document picker's files (FileAccess.pick)
  override def onActivityResult(code: Int, result: Int, data: android.content.Intent): Unit = {
    super.onActivityResult(code, result, data)
    everyui.render.FileAccess.answered(code, result, data)
  }

  /** Somebody is in front of it again: the app was away and is back, which is a sign of use like any key. Not the
    * first time it is in front, which Android also calls resuming: a launch is not a return, and an app that read
    * everything again on its way back read it all twice on the way in. */
  override def onResume(): Unit = {
    super.onResume()
    presence.shown()
  }

  /** Whether it has been out of sight since it was last in front, told once each way. */
  private val presence = new everyui.app.Presence(() => everyui.render.Platform.away(),
    () => everyui.render.Platform.resumed())

  /** Nobody can see it now — another app has the screen, or the device is going to sleep. Stop rather than pause,
    * which a picture-in-picture or a dialog over the top also brings, and neither means nobody is watching. */
  override def onStop(): Unit = {
    presence.hidden()
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
