package everyui.list

/** Items dragged within a `RichList`: their keys, in the list's order, from section `from` into section `section`,
  * before the item `before` there (at the section's end where none). The list's `accepts` decides whether they may
  * go, and `moved` hears that they went: the app puts them there in its sections. */
case class RichMove(keys: List[String], from: String, section: String, before: Option[String])
