package everyui.component

import everyui.Color
import everyui.component.types.Semantics
import everyui.font.MaterialIcon
import everyui.i18n.Direction
import everyui.theme.Theme
import reactify.*

/** A glyph that does something, with what it does as its tooltip. While `busy` (its work under way: a queue running)
  * the glyph breathes (`MaterialIconView.busy`), or, where less motion is asked for, stands still in the accent; its
  * tooltip and its name to a screen reader say it is busy. A `badge` hangs over its corner: a count ("3", "9+") in a
  * pill, or, given "", a dot; it is part of the button, so the pointer over it is over the button, a click on it
  * presses it, and a screen reader hears one control, the count after its name ("Queue, 3"). */
class IconButton(glyph: MaterialIcon, tip: String, action: () => Unit = () => (), colour: Option[Color] = None) extends Container {
  /** The glyph's colour at rest: the theme's muted colour, following the theme, unless `colour` names one. */
  val tint: Var[Color] = own(Var(Theme.muted()))
  colour.foreach(c => tint @= c)
  val icon: MaterialIconView = new MaterialIconView(glyph, 22.0) {
    position.left @= 5.0
    position.top @= 5.0
    // still and busy, it is marked by the accent, at full strength to be seen
    override protected def stillStrength: Double = 1.0
  }
  /** At work: the glyph breathes until it is set back. */
  val busy: Var[Boolean] = icon.busy
  focusable @= true
  radius @= 16.0
  /** What hangs over its top corner at the reading direction's end: a count in a pill, "" a dot, None
    * nothing. */
  val badge: Var[Option[String]] = own(Var(None))
  /** The badge's colour: the accent unless the app says (the theme's danger for something gone wrong). Its words
    * are the theme's on the accent where they read on it, else white or black, whichever does. */
  val badgeColour: Var[Color] = own(Var(Theme.accent()))
  private def said: String = {
    val named = if (busy()) tip + ", " + everyui.i18n.Words.busy else tip
    badge().filter(_.nonEmpty).map(n => named + ", " + n).getOrElse(named)
  }
  tooltip := said
  // a glyph says nothing to a screen reader: what it does does
  semantics := Semantics.button(said)
  size.width @= 32.0
  size.height @= 32.0
  // its own hover, lighter than the card it may sit on, and the glyph brightens with it
  backgroundColor := (if (focusShown() || event.pointer.over()) Theme.controlLit() else Color.Clear)
  icon.color := (if (busy() && everyui.motion.ReduceMotion()) Theme.accent()
    else if (focusShown() || event.pointer.over()) Theme.text() else tint())
  children += icon
  // the badge, made the first time there is one, hanging past the button's box (which then draws it whole), the
  // pointer and clicks on it the button's as any child's are
  private lazy val pill: Container = {
    val words = new TextView {
      font.size @= 10.0
      font.weight @= everyui.component.types.FontWeight.Bold
      color := {
        // the theme's words on the accent where they read on the badge's colour, else white or black, whichever does
        val fill = badgeColour()
        val on = Theme.onAccent()
        if (everyui.access.Access.contrast(on, fill) >= 4.5) on else Theme.wordsOn(fill)
      }
      // drawn whole in the pill, which hangs past the button's box: not kept to that box, which left it a pixel
      textOverflow @= everyui.component.types.TextOverflow.Visible
      content := badge().getOrElse("")
    }
    val p = new Container {
      private def dot: Boolean = badge().exists(_.isEmpty)
      size.height := (if (dot) 8.0 else 16.0)
      size.width := (if (dot) 8.0 else math.max(16.0, words.size.width() + 8.0))
      radius := size.height() / 2.0
      backgroundColor := badgeColour()
      visible := badge().nonEmpty
      words.visible := !dot
      words.position.left := (size.width() - words.size.width()) / 2.0
      words.position.top := (size.height() - words.size.height()) / 2.0
      children += words
    }
    p.position.top := (if (badge().exists(_.isEmpty)) 1.0 else -4.0)
    p.position.left := {
      val inset = if (badge().exists(_.isEmpty)) 9.0 else p.size.width() - 6.0
      // over the top corner at the reading direction's end: the right, or the left right to left
      if (Direction.rtl()) inset - p.size.width() else size.width() - inset
    }
    clipsToBox @= Some(false)
    children += p
    p
  }
  badge.attach(b => if (b.nonEmpty) pill)
  tapSlop @= true
  event.click.attach { e =>
    e.consume()
    action()
  }
}
