package everyui.app.screen

import everyui.async.Task
import everyui.component.{Backdrop, Component, Container, Treatment}
import everyui.task.*
import everyui.event.Key
import everyui.focus.Focus
import everyui.keymap.Keymap
import everyui.layout.LayoutPass
import everyui.net.URLPath
import everyui.render.Platform
import everyui.seo.SEO
import everyui.task.TaskSupport
import everyui.theme.Theme
import everyui.{scribe, ui}
import reactify.*

import scala.concurrent.duration.*

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
    b.fallbackSource := (active() match {
      case Some(screen: UIScreen) => screen.backgroundFallback().getOrElse("")
      case _ => ""
    })
    // where the picture sits as it is cropped follows the treatment as it blends from one screen's to the next
    b.alignX := shownTreatment().alignX
    b.alignY := shownTreatment().alignY
    b
  }

  /** What the active screen asks to have over the backdrop (`UIScreen.treatment`). */
  private lazy val wantedTreatment: Val[Treatment] = Val(active() match {
    case Some(screen: UIScreen) => screen.treatment()
    case _ => Treatment.none
  })
  /** How far the blend into the wanted treatment has got (0 to 1), and the treatment it is blending from: what was
    * showing when the wanted one last changed, part-blended itself if a change came mid-way. */
  private lazy val blended: Var[Double] = Var(1.0)
  private var blendFrom: Treatment = Treatment.none
  private var blending: Option[everyui.task.TaskInstance] = None
  /** The treatment being blended into: the wanted one, taken as it changes, so the blend is started before the
    * treatment shown hears of it. */
  private lazy val blendTo: Var[Treatment] = Var(Treatment.none)
  private lazy val shownTreatment: Val[Treatment] = Val(Treatment.lerp(blendFrom, blendTo(), blended()))

  /** Over the backdrop and under every screen, the washes the active screen's treatment lays over the artwork. A
    * change of treatment (another screen, or another theme's colours) moves across over
    * [[ScreenManager.treatmentSeconds]] rather than snapping: every wash and the picture's place blend together, as
    * the Flutter app's background does, instead of the washes jumping between two frames while the picture changes. */
  lazy val washes: Container = {
    val c = new Container
    c.size.width := ui.size.width
    c.size.height := ui.size.height
    // drawn once into a layer of its own, and again only while a blend changes it: every wash is the whole screen, and
    // drawing all three again under every frame of anything else was a good part of a television's GPU time per frame
    c.cached @= true
    // nothing to blend when what is showing is already what is wanted: an app with no treatments at all, say
    wantedTreatment.attach(next => if (blendTo() != next) {
      blendFrom = shownTreatment()
      blending.foreach(_.cancel())
      blended @= 0.0
      blendTo @= next
      blending = Some(blended.to(1.0).in(ScreenManager.treatmentSeconds)
        .easing(everyui.easing.Easing.cubicInOut).start())
    })
    shownTreatment.attach(t => lay(c, t))
    lay(c, shownTreatment())
    c
  }

  /** One layer the size of the screen for each wash, each drawn in its gradient. */
  private def lay(c: Container, t: Treatment): Unit = {
    if (c.children().size != t.washes.size) c.children @= t.washes.toVector.map { _ =>
      val layer = new Container
      layer.size.width := c.size.width()
      layer.size.height := c.size.height()
      layer
    }
    c.children().zip(t.washes).foreach { case (layer, wash) => layer.gradient @= Some(wash) }
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
    // with any already kept (`keep`), which an app may have made on its way to choosing the first
    screens = (screen :: others.toList) ++ screens.filterNot(s => (s eq screen) || others.exists(_ eq s))
    ScreenManager.running = Some(this)
    // everything on the page waits for it to have been painted once: what the server wrote into it (the page as plain
    // HTML) is on the screen first, rather than covered before it was ever drawn (`Platform.afterFirstPaint`)
    Platform.afterFirstPaint(() => start(screen))
  }

  private def start(screen: Screen): Unit = {
    // behind anything an app put in `ui` before running
    val before = ui.children()
    ui.children.clear()
    ui.children ++= List(backdrop, washes)
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
    // where the focus goes when it has nowhere else: the showing screen's own default
    Focus.home @= (() => active() match {
      case Some(u: UIScreen) => Some(u.defaultFocus())
      case _ => None
    })
    Focus.page @= (() => active() match {
      case Some(u: UIScreen) => Some(u.screenRoot)
      case _ => None
    })
    // the showing screen's shortcuts are in force while it shows
    Keymap.shown @= (() => active().map(_.keymap))
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

  /** Keep `screen` as the screens given to `run` are kept: built once, and answering its path from now on. For a screen
    * an app makes the first time it is wanted rather than at launch, where making every one of them up front is time
    * the first screen waits for. */
  protected def keep(screen: Screen): Unit = if (!screens.exists(_ eq screen)) screens = screens :+ screen

  /** The screen claiming `path`, if any. */
  def at(path: URLPath): Option[Screen] = screens.find {
    case p: PathActivation => p.matches(path)
    case _ => false
  }

  /** Show `screen`. With `push` (the default) the screen it replaces goes on the history and its path to the address
    * bar; without, it replaces the screen in place. */
  def show(screen: Screen, push: Boolean = true): Task[Unit] = {
    // whatever made `screen` is done making it: what is made from here on is not the screen's (UIScreen.made)
    everyui.component.Component.making @= None
    if (push) remember(screen)
    swap(screen, push)
  }

  /** Show the screen claiming `path`, as a link to it does: the path goes to the address bar first, so a screen with
    * parameters reads them from it. Whether a screen claimed it. */
  def go(path: String): Boolean = at(URLPath(path)) match {
    case Some(screen) =>
      everyui.component.Component.making @= None
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
    everyui.component.Component.making @= None
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
        // a screen made for one showing (a title's page) is let go once nothing can return to it, and taken apart a
        // moment later, once it is out of sight: left whole, whatever in it was bound to something that lives on (a
        // theme's colour, the host's size) held all of it, and a Roku ran out of memory a few dozen title pages later
        val kept = loaded.filter(s => (s eq screen) || screens.exists(_ eq s) || backStack.exists(_._1 eq s))
        val dropped = loaded.filterNot(s => kept.exists(_ eq s))
        loaded = kept
        dropped.filter(s => s.oneShowing).foreach(s => Platform.after(1.0, () =>
          if (!loaded.exists(_ eq s) && !active().exists(_ eq s) && !backStack.exists(_._1 eq s)) Screen.dispose(s)))
        screen match {
          case u: UIScreen =>
            val target = focus.filter(Focus.showing).getOrElse(u.defaultFocus())
            if (Focus.showing(target)) target.focus()
            else pendingFocus = Some(screen -> target)
          case _ => focus.filter(Focus.showing).foreach(_.focus())
        }
        // run with `focusAudit`, each screen names what no arrow can reach from where it starts
        if (Platform.launchArg("focusAudit").nonEmpty) Platform.after(2.0, () =>
          Focus.unreached(ui).foreach(line => Platform.log("[focus-audit] " + screen.getClass.getSimpleName + ": " + line)))
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

  /** Each frame as it went, for an app that reports where the time goes on a real device: `ms` from the start of the
    * frame before to the start of this one, and how much of that the app's own work took (the update: what changed,
    * laid out). The rest is the platform's, drawing it, and anything else the thread was held up by. Called on the
    * thread that draws, every frame, so what overrides it must be cheap. Nothing by default. */
  protected def frame(ms: Double, updateMs: Double): Unit = ()

  private def live(): Unit = {
    var last = -1.0
    var frames = 0.0
    var reported = Platform.ticks()
    var worked = 0.0
    Platform.animate(() => {
      val now = Platform.ticks()
      // the frame that has just ended, with the work it began with
      if (last >= 0.0) frame(now - last, worked)
      update(if (last < 0.0) 0.0 else (now - last) / 1000.0)
      worked = Platform.ticks() - now
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

  /** How long one screen's treatment of the backdrop takes to blend into the next's. */
  val treatmentSeconds: FiniteDuration = 400.millis
}
