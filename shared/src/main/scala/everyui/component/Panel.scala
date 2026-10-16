package everyui.component

/** A header over a body that fills the rest: a filter over a tree, a toolbar over a list. Give the panel its size
  * (bound to where it sits); the body is as tall as the header leaves it, less the `spacing` between them and the
  * `padding` round both, and follows as the header grows (a filter wrapped to two lines, a taller field) or the panel
  * is resized, so the two never overlap. The body's width is its own, as in any column: bind it to the panel's.
  * {{{
  *   val panel = Panel(filter, tree, spacing = 8.0)
  *   panel.size.width := host.size.width()
  *   panel.size.height := host.size.height()
  *   tree.size.width := panel.size.width()
  * }}} */
object Panel {
  def apply(header: Component, body: Component, spacing: Double = 0.0, padding: Double = 0.0): Container =
    Column(spacing, padding)(header, Fill(body))
}
