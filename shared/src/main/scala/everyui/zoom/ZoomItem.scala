package everyui.zoom

import everyui.component.Component
import everyui.spatial.{Rect, Size}

/** One thing on a [[ZoomCanvas]]: a box at (`x`, `y`), `width` by `height`, and the component drawn in it, made by
  * `make` only once the box comes into view, and taken apart a while after it leaves (`ZoomCanvas.keep`).
  *
  * At the top of the canvas the box is in the world's units. A child's box (`children`) is in its parent's, 0 to 1
  * across and down, so a section's modules are placed as fractions of the section, their files of them, and so on as
  * deep as it goes; `children` is asked for once, the first time the item is wide enough to show them.
  *
  * Semantic zoom: the item is drawn while it is at least `from` pixels wide on the screen (and, with `until`, narrower
  * than that), and its children are made and drawn once it is `childrenFrom` pixels wide. A module drawn as a summary
  * until it is 400 pixels wide gives way to its files there: `until = Some(400.0), childrenFrom = 400.0`.
  *
  * With `scales` (the default) the component is part of the picture, drawn larger and smaller with the view, words
  * and all: it is laid out at the box's size in world units, or at `design` and scaled to fit the box, which keeps its
  * words at sizes their faces were made for however deep it sits. Without, it is laid out again at the size the box
  * has on the screen as the view moves, so what it says stays its own size (a label, a card that shows more as it
  * grows). Each keeps its place in the list: a child is drawn over its parent, a later item over an earlier one, and
  * those that do not scale over all of those that do.
  *
  * `hits` says whether a pointer finds it (`ZoomCanvas.hit`), rather than what it lies on.
  *
  * {{{
  *   ZoomItem("core", 0, 0, 800, 500, () => SectionCard("Core"), childrenFrom = 300.0,
  *     children = () => modules.zipWithIndex.map { case (m, i) =>
  *       ZoomItem(m.name, 0.05 + 0.3 * i, 0.2, 0.25, 0.7, () => ModuleCard(m), scales = false)
  *     })
  * }}} */
case class ZoomItem(key: String, x: Double, y: Double, width: Double, height: Double, make: () => Component,
                    children: () => Vector[ZoomItem] = () => Vector.empty, childrenFrom: Double = 0.0,
                    from: Double = 0.0, until: Option[Double] = None, scales: Boolean = true,
                    design: Option[Size] = None, hits: Boolean = true) {
  def box: Rect = Rect(x, y, width, height)
}
