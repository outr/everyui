package everyui.list

import everyui.component.Component

/** A row of a `LazyColumn`: `key` names it (a row stays made while an item of its key is among the items), `estimate`
  * is how tall it is taken to be until it is made and has a height of its own, and `build` makes it, as it comes near
  * the view (and again, where it was let go far from it: `LazyColumn.releaseBeyond`). */
case class LazyItem(key: String, estimate: Double, build: () => Component)
