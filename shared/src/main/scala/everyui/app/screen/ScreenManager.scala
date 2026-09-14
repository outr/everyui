package everyui.app.screen

import everyui.async.Task
import everyui.component.{Component, Container}
import everyui.event.Key
import everyui.focus.Focus
import everyui.net.URLPath
import everyui.render.Platform
import everyui.task.TaskSupport
import everyui.{scribe, ui}
import reactify.*

/** youi's `ScreenManager`: load the app, then show one screen at a time — each is init'd and loaded once, on its
  * first showing, and activated on every showing after the last one is deactivated. Every frame's delta goes
  * through the app, `ui` and the active screen.
  *
  * Showing a screen puts the one it replaces on the history, and Back (`Key.Back`: a remote's back button, a TV
  * browser's, a mouse's) returns to it, with the focus where it was; at the first screen Back is the platform's, which leaves the app. On the web
  * the browser's own history is that history: showing a screen pushes its path, Back is the browser's back, and the
  * browser's back or forward shows the screen that claims the new path. `run` starts on the screen claiming the page's
  * path, or the first. */
trait ScreenManager extends TaskSupport {
  val active: Var[Option[Screen]] = Var(None)
  /** Where the screens go: `ui` itself, or a region of it under an app bar. */
  def host: Container = ui
  private var screens: List[Screen] = Nil
  private var loaded: List[Screen] = Nil
  /** The screens Back returns to, the most recent first, each with what had the focus when it was left. */
  private var history: List[(Screen, Option[Component])] = Nil

  protected def load(): Task[Unit] = Task.unit

  /** When zero or more, step that many seconds on a virtual 60 Hz clock once the screen is up, log the component
    * tree, then stop — so a frame can be compared position for position (tools/check.sh). Negative runs live. The
    * platform supplies it: `?freeze=<seconds>` in the page URL, `pkg:/freeze.txt` in a Roku sideload, `--es freeze`
    * on the Android launch intent, `--freeze=` on the desktop. */
  protected def freezeSeconds: Double = Platform.freezeSeconds()

  /** Start on the screen claiming the page's path, or `screen`. Nothing is built until the bundled faces have
    * loaded, so every node is measured with its real font and never a fallback the browser is still replacing. */
  def run(screen: Screen, others: Screen*): Unit = {
    screens = screen :: others.toList
    Platform.onPath(path => at(URLPath(path)).foreach { s =>
      // the browser went back to the screen before this one, or forward past it
      history.headOption.filter(_._1 eq s) match {
        case Some((_, focus)) =>
          history = history.tail
          swap(s, push = false).map(_ => restore(focus)).startUnit()
        case None =>
          remember(s)
          swap(s, push = false).startUnit()
      }
    })
    Focus.onBack(key => key == Key.Back && back())
    Platform.whenFontsReady(() =>
      load()
        .flatMap(_ => show(at(URLPath(Platform.path())).getOrElse(screen), push = false))
        .map(_ => if (freezeSeconds >= 0.0) freeze() else live())
        .startUnit())
  }

  /** The screen claiming `path`, if any. */
  def at(path: URLPath): Option[Screen] = screens.find {
    case p: PathActivation => p.matches(path)
    case _ => false
  }

  /** Show `screen`. With `push` (the default) the screen it replaces goes on the history and its path to the address
    * bar; without, it replaces the screen in place. */
  def show(screen: Screen, push: Boolean = true): Task[Unit] = {
    if (push) remember(screen)
    swap(screen, push)
  }

  /** Back to the screen before this one: whether there was one (at the first, Back is left to the platform). */
  def back(): Boolean = history match {
    case (previous, focus) :: rest =>
      if (!Platform.historyBack()) {
        history = rest
        swap(previous, push = false).map(_ => restore(focus)).startUnit()
      }
      true
    case Nil => false
  }

  /** The screen being left for `next` goes on the history, with its focus. */
  private def remember(next: Screen): Unit =
    active().filterNot(_ eq next).foreach(current => history = (current, Focus.focused()) :: history)

  /** The focus back where it was, if that component is still on screen. */
  private def restore(focus: Option[Component]): Unit =
    focus.foreach(c => if (Focus.candidates(ui).exists(_ eq c)) c.focus())

  /** Forget the history: what is shown now is where Back leaves the app (signing in, say). */
  def clearHistory(): Unit = history = Nil

  private def swap(screen: Screen, push: Boolean): Task[Unit] =
    active().filterNot(_ eq screen).map(Screen.deactivate).getOrElse(Task.unit)
      .flatMap { _ =>
        if (loaded.exists(_ eq screen)) Task.unit
        else {
          loaded = screen :: loaded
          Screen.init(screen).flatMap(_ => Screen.load(screen))
        }
      }
      .flatMap(_ => Screen.activate(screen))
      .map { _ =>
        active @= Some(screen)
        screen match {
          case p: PathActivation if push => Platform.pushPath(p.path.value)
          case _ => ()
        }
      }

  override def update(delta: Double): Unit = {
    super.update(delta)
    everyui.util.Clock.update(delta)
    ui.update(delta)
    active().foreach(_.update(delta))
  }

  private def freeze(): Unit = {
    val frame = 1.0 / 60.0
    var t = 0.0
    while (t < freezeSeconds) {
      val step = if (freezeSeconds - t < frame) freezeSeconds - t else frame
      t = t + step
      update(step)
    }
    scribe.info("frozen at " + freezeSeconds.toString + "s")
    dump(ui, "")
    Platform.frozen()
  }

  /** Log the component tree with each node's position and size, for comparing a frame across backends. */
  def dump(component: Component, indent: String): Unit = {
    scribe.info("tree " + indent + component.kind +
      " x=" + component.position.left().toString + " y=" + component.position.top().toString +
      " w=" + component.size.width().toString + " h=" + component.size.height().toString +
      " rot=" + component.rotation().toString)
    component.childComponents.foreach(child => dump(child, indent + "  "))
  }

  private def live(): Unit = {
    var last = -1.0
    var frames = 0.0
    var reported = Platform.ticks()
    Platform.animate(() => {
      val now = Platform.ticks()
      update(if (last < 0.0) 0.0 else (now - last) / 1000.0)
      last = now
      frames = frames + 1.0
      if (now - reported >= 1000.0) {
        scribe.info("fps=" + (frames * 1000.0 / (now - reported)).toString)
        frames = 0.0
        reported = now
      }
    })
  }
}
