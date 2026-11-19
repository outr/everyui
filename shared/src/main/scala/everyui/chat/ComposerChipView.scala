package everyui.chat

import everyui.component.*
import everyui.component.types.{Semantics, TextOverflow, WhiteSpace}
import everyui.font.MaterialIcon
import everyui.theme.Theme
import reactify.*

/** One of the app's chips (`ComposerChip`) in a [[Composer]]'s row of attachments, drawn as an `AttachmentChip` is:
  * its icon on a tile, its label, and a × where it can be taken off. The icon and the label are one button, named by
  * the label, that hands the chip to `onPress`; the × is "Remove" and the label. Changed by `chip`, in place: the
  * composer sets a chip of the same key here rather than making another. */
class ComposerChipView(first: ComposerChip, removeLabel: String, usable: => Boolean, points: Double = 13.0)
  extends Container {
  /** The chip it draws, changed in place. */
  val chip: Var[ComposerChip] = own(Var(first))

  private val side = points * 2.4
  private val glyph = new MaterialIconView(first.icon, side * 0.6)
  glyph.icon := chip().icon
  glyph.color := Theme.muted()
  private val tile = new Container {
    size.width @= side
    size.height @= side
    radius @= 6.0
    backgroundColor := Theme.control()
  }
  glyph.position.center := tile.size.center()
  glyph.position.middle := tile.size.middle()
  tile.children += glyph
  private val name = new TextView {
    content := chip().label
    font.size @= points
    color := Theme.text()
    maxWidth @= points * 12.0
    whiteSpace @= WhiteSpace.NoWrap
    textOverflow @= TextOverflow.Ellipsis
  }
  /** The icon and the label: a press on them is the app's (`ComposerChip.onPress`). */
  val body: Container = new Container {
    layout @= Some(everyui.layout.HorizontalLayout(points * 0.6, middle = true))
    padding @= 0.0
    radius @= 6.0
    focusable @= true
    tapSlop @= true
    cursor @= "pointer"
    backgroundColor := (if (focusShown() || event.pointer.over()) Theme.controlLit() else everyui.Color.Clear)
    semantics := Semantics.button(chip().label)
    tooltip := chip().tooltip.getOrElse(if (name.elided()) chip().label else "")
    children ++= List(tile, name)
  }
  body.event.click.attach { e =>
    e.consume()
    if (usable) chip().onPress(this)
  }

  // the ×, made while the chip can be taken off
  private var removing: Option[IconButton] = None
  /** What takes it off, where it can be. */
  def removal: Option[IconButton] = removing
  private def removable(on: Boolean): Unit =
    if (on && removing.isEmpty) {
      val b = new IconButton(MaterialIcon.Close, removeLabel + " " + chip().label,
        () => if (usable) chip().onRemove.foreach(_()))
      b.semantics := Semantics.button(removeLabel + " " + chip().label)
      b.tooltip := removeLabel + " " + chip().label
      removing = Some(b)
      children += b
    }
    else if (!on) removing.foreach { b =>
      removing = None
      children -= b
      b.dispose()
    }
  chip.attach(c => removable(c.onRemove.nonEmpty))

  padding @= 4.0
  layout @= Some(everyui.layout.HorizontalLayout(points * 0.6, middle = true))
  radius @= 10.0
  backgroundColor := Theme.surface()
  borderColor := Theme.border()
  borderWidth @= 1.0
  semantics @= Semantics.listItem
  children += body
  removable(first.onRemove.nonEmpty)

  override def kind: String = "ComposerChipView(" + first.key + ")"
}
