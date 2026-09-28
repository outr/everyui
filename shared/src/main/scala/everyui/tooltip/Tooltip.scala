package everyui.tooltip

import everyui.component.*
import everyui.component.types.WhiteSpace
import everyui.focus.Focus
import everyui.render.Platform
import everyui.theme.Theme
import everyui.{Color, Shadow, ui}
import reactify.*

/** A tip beside a component, for as long as the pointer rests on it or the focus is on it: words, or anything the app
  * makes (a key's chord beside its name, a preview). It comes after `delay` seconds, so a pointer passing over does
  * not raise it, and goes as the pointer leaves, presses, or the focus moves on. It sits under the component, centred
  * on it, or over it where under has no room, and never off the screen. One shows at a time.
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

  /** `target`'s tip, the words `text` says as it shows. */
  def apply(target: Component, text: => String): Unit = rich(target)(() => words(text))

  /** `target`'s tip, made by `make` each time it shows. */
  def rich(target: Component)(make: () => Component): Unit = {
    val over = target.event.pointer.over   // following the pointer from now, before the focus asks
    target.event.pointer.enter.attach(_ => ask(target, make))
    target.event.pointer.leave.attach(_ => hideFor(target))
    target.event.pointer.down.attach(_ => hideFor(target))
    target.focused.attach { on =>
      if (on && onFocus() && !over()) ask(target, make)
      else if (!on && !over()) hideFor(target)
    }
    target.onDispose(() => hideFor(target))
  }

  /** The tip showing, taken away. */
  def hide(): Unit = {
    asked += 1
    showing.foreach(s => ui.children -= s._2)
    showing = None
  }

  /** Whether a tip is showing, and for what. */
  def shownFor: Option[Component] = showing.map(_._1)

  private def hideFor(target: Component): Unit =
    if (showing.forall(_._1 eq target)) hide()

  private def ask(target: Component, make: () => Component): Unit = {
    asked += 1
    val mine = asked
    Platform.after(delay(), () => if (mine == asked && Focus.showing(target)) show(target, make()))
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

  /** Words as a tip: small, wrapping past `widest`. */
  private def words(text: String): Component = {
    val line = new TextView {
      font.size @= 12.5
      color := Theme.text()
      content @= text
    }
    if (line.size.width() <= widest()) line
    else new TextView {
      whiteSpace @= WhiteSpace.Normal
      size.width @= widest()
      font.size @= 12.5
      color := Theme.text()
      content @= text
    }
  }
}
