package everyui.list

/** One row of a `RichList`: a section's header (`item` empty), or one of its items. `open` is whether the section is,
  * and `count` what its header says. */
case class RichLine[T](key: String, section: RichSection[T], item: Option[T], open: Boolean, count: Int)
