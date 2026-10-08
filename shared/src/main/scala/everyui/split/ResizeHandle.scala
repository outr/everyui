package everyui.split

import everyui.render.Platform
import reactify.*

/** The edge of a part an app lays out by hand (a sidebar beside a tool rail and the main area), which a pointer or a
  * finger drags to make the part longer or shorter. `length` is the part's width (its height, at its top or bottom) in
  * pixels: a Var the app keeps and saves, and lays the part out by, so the part keeps its width as the window changes.
  * It is kept between `minLength` and `maxLength`. `edge` is the part's edge the handle is on, and dragged away from the
  * part it grows: a sidebar's `End` (on its left in a right-to-left language, where the drag is mirrored with it), a
  * panel at the end's `Start`, its `Bottom` or its `Top`.
  *
  * Where `collapsed` is given, a double-click folds the part away and back, as a drag most of the way past its minimum
  * folds it; the app hides the part while it is folded (and puts the handle at its edge), and a drag out from there
  * brings it back. Focusable where the app says (`focusable @= true`), the arrows along it move it by `step` and Enter
  * folds. It is a `SplitHandle`, as `SplitPane`'s is, so it is dragged the same way: lit with the pointer on it, the
  * resize cursor, followed over the whole screen. The app places it, its middle on the edge (from the right in a
  * right-to-left language: `Mirror.place`).
  *
  * {{{
  *   val width = Var(Platform.setting("sidebar").toDoubleOption.getOrElse(280.0))
  *   width.attach(w => Platform.saveSetting("sidebar", w.toString))
  *   val edge = new ResizeHandle(width, min = 180.0, max = 480.0, collapsed = Some(sidebarFolded))
  *   sidebar.size.width := width()
  *   edge.size.height := ui.size.height()
  *   Mirror.place(edge, () => ui.size.width(), () => rail.size.width() + (if (sidebarFolded()) 0.0 else width()) -
  *     edge.size.width() / 2.0)
  * }}} */
class ResizeHandle(val length: Var[Double], min: Double, max: Double = SplitPane.unbounded,
                   val edge: ResizeEdge = ResizeEdge.End, val collapsed: Option[Var[Boolean]] = None)
  extends SplitHandle(edge == ResizeEdge.Top || edge == ResizeEdge.Bottom) {
  /** The least and the most the part may be. */
  val minLength: Var[Double] = own(Var(min))
  val maxLength: Var[Double] = own(Var(max))
  /** How far the arrows move it, in pixels. */
  val step: Var[Double] = own(Var(24.0))
  // whether the part grows as the handle goes on (to the reading direction's end, or down) or back
  private val grows: Double = if (edge == ResizeEdge.End || edge == ResizeEdge.Bottom) 1.0 else -1.0
  private var from: Double = 0.0

  if (vertical) size.height @= ResizeHandle.thickness() else size.width @= ResizeHandle.thickness()
  pressed.attach(_ => from = if (folded) 0.0 else length())
  dragged.attach(delta => resizeTo(from + delta * grows))
  doubleClicked.attach(_ => toggle())
  nudged.attach(way => if (folded) toggle() else resizeTo(length() + way * step() * grows))
  entered.attach(_ => toggle())

  private def folded: Boolean = collapsed.exists(_())

  /** The part folded away, or back, where it is collapsible. */
  def toggle(): Unit = collapsed.foreach(c => c @= !c())

  /** The part made `to` long, as a drag makes it: kept to its minimum and its most, or, where it is collapsible and `to`
    * is under half its minimum, folded. */
  def resizeTo(to: Double): Unit = collapsed match {
    case Some(c) if to < minLength() / 2.0 => c @= true
    case _ =>
      collapsed.foreach(c => if (c()) c @= false)
      length @= math.max(minLength(), math.min(maxLength(), to))
  }

  override def kind: String = "ResizeHandle"
}

object ResizeHandle {
  /** How far across a handle reaches: 8 for a pointer, 20 for a finger. */
  def thickness(): Double = if (Platform.touchSupport() && !Platform.pointerSupport()) 20.0 else 8.0
}
