package everyui.kit

import reactify.*

/** How a `TabGroups`' groups lie: one group, or a split of two parts, one beside the other (one over the other where
  * `vertical`), the first given `ratio` of the room, which the split's handle changes. */
enum TabTree[K] {
  case Leaf(group: TabGroup[K])
  case Split(vertical: Boolean, ratio: Var[Double], first: TabTree[K], second: TabTree[K])
}
