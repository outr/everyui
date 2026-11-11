package everyui.tooltip

import everyui.component.*
import everyui.component.types.WhiteSpace
import everyui.focus.Focus
import everyui.render.Platform
import everyui.theme.Theme
import everyui.{Color, Shadow, ui}
import reactify.*

/** A tip beside a component, for as long as the pointer rests on it or the focus is drawn on it
  * (`Component.focusShown`: the keys brought it there): words, or anything the app makes (a key's chord beside its
  * name, a preview). It comes after `delay` seconds, so a pointer passing over does not raise it, and goes
  * as the pointer leaves, presses, or the focus moves on. It sits under the component, centred on it, or over it where
  * under has no room, and never off the screen. One shows at a time.
  *
  * On a platform driven by a remote it shows for the focus only where the app asks (`onFocus`): there the focus moves
  * with every press, and a tip following it would cover the next thing along. Where a pointer rests, the platform's
  * own plain tip is `Component.tooltip`.
  *
  * {{{
  *   Tooltip(saveButton, "Save (" + Chord("S", primary = true).label + ")")
  *   Tooltip.rich(fileRow)(() => new FilePreview(file))
  * }}} */
object Tooltip {
  /** How long the pointer rests, or the focus stays, before a tip shows, in seconds. */
  val delay: Var[Double] = Var(0.6)
  /** Whether the focus shows a tip as well as the pointer: not where a remote moves the focus, unless an app says. */
  val onFocus: Var[Boolean] = Var(!Platform.remoteNavigation())
  /** How wide words in a tip wrap at. */
  val widest: Var[Double] = Var(320.0)

  private var showing: Option[(Component, Container)] = None
  private var asked = 0
  // whose tip is still to come: asked for and not yet shown, none once it shows or is called off
  private var pending: Option[Component] = None

  /** `target`'s tip, the words `text` says as it shows. */
  def apply(target: Component, text: => String): Unit = rich(target)(() => words(text))

  /** `target`'s tip, made by `make` each time it shows. */
  def rich(target: Component)(make: () => Component): Unit = {
    val over = target.event.pointer.over   // following the pointer from now, before the focus asks
    target.event.pointer.enter.attach(_ => ask(target, make))
    target.event.pointer.leave.attach(_ => hideFor(target))
    target.event.pointer.down.attach(_ => hideFor(target))
    // the focus drawn, not merely there: a button a click or a screen focused raises no tip, a key arriving at it does
    target.focusShown.attach { on =>
      if (on && onFocus() && !over()) ask(target, make)
      else if (!on && !over()) hideFor(target)
    }
    target.onDispose(() => hideFor(target))
  }

  /** `target`'s tip asked for by what owns it rather than by its own pointer and focus (a list's row as the pointer or
    * the cursor rests on it): the words `text` gives, read as it shows, and nothing shown where they are None by then.
    * After `delay`, or at once with `now` (a finger held on it, which has waited already). */
  def askFor(target: Component, text: () => Option[String], now: Boolean = false): Unit = {
    asked += 1
    val mine = asked
    pending = Some(target)
    if (now) showIf(target, text, mine) else Platform.after(delay(), () => showIf(target, text, mine))
  }

  /** The tip showing, taken away, and any still to come, whoever's. */
  def hide(): Unit = {
    asked += 1
    pending = None
    showing.foreach(s => ui.children -= s._2)
    showing = None
  }

  /** Whether a tip is showing, and for what. */
  def shownFor: Option[Component] = showing.map(_._1)

  /** `target`'s tip taken away, the one showing or the one still to come for it, where it is `target`'s; another's,
    * showing or to come, is left as it is (a paragraph taken apart as a reply streams calls off none but its own). */
  def hideFor(target: Component): Unit = {
    if (pending.exists(_ eq target)) {
      asked += 1
      pending = None
    }
    if (showing.exists(_._1 eq target)) {
      showing.foreach(s => ui.children -= s._2)
      showing = None
    }
  }

  /** Whether a tip is still to come, and for what. */
  def pendingFor: Option[Component] = pending

  private def ask(target: Component, make: () => Component): Unit = {
    asked += 1
    val mine = asked
    pending = Some(target)
    Platform.after(delay(), () => if (mine == asked) {
      pending = None
      if (Focus.showing(target)) show(target, make())
    })
  }

  private def showIf(target: Component, text: () => Option[String], mine: Int): Unit = if (mine == asked) {
    pending = None
    if (Focus.showing(target)) text().foreach(t => show(target, words(t)))
  }

  private def show(target: Component, content: Component): Unit = {
    showing.foreach(s => ui.children -= s._2)
    val plate = new Container {
      size.width := content.size.width() + 20.0
      size.height := content.size.height() + 12.0
      backgroundColor := Theme.raised()
      borderWidth @= 1.0
      borderColor := Theme.border()
      radius @= 6.0
      shadow @= Some(Shadow(0.0, 3.0, 12.0, Color.Black.withAlpha(0.4)))
      content.position.left @= 10.0
      content.position.top @= 6.0
      children += content
    }
    val left = target.absoluteLeft
    val top = target.absoluteTop
    plate.position.left := math.max(8.0, math.min(left + (target.size.width() - plate.size.width()) / 2.0,
      ui.size.width() - plate.size.width() - 8.0))
    plate.position.top := {
      val under = top + target.size.height() + 6.0
      if (under + plate.size.height() <= ui.size.height() - 8.0) under else math.max(8.0, top - plate.size.height() - 6.0)
    }
    ui.children += plate
    showing = Some((target, plate))
  }

  /** Words as a tip: small, wrapping past `widest`, a path after its slashes where it can and a word longer than that
    * where it must. */
  private def words(text: String): Component = {
    val line = new TextView {
      font.size @= 12.5
      color := Theme.text()
      content @= text
    }
    if (line.size.width() <= widest()) line
    else new TextView {
      whiteSpace @= WhiteSpace.Normal
      wrapAnywhere @= true
      size.width @= widest()
      font.size @= 12.5
      color := Theme.text()
      content @= text.replace("/", "/\u200B")
    }
  }
}
