package everyui.component

import everyui.Color
import everyui.component.types.{Plain, Semantics, TextAlign, TextOverflow, WhiteSpace}
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
  val label: Var[String] = own(Plain[String](name))
  /** Whether it does anything. A disabled one is muted, does nothing, and says so to whatever reads the page (the web's
    * `aria-disabled`, `Component.disabled`); the focus still reaches it, so a focused button turned off leaves the
    * focus where it was, and a screen reader can still find it and say why. */
  val enabled: Var[Boolean] = own(Plain[Boolean](true))
  /** At least this wide, its content centred: a button as wide as the form it ends. */
  val minWidth: Var[Double] = own(Plain[Double](0.0))
  /** Extra space after each letter of the words, in pixels (`FontFeature.letterSpacing`): Material's labelLarge is
    * 0.1. */
  val letterSpacing: Var[Double] = own(Plain[Double](0.0))
  /** Its words slanted (`FontFeature.italic`). */
  val italic: Var[Boolean] = own(Plain[Boolean](false))
  /** How many lines its words may take in the room it has (`TextView.textOverflow`): 0, the default, wraps them onto as
    * many as they need where they pass it, the button as tall as they are (Matt's ruling of 2026-10-27: words wrap by
    * default); more wraps them onto up to that many, the last cut short; 1 keeps them on one line, cut short with an
    * ellipsis where they pass it, the default where the app cuts words by default (`TextOverflow.default` set to
    * `Ellipsis`). Words that fit are on one line, a pill, as ever; wrapped, the pill becomes a box with rounded
    * corners. */
  val labelLines: Var[Int] = own(Var(if (TextOverflow.default() == TextOverflow.Ellipsis) 1 else 0))
  /** Where wrapped words line up (`labelLines`): at the start, the reading direction's, as a paragraph and the
    * one-line choices beside it do; `Center` or `End` where a design wants them so. */
  val labelAlign: Var[TextAlign] = own(Plain[TextAlign](TextAlign.Start))
  /** Where its glyph and words sit across its width: `Center`, the default, in the middle; `Start` or `End` at that edge
    * of the reading direction, inside its padding (a full-width button as a rail's item, its icon and name at the
    * start). `labelAlign` lines the words up among themselves. */
  val contentAlign: Var[TextAlign] = own(Plain[TextAlign](TextAlign.Center))
  /** Whether what it does cannot be undone (Delete): its words, an outlined one's edge and a filled one's plate in the
    * danger colour rather than the accent. */
  val danger: Var[Boolean] = own(Plain[Boolean](false))
  /** At work (a save under way): a spinner turns in the glyph's place (before the words, where it has none), a press does
    * nothing until it is set back, and a screen reader hears that it is busy ("Save, busy"; the web's `aria-busy`). */
  val busy: Var[Boolean] = own(Plain[Boolean](false))

  // The focus is unmistakable from across a room: a filled button turns to the text colour, anything else gets a ring
  // in the accent over the lit fill. The pointer resting on it only lights it.
  private val lit: Val[Boolean] = own(Val(enabled() && (focusShown() || event.pointer.over())))
  private val ringed: Val[Boolean] = own(Val(focusShown() && style != ButtonStyle.Filled))
  private val foreground: Val[Color] = own(Val(
    if (!enabled()) Theme.muted()
    else if (style == ButtonStyle.Filled) Theme.onAccent()
    else if (danger()) Theme.dangerText()
    else if (style == ButtonStyle.Tonal) Theme.text()
    else if (lit() && style == ButtonStyle.Text) Theme.text()
    else Theme.accentText()))
  private val words: TextView = new TextView {
    content := label()
    font.size @= points
    color := foreground()
  }
  letterSpacing.attach(px => words.font.letterSpacing @= px)
  words.align := labelAlign()
  italic.attach(on => words.font.italic @= on)
  // its words on one line, cut; on up to that many; or on as many as they need
  private def lined(n: Int): Unit = {
    // (as many as they need: on one line as ever until they pass their room, then wrapped by the words themselves, so
    // words that fit are drawn exactly as they were)
    words.whiteSpace @= (if (n > 1) WhiteSpace.Normal else WhiteSpace.NoWrap)
    // one line asked for: cut short, as asked; as many as they need: wrapped, unless the app draws words whole
    words.textOverflow @=
      (if (n == 1) TextOverflow.Ellipsis
      else if (n == 0 && TextOverflow.default() != TextOverflow.Visible) TextOverflow.Wrap
      else TextOverflow.default())
    words.maxLines @= (if (n > 1) n else 0)
    words.measureWhole(n > 1)
  }
  lined(labelLines())
  labelLines.attach(lined)
  private val icon: Option[MaterialIconView] = glyph.map(g => new MaterialIconView(g, points * 1.4))
  icon.foreach(i => i.color := foreground())
  private val line: Container = icon match {
    case Some(i) => Row(points * 0.6, middle = true)(i, words)
    case None => Row(points * 0.6, middle = true)(words)
  }
  // the spinner while busy, in the glyph's place, made the first time it is busy (a turning glyph on every button would
  // be a motion on each): each takes no room while it is hidden, so the words move only as one takes the other's place
  private lazy val spinner: Component = {
    val s = new everyui.kit.Spinner(points * 1.3, () => foreground())
    s.visible := busy()
    s.collapses @= true
    icon.foreach { i =>
      i.visible := !busy()
      i.collapses @= true
    }
    line.children @= s +: line.children()
    s
  }
  busy.attach(b => if (b) spinner)

  // a button to whatever reads the page (the web's role, so a screen reader and a test runner find one), named by its
  // words, as IconButton is by its tip
  semantics := Semantics.button(if (busy()) label() + ", " + everyui.i18n.Words.busy else "")
  updating := busy()
  // out of use, as whatever reads the page hears it
  disabled := !enabled()
  focusable @= true
  size.width := math.max(minWidth(), line.size.width() + points * 2.2)
  size.height := line.size.height() + points
  // a pill on one line; wrapped, a box with the corners one line would have
  radius := (if (labelLines() == 1) size.height() / 2.0 else math.min(size.height() / 2.0, points * 1.2))
  // across its width as `contentAlign` says, the start the reading direction's, inside the padding its width allows
  line.position.left := {
    val pad = points * 1.1
    val room = size.width() - line.size.width()
    val atStart = contentAlign() == TextAlign.Start
    if (contentAlign() == TextAlign.Center) room / 2.0
    else if (atStart != everyui.i18n.Direction.rtl()) pad
    else room - pad
  }
  line.position.middle := size.middle()
  backgroundColor := {
    if (style == ButtonStyle.Filled)
      (if (!enabled()) Theme.control() else if (focusShown()) Theme.text()
        else if (danger()) (if (lit()) Theme.danger().withAlpha(0.85) else Theme.danger())
        else if (lit()) Theme.accentLit() else Theme.accent())
    else if (style == ButtonStyle.Tonal) (if (lit()) Theme.controlLit() else Theme.control())
    else if (lit()) Theme.controlLit()
    else Color.Clear
  }
  borderWidth := (if (ringed()) 2.5 else if (style == ButtonStyle.Outlined) 1.0 else 0.0)
  borderColor := (if (ringed()) Theme.accent() else if (danger() && style == ButtonStyle.Outlined) Theme.danger()
    else if (lit() && style == ButtonStyle.Outlined) Theme.accent() else Theme.edge())
  children += line
  tapSlop @= true
  event.click.attach { e =>
    e.consume()
    if (enabled() && !busy()) action()
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
