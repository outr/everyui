package universal.app.screen

import universal.async.Task
import universal.component.Component
import universal.render.Platform
import universal.task.TaskSupport
import universal.{scribe, ui}

/** youi's `ScreenManager`, reduced to running one screen: load the app, then init, load and activate the screen,
  * then pump every frame's delta through the app, `ui` and the screen. */
trait ScreenManager extends TaskSupport:
  private var active: List[Screen] = Nil

  protected def load(): Task[Unit] = Task.unit

  /** When zero or more, step that many seconds on a virtual 60 Hz clock once the screen is up, log the component
    * tree, then stop — so a frame can be compared position for position (tools/check.sh). Negative runs live. The
    * platform supplies it: `?freeze=<seconds>` in the page URL, `pkg:/freeze.txt` in a Roku sideload, `--es freeze`
    * on the Android launch intent, `--freeze=` on the desktop. */
  protected def freezeSeconds: Double = Platform.freezeSeconds()

  def run(screen: Screen): Unit =
    load()
      .flatMap(_ => Screen.init(screen))
      .flatMap(_ => Screen.load(screen))
      .flatMap(_ => Screen.activate(screen))
      .map { _ =>
        active = List(screen)
        if freezeSeconds >= 0.0 then freeze() else live()
      }
      .startUnit()

  override def update(delta: Double): Unit =
    super.update(delta)
    ui.update(delta)
    active.foreach(_.update(delta))

  private def freeze(): Unit =
    val frame = 1.0 / 60.0
    var t = 0.0
    while t < freezeSeconds do
      val step = if freezeSeconds - t < frame then freezeSeconds - t else frame
      t = t + step
      update(step)
    scribe.info("frozen at " + freezeSeconds.toString + "s")
    dump(ui, "")
    Platform.frozen()

  /** Log the component tree with each node's position and size, for comparing a frame across backends. */
  def dump(component: Component, indent: String): Unit =
    scribe.info("tree " + indent + component.kind +
      " x=" + component.position.left().toString + " y=" + component.position.top().toString +
      " w=" + component.size.width().toString + " h=" + component.size.height().toString +
      " rot=" + component.rotation().toString)
    component.childComponents.foreach(child => dump(child, indent + "  "))

  private def live(): Unit =
    var last = -1.0
    var frames = 0.0
    var reported = Platform.ticks()
    Platform.animate(() =>
      val now = Platform.ticks()
      update(if last < 0.0 then 0.0 else (now - last) / 1000.0)
      last = now
      frames = frames + 1.0
      if now - reported >= 1000.0 then
        scribe.info("fps=" + (frames * 1000.0 / (now - reported)).toString)
        frames = 0.0
        reported = now
    )
