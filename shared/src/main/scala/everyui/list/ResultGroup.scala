package everyui.list

import everyui.font.MaterialIcon

/** A group of results under a header: a file's matches in a search. `key` names it, `title` heads it, with `icon`
  * before it where given; the header counts its items. */
case class ResultGroup[T](key: String, title: String, items: Vector[T], icon: Option[MaterialIcon] = None)
