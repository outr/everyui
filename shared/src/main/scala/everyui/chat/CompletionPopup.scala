package everyui.chat

import everyui.component.*
import everyui.component.types.Semantics
import everyui.theme.Theme
import everyui.{Color, Shadow}
import reactify.*

/** The list a [[Composer]] offers for the word being typed, `width` wide: each completion's label, and its detail
  * muted after it, the lit one (`lit`, which the composer moves with Up and Down) in the lit colour. It takes no focus:
  * the keys stay in the composer's field. A pointer resting on a row lights it, and a click chooses it (`chosen`). */
class CompletionPopup(val completions: Vector[Completion], val lit: Var[Int], width: => Double, points: Double = 14.0)
  extends Container {
  /** The index of the row clicked. */
  val chosen: Channel[Int] = Channel[Int]

  private val pad = points * 0.7
  private val rows: Vector[Container] = completions.zipWithIndex.map { case (c, i) => row(c, i) }

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
      semantics @= Semantics("option", label = c.label + c.detail.map(d => ", " + d).getOrElse(""))
    }
    line.backgroundColor := (if (lit() == i) Theme.controlLit() else Color.Clear)
    label.position.left @= pad
    label.position.middle := line.size.middle()
    // a completion is a line tall: cut short, and read whole in its tip
    label.textOverflow @= everyui.component.types.TextOverflow.Ellipsis
    detail.textOverflow @= everyui.component.types.TextOverflow.Ellipsis
    line.tooltip := (if (label.elided() || detail.elided()) (label.content() + "  " + detail.content()).trim else "")
    label.maxWidth := line.size.width() * 0.6
    detail.position.left := label.position.left() + label.size.width() + pad
    detail.position.middle := line.size.middle()
    detail.maxWidth := math.max(0.0, line.size.width() - detail.position.left() - pad)
    line.children ++= List(label, detail)
    line.event.pointer.enter.attach(_ => lit @= i)
    line.event.click.attach { e =>
      e.consume()
      chosen @= i
    }
    line
  }

  padding @= pad / 2.0
  layout @= Some(everyui.layout.VerticalLayout(0.0))
  size.width := width
  radius @= points * 0.6
  backgroundColor := Theme.raised()
  borderColor := Theme.border()
  borderWidth @= 1.0
  shadow := Some(Shadow(0.0, 4.0, 14.0, Theme.backdrop()))
  semantics @= Semantics("listbox")
  children ++= rows

  override def kind: String = "CompletionPopup"
}
