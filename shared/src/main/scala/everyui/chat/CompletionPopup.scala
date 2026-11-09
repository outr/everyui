package everyui.chat

import everyui.component.*
import everyui.component.types.Semantics
import everyui.theme.Theme
import everyui.{Color, Shadow}
import reactify.*

/** The list of completions a [[Composer]] offers for the word being typed, and a field for what is typed in it
  * (`everyui.text.CompletionSource`), `width` wide: each completion's label, its picture before it where it has one,
  * and its detail muted after it (from the right in a right-to-left language), the lit one (`lit`, which the keys move
  * with Up and Down) in the lit colour. With `rows` (more than 0) it shows that many at most and scrolls through the
  * rest, the lit one kept in view. It takes no focus: the keys stay in the field, and a press on it leaves the field's
  * caret (and a phone's keyboard) where they are. A pointer resting on a row lights it, and a click or a tap chooses
  * it (`chosen`). To a screen reader it is a list box of options, the lit one chosen. */
class CompletionPopup(val completions: Vector[Completion], val lit: Var[Int], width: => Double, points: Double = 14.0,
                      rows: Int = 0)
  extends Container {
  /** The index of the row clicked. */
  val chosen: Channel[Int] = Channel[Int]

  private val pad = points * 0.7
  private val lines: Vector[Container] = completions.zipWithIndex.map { case (c, i) => row(c, i) }
  // the rows in a scroll of their own, where there are more than it shows
  private val scroll: Option[ScrollContainer] =
    if (rows > 0 && completions.length > rows) Some(new ScrollContainer) else None
  // the lit row kept in view in it, as the keys move it
  private var revealing: Option[reactify.reaction.Reaction[Int]] = None

  private def row(c: Completion, i: Int): Container = {
    val label = new TextView {
      content @= c.label
      font.size @= points
      color := Theme.text()
    }
    val detail = new TextView {
      content @= c.detail.getOrElse("")
      font.size @= points * 0.85
      color := Theme.muted()
    }
    val line = new Container {
      size.width := CompletionPopup.this.size.width() - pad
      size.height := label.size.height() + points * 0.7
      radius @= points * 0.35
    }
    line.semantics := Semantics("option", label = c.label + c.detail.map(d => ", " + d).getOrElse(""),
      selected = Some(lit() == i))
    line.backgroundColor := (if (lit() == i) Theme.controlLit() else Color.Clear)
    // its picture, where it has one, before the label: from the right in a right-to-left language
    val picture = c.picture.map(source => new PictureSquare(source, points * 1.3, ""))
    val across = () => line.size.width()
    val start = picture.map(_ => pad * 2.0 + points * 1.3).getOrElse(pad)
    picture.foreach { p =>
      everyui.kit.Mirror.place(p, across, () => pad)
      p.position.middle := line.size.middle()
      line.children += p
    }
    everyui.kit.Mirror.place(label, across, () => start)
    label.position.middle := line.size.middle()
    // a completion is a line tall: cut short, and read whole in its tip
    label.textOverflow @= everyui.component.types.TextOverflow.Ellipsis
    detail.textOverflow @= everyui.component.types.TextOverflow.Ellipsis
    line.tooltip := (if (label.elided() || detail.elided()) (label.content() + "  " + detail.content()).trim else "")
    // the whole row's width where there is no detail beside it
    label.maxWidth := (if (c.detail.isEmpty) math.max(0.0, line.size.width() - start - pad)
      else line.size.width() * 0.6)
    everyui.kit.Mirror.place(detail, across, () => start + label.size.width() + pad)
    detail.position.middle := line.size.middle()
    detail.maxWidth := math.max(0.0, line.size.width() - start - label.size.width() - pad * 2.0)
    line.children ++= List(label, detail)
    line.event.pointer.enter.attach(_ => lit @= i)
    line.event.click.attach { e =>
      e.consume()
      chosen @= i
    }
    line
  }

  /** The row of the completion at `i`, where there is one. */
  def rowAt(i: Int): Option[Component] = if (i >= 0 && i < lines.length) Some(lines(i)) else None

  padding @= pad / 2.0
  layout @= Some(everyui.layout.VerticalLayout(0.0))
  size.width := width
  radius @= points * 0.6
  backgroundColor := Theme.raised()
  borderColor := Theme.border()
  borderWidth @= 1.0
  shadow := Some(Shadow(0.0, 4.0, 14.0, Theme.backdrop()))
  semantics @= Semantics("listbox")
  scroll match {
    case Some(s) =>
      val column = new Container {
        layout @= Some(everyui.layout.VerticalLayout(0.0))
      }
      column.children ++= lines
      s.content.children += column
      s.size.width := size.width() - pad
      s.size.height := lines.head.size.height() * rows
      children += s
      revealing = Some(lit.attach(i => rowAt(i).foreach(r => s.reveal(r))))
    case None => children ++= lines
  }
  node.holdFocusOnPress()
  // a press on it is its own, and lets no focus go as it ends: the field it was opened from keeps it (a finger's click
  // comes a turn after it is let go, by when a press that was nobody's would have taken the focus away)
  event.pointer.down.attach(_ => everyui.focus.Focus.dragging())

  override def dispose(): Unit = {
    revealing.foreach(r => lit.reactions -= r)
    revealing = None
    super.dispose()
  }

  override def kind: String = "CompletionPopup"
}
