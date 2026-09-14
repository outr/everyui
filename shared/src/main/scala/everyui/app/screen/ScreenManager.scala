package everyui.app.screen

import everyui.async.Task
import everyui.component.Component
import everyui.net.URLPath
import everyui.render.Platform
import everyui.task.TaskSupport
import everyui.{scribe, ui}
import reactify.*

/** youi's `ScreenManager`: load the app, then show one screen at a time — each is init'd and loaded once, on its
  * first showing, and activated on every showing after the last one is deactivated. Every frame's delta goes
  * through the app, `ui` and the active screen.
  *
  * The address bar follows: showing a screen pushes its path, and the browser's back or forward shows the screen
  * that claims the new path. `run` starts on the screen claiming the page's path, or the first. */
trait ScreenManager extends TaskSupport:
  val active: Var[Option[Screen]] = Var(None)
  private var screens: List[Screen] = Nil
  private var loaded: List[Screen] = Nil

  protected def load(): Task[Unit] = Task.unit

  /** When zero or more, step that many seconds on a virtual 60 Hz clock once the screen is up, log the component
    * tree, then stop — so a frame can be compared position for position (tools/check.sh). Negative runs live. The
    * platform supplies it: `?freeze=<seconds>` in the page URL, `pkg:/freeze.txt` in a Roku sideload, `--es freeze`
    * on the Android launch intent, `--freeze=` on the desktop. */
  protected def freezeSeconds: Double = Platform.freezeSeconds()

  def run(screen: Screen, others: Screen*): Unit =
    screens = screen :: others.toList
    Platform.onPath(path => at(URLPath(path)).foreach(s => show(s, push = false).startUnit()))
    load()
      .flatMap(_ => show(at(URLPath(Platform.path())).getOrElse(screen), push = false))
      .map(_ => if freezeSeconds >= 0.0 then freeze() else live())
      .startUnit()

  /** The screen claiming `path`, if any. */
  def at(path: URLPath): Option[Screen] = screens.find {
    case p: PathActivation => p.matches(path)
    case _ => false
  }

  def show(screen: Screen, push: Boolean = true): Task[Unit] =
    active().filterNot(_ eq screen).map(Screen.deactivate).getOrElse(Task.unit)
      .flatMap { _ =>
        if loaded.exists(_ eq screen) then Task.unit
        else
          loaded = screen :: loaded
          Screen.init(screen).flatMap(_ => Screen.load(screen))
      }
      .flatMap(_ => Screen.activate(screen))
      .map { _ =>
        active @= Some(screen)
        screen match
          case p: PathActivation if push => Platform.pushPath(p.path.value)
          case _ => ()
      }

  override def update(delta: Double): Unit =
    super.update(delta)
    ui.update(delta)
    active().foreach(_.update(delta))

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
