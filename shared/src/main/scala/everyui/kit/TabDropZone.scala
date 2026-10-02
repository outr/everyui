package everyui.kit

/** Where a tab dragged over a `TabGroups`' group would land: into it (among its tabs), or in a new group split off at
  * one of its edges: `Start` and `End` across, by the reading direction (on the right and the left in a right-to-left
  * language), `Top` and `Bottom` down. */
enum TabDropZone {
  case Into, Start, End, Top, Bottom
}
