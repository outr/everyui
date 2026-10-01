package everyui.component.types

/** Where words too wide for their room are cut short with an ellipsis: at the end ("AppConnect…"), the default; at the
  * start ("…/backend/app +39 −7"), for a path or anything whose end says the most; or in the middle
  * ("backend/sr…/app +39 −7"), keeping both. */
enum Elide {
  case Start, Middle, End
}
