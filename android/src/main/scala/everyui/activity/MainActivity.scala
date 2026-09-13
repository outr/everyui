package everyui.activity

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.{ViewGroup, WindowInsets, WindowInsetsController, WindowManager}
import everyui.render.{Host, NodeGroup}

/** The Android entry point: a full-screen window whose content view is the render root, then the shared `main`.
  *
  * tools/check.sh launches it with `--es freeze 0.434` to freeze a frame, and `--es viewport 1920x1080` to lay out at
  * that size whatever the screen — so a phone emulator produces the same frame as a 1080p TV (and shows its top-left
  * corner). */
class MainActivity extends Activity:
  override def onCreate(savedInstanceState: Bundle): Unit =
    super.onCreate(savedInstanceState)
    Host.activity = this
    Host.freeze = extra("freeze").flatMap(_.toDoubleOption).getOrElse(-1.0)
    extra("viewport").map(_.split('x')).foreach {
      case Array(w, h) =>
        Host.viewportWidth = w.toDouble
        Host.viewportHeight = h.toDouble
      case _ => ()
    }

    val root = new NodeGroup(this)
    // The render root is a viewport: content past its edges is clipped, as on every other backend.
    root.setClipChildren(true)
    root.setBackgroundColor(Color.WHITE)
    def extent(size: Double): Int = if size > 0.0 then size.toInt else ViewGroup.LayoutParams.MATCH_PARENT
    setContentView(root, new ViewGroup.LayoutParams(extent(Host.viewportWidth), extent(Host.viewportHeight)))
    Host.root = root

    val window = getWindow
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.setDecorFitsSystemWindows(false)
    val attributes = window.getAttributes
    attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    window.setAttributes(attributes)
    val insets = window.getInsetsController
    if insets != null then
      insets.hide(WindowInsets.Type.systemBars())
      insets.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)

    everyui.app.main()

  /** The shared layer's singletons (`ui`, the app) live as long as the process, as they live as long as a page or a
    * channel, so leaving the app ends the process and the next launch starts fresh. */
  override def onDestroy(): Unit =
    super.onDestroy()
    if isFinishing then android.os.Process.killProcess(android.os.Process.myPid())

  private def extra(name: String): Option[String] = Option(getIntent.getStringExtra(name))
