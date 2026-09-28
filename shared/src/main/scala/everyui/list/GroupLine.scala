package everyui.list

/** One row of a `GroupedList`: a group's header (`item` empty), or one of its results. */
case class GroupLine[T](key: String, group: ResultGroup[T], item: Option[T], expanded: Boolean)
