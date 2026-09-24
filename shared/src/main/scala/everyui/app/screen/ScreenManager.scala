package everyui.app.screen

import everyui.async.Task
import everyui.component.{Backdrop, Component, Container}
import everyui.event.Key
import everyui.focus.Focus
import everyui.layout.LayoutPass
import everyui.net.URLPath
import everyui.render.Platform
import everyui.seo.SEO
import everyui.task.TaskSupport
import everyui.theme.Theme
import everyui.{scribe, ui}
import reactify.*

/** youi's `ScreenManager`: load the app, then show one screen at a time — each is init'd and loaded once, on its
  * first showing, and activated on every showing after the last one is deactivated. Every frame's delta goes
  * through the app, `ui` and the active screen.
  *
  * Showing a screen puts the one it replaces on the history, and Back (`Key.Back`: a remote's back button, a TV
  * browser's, a mouse's) returns to it, with the focus where it was (a screen shown afresh starts at its
  * `defaultFocus`); at the first screen Back is the platform's, which leaves the app. On the web
  * the browser's own history is that history: showing a screen pushes its path, Back is the browser's back, and the
  * browser's back or forward shows the screen that claims the new path. `run` starts on the screen claiming the page's
  * path, or the first. */
trait ScreenManager extends TaskSupport {
  val active: Var[Option[Screen]] = Var(None)
  /** What the shown screen says about itself (`Screen.seo`), followed as it changes: the screen changing, or what the
    * screen built it from arriving. */
  private lazy val shownSEO: Val[Option[SEO]] = Val(active().flatMap(Screen.seo))
  /** Where the screens go: `ui` itself, or a region of it under an app bar. */
  def host: Container = ui
  /** Behind everything, the whole size of `ui`: the active screen's own `background`, else the theme's, so no screen
    * shows an empty ground. */
  lazy val backdrop: Backdrop = {
    val b = new Backdrop
    b.size.width := ui.size.width
    b.size.height := ui.size.height
    b.source := (active() match {
      case Some(screen: UIScreen) => screen.background().getOrElse(Theme.backgroundImage())
      case _ => Theme.backgroundImage()
    })
    b
  }
  private var screens: List[Screen] = Nil
  private var loaded: List[Screen] = Nil
  /** The screens Back returns to, the most recent first, each with what had the focus when it was left. */
  private var backStack: List[(Screen, Option[Component])] = Nil
  /** A screen's default focus not on screen yet (behind a loading state): it takes the focus when it appears. */
  private var pendingFocus: Option[(Screen, Component)] = None

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
    ScreenManager.running = Some(this)
    // behind anything an app put in `ui` before running
    val before = ui.children()
    ui.children.clear()
    ui.children += backdrop
    ui.children ++= before
    Platform.onPath(path => at(URLPath(path)).foreach { s =>
      // the browser went back to the screen before this one, or forward past it
      backStack.headOption.filter(_._1 eq s) match {
        case Some((_, focus)) =>
          backStack = backStack.tail
          swap(s, push = false, focus).startUnit()
        case None =>
          remember(s)
          swap(s, push = false).startUnit()
      }
    })
    Focus.onBack(key => key == Key.Back && back())
    // the page's head follows the shown screen's SEO; a screen with none leaves it as it is
    shownSEO.attachAndFire(_.foreach(Platform.setSEO))
    Platform.whenFontsReady(() =>
      load()
        .flatMap(_ => show(at(URLPath(Platform.path())).getOrElse(screen), push = false))
        .map { _ =>
          // drawn: whatever the page held in its place until now (a server's plain HTML of it) goes
          Platform.drawn()
          if (freezeSeconds >= 0.0) freeze() else live()
        }
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

  /** Show the screen claiming `path`, as a link to it does: the path goes to the address bar first, so a screen with
    * parameters reads them from it. Whether a screen claimed it. */
  def go(path: String): Boolean = at(URLPath(path)) match {
    case Some(screen) =>
      remember(screen)
      Platform.pushPath(path)
      swap(screen, push = false).startUnit()
      true
    case None => false
  }

  /** Back to the screen before this one: whether there was one (at the first, Back is left to the platform). */
  def back(): Boolean = backStack match {
    case (previous, focus) :: rest =>
      if (!Platform.historyBack()) {
        backStack = rest
        swap(previous, push = false, focus).startUnit()
      }
      true
    case Nil => false
  }

  /** The screen being left for `next` goes on the history, with its focus. */
  private def remember(next: Screen): Unit =
    active().filterNot(_ eq next).foreach(current => backStack = (current, Focus.focused()) :: backStack)

  /** Show `screen` with only `under` behind it, whatever came before: Back returns to `under`, at its default focus. A
    * section of an app's shell, which Back leaves for the shell's first screen. `under` itself is shown with nothing
    * behind it. */
  def showOver(screen: Screen, under: Screen): Task[Unit] = {
    backStack = if (screen eq under) Nil else List((under, None))
    swap(screen, push = true)
  }

  /** Forget the history: what is shown now is where Back leaves the app (signing in, say). */
  def clearHistory(): Unit = backStack = Nil

  /** Show `screen`, then focus `focus` if it is still on screen, else the screen's default: on a turn of its own
    * (`Platform.later`), since a change is asked for at the end of a chain (a key, the focus, a click, an action, a
    * task) whose nesting a Roku's small stack cannot also carry a screen's build under. */
  private def swap(screen: Screen, push: Boolean, focus: Option[Component] = None): Task[Unit] =
    Task.async[Unit](done => Platform.later(() => change(screen, push, focus).map(_ => done(())).startUnit()))

  private def change(screen: Screen, push: Boolean, focus: Option[Component]): Task[Unit] = {
    val started = Platform.ticks()
    // the leaving, the building and the arriving in one layout pass, before the focus looks for where things are
    LayoutPass.batched(
      active().filterNot(_ eq screen).map(Screen.deactivate).getOrElse(Task.unit)
        .flatMap { _ =>
          if (loaded.exists(_ eq screen)) Task.unit
          else {
            loaded = screen :: loaded
            Screen.init(screen).flatMap(_ => Screen.load(screen))
          }
        }
        .flatMap(_ => Screen.activate(screen)))
      .map { _ =>
        active @= Some(screen)
        // a screen made for one showing (a title's page) is let go once nothing can return to it
        loaded = loaded.filter(s => (s eq screen) || screens.exists(_ eq s) || backStack.exists(_._1 eq s))
        screen match {
          case u: UIScreen =>
            val target = focus.filter(Focus.showing).getOrElse(u.defaultFocus())
            if (Focus.showing(target)) target.focus()
            else pendingFocus = Some(screen -> target)
          case _ => focus.filter(Focus.showing).foreach(_.focus())
        }
        screen match {
          case p: PathActivation if push => Platform.pushPath(p.path.value)
          case _ => ()
        }
        // how long the screen took to leave the last, build (the first time), lay out and arrive; a slow one is
        // logged loud enough to forward, so a sluggish page in the wild shows up centrally and not only here
        val ms = Platform.ticks() - started
        val note = "screen " + screen.getClass.getSimpleName + " shown in " + ms.toInt.toString + "ms"
        if (ms >= 1200.0) scribe.warn(note) else scribe.info(note)
      }
  }

  override def update(delta: Double): Unit = {
    super.update(delta)
    everyui.util.Clock.update(delta)
    ui.update(delta)
    pendingFocus.foreach { (screen, target) =>
      if (!active().exists(_ eq screen)) pendingFocus = None
      else if (Focus.showing(target)) {
        // unless the person has already put the focus somewhere on this screen
        if (Focus.focused().forall(f => !Focus.showing(f))) target.focus()
        pendingFocus = None
      }
    }
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

object ScreenManager {
  /** The app's screen manager, once it runs: where a [[everyui.component.Link]] to a path looks for its screen. */
  var running: Option[ScreenManager] = None

  /** Show `path`'s screen in the running app. Whether there was one. */
  def navigate(path: String): Boolean = running.exists(_.go(path))
}
