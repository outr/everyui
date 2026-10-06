package everyui.component

import everyui.Color
import everyui.component.types.{TextAlign, WhiteSpace}
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** A pill that does something: the pointer over it or the focus on it lights it; a click, a tap or Enter runs
  * `action` (and `event.click`). `style` says how much it stands out, `glyph` puts an icon before the words, and
  * `points` sizes the words, the padding following. The focus on it shows plainly: a filled one takes the text colour,
  * the rest a ring in the accent. A disabled one is muted and does nothing, but stays put. */
class Button(name: String, action: () => Unit = () => (), style: ButtonStyle = ButtonStyle.Tonal,
             glyph: Option[MaterialIcon] = None, points: Double = 13.0) extends Container {
  /** What it says; bind it to change it. */
  val label: Var[String] = own(Var(name))
  val enabled: Var[Boolean] = own(Var(true))
  /** At least this wide, its content centred: a button as wide as the form it ends. */
  val minWidth: Var[Double] = own(Var(0.0))
  /** Extra space after each letter of the words, in pixels (`FontFeature.letterSpacing`): Material's labelLarge is
    * 0.1. */
  val letterSpacing: Var[Double] = own(Var(0.0))
  /** Its words slanted (`FontFeature.italic`). */
  val italic: Var[Boolean] = own(Var(false))
  /** How many lines its words may take in the room it has (`TextView.textOverflow`): 1, the default, keeps them on one
    * line, cut short with an ellipsis where they pass it; more wraps them onto up to that many, the button as tall as
    * they are, the last cut short; 0 wraps them onto as many as they need. A choice someone must read whole before
    * picking it wraps; wrapped, the pill becomes a box with rounded corners. */
  val labelLines: Var[Int] = own(Var(1))
  /** Where wrapped words line up (`labelLines`): at the start, the reading direction's, as a paragraph and the
    * one-line choices beside it do; `Center` or `End` where a design wants them so. */
  val labelAlign: Var[TextAlign] = own(Var(TextAlign.Start))

  // The focus is unmistakable from across a room: a filled button turns to the text colour, anything else gets a ring
  // in the accent over the lit fill. The pointer resting on it only lights it.
  private val lit: Val[Boolean] = own(Val(enabled() && (focused() || event.pointer.over())))
  private val ringed: Val[Boolean] = own(Val(focused() && style != ButtonStyle.Filled))
  private val foreground: Val[Color] = own(Val(
    if (!enabled()) Theme.muted()
    else if (style == ButtonStyle.Filled) Theme.onAccent()
    else if (style == ButtonStyle.Tonal) Theme.text()
    else if (lit() && style == ButtonStyle.Text) Theme.text()
    else Theme.accent()))
  private val words: TextView = new TextView {
    content := label()
    font.size @= points
    color := foreground()
  }
  letterSpacing.attach(px => words.font.letterSpacing @= px)
  words.align := labelAlign()
  italic.attach(on => words.font.italic @= on)
  labelLines.attach { n =>
    words.whiteSpace @= (if (n == 1) WhiteSpace.NoWrap else WhiteSpace.Normal)
    words.maxLines @= (if (n > 1) n else 0)
    words.measureWhole(n != 1)
  }
  private val icon: Option[MaterialIconView] = glyph.map(g => new MaterialIconView(g, points * 1.4))
  icon.foreach(i => i.color := foreground())
  private val line: Container = icon match {
    case Some(i) => Row(points * 0.6, middle = true)(i, words)
    case None => Row(points * 0.6, middle = true)(words)
  }

  focusable @= true
  size.width := math.max(minWidth(), line.size.width() + points * 2.2)
  size.height := line.size.height() + points
  // a pill on one line; wrapped, a box with the corners one line would have
  radius := (if (labelLines() == 1) size.height() / 2.0 else math.min(size.height() / 2.0, points * 1.2))
  line.position.center := size.center()
  line.position.middle := size.middle()
  backgroundColor := {
    if (style == ButtonStyle.Filled)
      (if (!enabled()) Theme.control() else if (focused()) Theme.text() else if (lit()) Theme.accentLit() else Theme.accent())
    else if (style == ButtonStyle.Tonal) (if (lit()) Theme.controlLit() else Theme.control())
    else if (lit()) Theme.controlLit()
    else Color.Clear
  }
  borderWidth := (if (ringed()) 2.5 else if (style == ButtonStyle.Outlined) 1.0 else 0.0)
  borderColor := (if (ringed() || (lit() && style == ButtonStyle.Outlined)) Theme.accent() else Theme.border())
  children += line
  tapSlop @= true
  event.click.attach { e =>
    e.consume()
    if (enabled()) action()
  }
}

object Button {
  /** A button whose words follow what they read: a translation (`Tr.signIn`), a count. */
  def bound(words: => String, action: () => Unit, style: ButtonStyle = ButtonStyle.Tonal, glyph: Option[MaterialIcon] = None,
            points: Double = 13.0): Button = {
    val button = new Button("", action, style, glyph, points)
    button.label := words
    button
  }
}
