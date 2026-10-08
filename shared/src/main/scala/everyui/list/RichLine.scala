package everyui.list

/** One row of a `RichList`: a section's header (`item` empty), or one of its items; or, where `groupHead` is given, the
  * header of that group of sections (`section` its first); or, where `empty`, the line an open section that holds
  * nothing shows in place of its items (`RichSection.empty`), which the cursor passes over. `open` is whether the
  * section (or the group) is, `count` what its header says, and `nested` whether it sits in a group. `blockStart` and
  * `blockEnd` say whether it is the first and the last of its section's lines, where `RichStyle.Blocks` rounds its
  * block's corners (a group's header is in no block). */
case class RichLine[T](key: String, section: RichSection[T], item: Option[T], open: Boolean, count: Int,
                       groupHead: Option[RichGroup] = None, nested: Boolean = false, blockStart: Boolean = false,
                       blockEnd: Boolean = false, empty: Boolean = false) {
  /** Whether it is a header, of a section or a group. */
  def header: Boolean = item.isEmpty && !empty
}
