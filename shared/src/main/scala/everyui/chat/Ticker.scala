package everyui.chat

import everyui.component.{Component, OnScreen}
import everyui.render.Platform
import everyui.util.Clock
import reactify.*

/** The wall clock as a reactive value, moved on every `seconds`: what a live elapsed time and "5m ago" are bound to.
  *
  * With an `owner` (the transcript or list that shows the times), it moves on only while the owner is on the screen
  * (`OnScreen`) and the app is at the front (`Platform.onAway`): a view kept off the screen, in a tab not shown, hidden,
  * or with the app in the background, sets no timer and wakes nothing. The moment it is back it reads the clock at
  * once, so every time it shows is right as it shows, and moves on from there: one refresh however long it was gone,
  * never a catch-up. Without one it moves on whenever the app is at the front. `stop` ends it for good, as the owner
  * disposed of does. */
class Ticker(seconds: Double = 1.0, owner: Option[Component] = None) {
  val now: Var[Long] = Var(Ticker.clock())
  private var running = true
  // whether a tick is waiting: never more than one, however often the owner comes and goes
  private var waiting = false
  private val screen: Option[OnScreen] = owner.map(o => new OnScreen(o, on => seen(on)))

  /** Whether it moves on now: running, its owner (where it has one) on the screen, and the app at the front. */
  def ticking: Boolean = running && screen.forall(_()) && !Ticker.away

  private def tick(): Unit = {
    waiting = false
    if (ticking) {
      now @= Ticker.clock()
      schedule()
    }
  }

  private def schedule(): Unit = if (!waiting) {
    waiting = true
    Ticker.after(seconds, () => tick())
  }

  private def seen(on: Boolean): Unit = if (on) {
    Ticker.shown(this)
    back()
  }
  else Ticker.gone(this)

  // back on the screen, or the app at the front again: the clock read at once, and moving on from there
  private def back(): Unit = if (ticking) {
    now @= Ticker.clock()
    schedule()
  }

  /** Stopped for good: it never moves on again. */
  def stop(): Unit = {
    running = false
    screen.foreach(_.stop())
    Ticker.gone(this)
  }

  owner.foreach(_.onDispose(() => stop()))
  Ticker.listen()
  if (running && screen.forall(_())) Ticker.shown(this)
  if (ticking) schedule()
}

object Ticker {
  /** The wall clock (ms) and the timer the ticks wait on: the platform's, or a spec's own, moved on as it says. */
  private[everyui] var clock: () => Long = () => Clock.real()
  private[everyui] var after: (Double, () => Unit) => Unit = (s: Double, f: () => Unit) => Platform.after(s, f)

  /** A ticker for the times `owner` shows, moving on every `seconds` while it is on the screen. */
  def of(owner: Component, seconds: Double = 1.0): Ticker = new Ticker(seconds, Some(owner))

  // whether the app is away from the front, and the tickers on the screen, read again as it comes back: only those,
  // so nothing is held here that is not held on the screen already
  private var away = false
  private var listening = false
  private var onScreen: List[Ticker] = Nil

  private def listen(): Unit = if (!listening) {
    listening = true
    Platform.onAway(() => away = true)
    Platform.onResume(() => {
      away = false
      onScreen.foreach(_.back())
    })
  }

  private def shown(t: Ticker): Unit = if (!onScreen.exists(_ eq t)) onScreen = t :: onScreen
  private def gone(t: Ticker): Unit = onScreen = onScreen.filterNot(_ eq t)
}
