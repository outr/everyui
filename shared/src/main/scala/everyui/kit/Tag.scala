package everyui.kit

import everyui.Color
import everyui.component.{Container, TextView}
import everyui.component.types.FontWeight
import everyui.theme.Theme
import reactify.*

/** A word in a small coloured pill: a branch or a tag on a commit, a status, a count. Solid, the pill is `colour` and
  * the word is white or near black, whichever reads on it; `soft`, the pill is a wash of `colour` with an edge of it and
  * the word in it. It takes no room while it has nothing to say, so a row of them closes up.
  *
  * {{{
  *   new Tag("main", Theme.accent())
  *   new Tag(ref.name, Tag.tone("success"), soft = true)
  *   val refs = new Container { layout @= Some(HorizontalLayout(4.0, middle = true)) }
  *   refs.children := commit().refs.map(r => new Tag(r, Tag.tone(r.kind), soft = true)).toVector
  * }}} */
class Tag(label: => String, colour: => Color, soft: Boolean = false, points: Double = 11.0) extends Container {
  private val words = new TextView {
    content := label
    font.size @= points
    font.weight @= FontWeight.SemiBold
    color := (if (soft) colour else Tag.ink(colour))
    position.left @= points * 0.6
    position.top @= points * 0.15
  }
  private def empty: Boolean = words.content().isEmpty

  backgroundColor := (if (soft) colour.withAlpha(0.16) else colour)
  borderWidth @= (if (soft) 1.0 else 0.0)
  borderColor := colour.withAlpha(0.5)
  radius := size.height() / 2.0
  size.width := (if (empty) 0.0 else words.size.width() + points * 1.2)
  size.height := (if (empty) 0.0 else words.size.height() + points * 0.3)
  children += words

  override def kind: String = "Tag(" + words.content() + ")"
}

object Tag {
  /** The word's colour on a solid `plate`: near black on a light one, white on a dark one. */
  def ink(plate: Color): Color =
    if (plate.red * 0.299 + plate.green * 0.587 + plate.blue * 0.114 > 0.6) Color.fromHex("1c1b1f") else Color.White

  /** A colour of the theme by its name: accent, success, warning, danger, info, muted, text, or an extra of the
    * palette's; the accent for any other. */
  def tone(name: String): Color = name match {
    case "success" => Theme.success()
    case "warning" => Theme.warning()
    case "danger" => Theme.danger()
    case "info" => Theme.info()
    case "muted" => Theme.muted()
    case "text" => Theme.text()
    case "accent" => Theme.accent()
    case other => Theme.extra(other, Theme.accent())
  }
}
