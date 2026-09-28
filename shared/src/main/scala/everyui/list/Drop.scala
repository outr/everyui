package everyui.list

import everyui.component.Component

/** Rows dropped, or about to be: the list or tree they came from (`from`), their keys, and where they would go, against
  * the row `target` (none in an empty list) at `position`. The target list's `accepts` decides whether it may land,
  * and its `dropped` hears it land. */
case class Drop(from: Component, keys: List[String], target: Option[String], position: DropPosition)
