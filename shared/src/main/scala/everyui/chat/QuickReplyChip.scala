package everyui.chat

import everyui.component.{Container, TextView}
import everyui.component.types.{FontWeight, Semantics, TextDirection}
import everyui.event.{Key, KeyEvent}
import everyui.theme.Theme
import reactify.*

/** One answer under a message (`QuickReply`): a pill in the theme's accent, edged and lightly washed, its words in the
  * size of a message's. Pressed it is filled with the accent; `dimmed` (another of its group pressed), it is drawn
  * faint but still answers. A click, a tap, Enter or Space is `press`. `tall` makes it a thumb's height (a touch screen,
  * a remote), and its words wrap within `widest`. */
class QuickReplyChip(val reply: QuickReply, tall: Boolean, widest: => Double, press: () => Unit) extends Container {
  /** Whether it is the answer given: filled. */
  val pressed: Var[Boolean] = own(Var(false))
  /** Whether another of its group is the answer given: faint, still pressable. */
  val dimmed: Var[Boolean] = own(Var(false))

  private val lit: Val[Boolean] = own(Val(focusShown() || event.pointer.hover()))
  private val words: TextView = new TextView {
    content @= reply.label
    direction @= TextDirection.Auto
    font.size @= QuickReplyChip.points
    font.weight @= FontWeight.Medium
    color := (if (pressed()) Theme.onAccent() else Theme.accentText())
    maxWidth := math.max(QuickReplyChip.padX, widest - QuickReplyChip.padX * 2.0)
  }
  private val least: Double = if (tall) QuickReplyChip.tallHeight else QuickReplyChip.height

  size.width := words.size.width() + QuickReplyChip.padX * 2.0
  size.height := math.max(least, words.size.height() + QuickReplyChip.padY * 2.0)
  // a pill on one line; wrapped, a box with the corners one line would have
  radius := math.min(size.height() / 2.0, least / 2.0)
  words.position.center := size.center()
  words.position.middle := size.middle()
  backgroundColor := (if (pressed()) (if (lit()) Theme.accentLit() else Theme.accent())
    else Theme.accent().withAlpha(if (lit()) 0.2 else 0.08))
  // the focus a ring: the text colour round a filled one, the accent's full strength round the rest
  borderWidth := (if (focusShown()) 2.5 else 1.0)
  borderColor := (if (focusShown() && pressed()) Theme.text() else if (focusShown() || pressed()) Theme.accent()
    else Theme.accent().withAlpha(0.6))
  opacity := (if (dimmed() && !pressed()) QuickReplyChip.faint else 1.0)
  semantics := Semantics.toggle(QuickReply.said(reply), pressed())
  focusable @= true
  tapSlop @= true
  cursor @= "pointer"
  children += words
  event.click.attach { e =>
    e.consume()
    press()
  }
  event.key.down.attach(e => if (e.key == Key.Space) press())

  // Space presses, as Enter does; Escape and Back are its row's, back to the message
  override def keepsEvent(e: KeyEvent): Boolean =
    e.key == Key.Space || e.key == Key.Escape || e.key == Key.Back || super.keepsEvent(e)

  override def kind: String = "QuickReplyChip"
}

object QuickReplyChip {
  /** Its words' size: a message's. */
  val points: Double = 14.0
  /** How tall it is at least: with a mouse, and for a thumb or across a room. */
  val height: Double = 32.0
  val tallHeight: Double = 40.0
  val padX: Double = 16.0
  val padY: Double = 6.0
  /** How opaque a chip is while another of its group is pressed. */
  val faint: Double = 0.5
}
