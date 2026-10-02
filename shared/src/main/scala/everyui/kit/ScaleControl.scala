package everyui.kit

import everyui.Color
import everyui.component.{Container, MaterialIconView, TextView}
import everyui.component.types.{SemanticRange, Semantics}
import everyui.event.{Key, KeyEvent}
import everyui.font.MaterialIcon
import everyui.i18n.{Direction, Words}
import everyui.list.ListRow
import everyui.theme.{Theme, UiScale}
import reactify.*

/** The size the app is drawn at (`UiScale`), turned down and up: −, the size as a percentage ("100%"), and +, a step at
  * a time through `UiScale.steps` (0.8 to 2.0 by tenths unless the app says). A click on − or + takes it a step, and
  * one on the percentage back to 100%; each end fades where the steps end.
  *
  * One stop for the focus, whose edge it lights: Left and Right step it (mirrored right to left, as the − and + are),
  * and - and + too; Enter, or OK on a remote, back to 100%; Up and Down move the focus on, so it sits in a column of
  * settings or a menu's panel. A screen reader hears it as a slider, `label` and the percentage.
  *
  * {{{
  *   settings.children += new ScaleControl(label = "Text size")
  * }}} */
class ScaleControl(label: String = "") extends Container {
  private val less = part(MaterialIcon.Remove, Words.smaller, () => UiScale.smaller(), () => UiScale() > UiScale.steps().min)
  private val more = part(MaterialIcon.Add, Words.larger, () => UiScale.larger(), () => UiScale() < UiScale.steps().max)
  private val middle = new Container {
    size.width @= ScaleControl.middle
    size.height @= ScaleControl.height
    radius @= 6.0
    tooltip @= Words.defaultSize
    backgroundColor := (if (event.pointer.over()) Theme.controlLit() else Color.Clear)
  }
  private val shown = TextView.bound(UiScale.percent(UiScale()), 14.0)
  shown.position.left := (ScaleControl.middle - shown.size.width()) / 2.0
  shown.position.top := (ScaleControl.height - shown.size.height()) / 2.0
  middle.children += shown
  middle.event.click.attach { e =>
    e.consume()
    UiScale.reset()
  }

  focusable @= true
  radius @= 8.0
  size.width @= ScaleControl.width
  size.height @= ScaleControl.height
  borderWidth := (if (focused()) 2.0 else 1.0)
  borderColor := (if (focused()) Theme.accent() else Theme.border())
  backgroundColor := (if (focused()) Theme.controlLit() else Theme.raised())
  semantics := Semantics.slider(label, SemanticRange(UiScale(), UiScale.steps().min, UiScale.steps().max,
    UiScale.percent(UiScale())))
  less.position.left := ListRow.along(0.0, ScaleControl.height, size.width())
  middle.position.left := ListRow.along(ScaleControl.height, ScaleControl.middle, size.width())
  more.position.left := ListRow.along(ScaleControl.height + ScaleControl.middle, ScaleControl.height, size.width())
  children ++= List(less, middle, more)
  // Enter, and OK on a remote, click it where it stands
  event.click.attach(_ => UiScale.reset())
  event.key.down.attach { e =>
    if (steps(e) > 0) UiScale.larger()
    else if (steps(e) < 0) UiScale.smaller()
  }

  // a step larger (1), smaller (-1), or none (0): the arrows along it, mirrored right to left, and - and + (= unshifted)
  private def steps(e: KeyEvent): Int = {
    val forward = if (Direction.rtl()) -1 else 1
    if (e.ctrl || e.meta || e.alt) 0
    else if (e.key == Key.ArrowRight) forward
    else if (e.key == Key.ArrowLeft) -forward
    else if (e.name == "+" || e.name == "=") 1
    else if (e.name == "-") -1
    else 0
  }

  override def keepsEvent(e: KeyEvent): Boolean = steps(e) != 0

  // − or +: its glyph, faded where `can` says it can go no further, with what it does as its tooltip
  private def part(glyph: MaterialIcon, tip: String, act: () => Unit, can: () => Boolean): Container = new Container {
    private val icon = new MaterialIconView(glyph, 20.0)
    icon.position.left @= (ScaleControl.height - 20.0) / 2.0
    icon.position.top @= (ScaleControl.height - 20.0) / 2.0
    icon.color := (if (can()) Theme.text() else Theme.muted())
    icon.opacity := (if (can()) 1.0 else 0.5)
    size.width @= ScaleControl.height
    size.height @= ScaleControl.height
    radius @= 6.0
    tooltip @= tip
    backgroundColor := (if (event.pointer.over() && can()) Theme.controlLit() else Color.Clear)
    children += icon
    tapSlop @= true
    event.click.attach { e =>
      e.consume()
      act()
    }
  }

  override def kind: String = "ScaleControl"
}

object ScaleControl {
  /** How tall it is, and how wide each of − and + is. */
  val height: Double = 36.0
  /** How wide the percentage is. */
  val middle: Double = 64.0
  val width: Double = height * 2.0 + middle
}
