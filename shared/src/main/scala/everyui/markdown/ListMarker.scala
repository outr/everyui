package everyui.markdown

/** A list item's marker: `-`, `+` or `*`, or a number and `.` or `)` (`mark` is the character that says which list
  * it belongs to), where it stands (`indent`), and the column its content is indented to (`width`); `empty` when
  * nothing follows it on its line. */
private[markdown] case class ListMarker(ordered: Boolean, mark: String, start: Int, indent: Int, width: Int,
                                        empty: Boolean)
