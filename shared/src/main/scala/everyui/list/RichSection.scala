package everyui.list

/** A group of a `RichList`'s items under a header that opens and closes them ("Unread", "Recent", a persona's name):
  * `key` names it, `title` heads it, and the header says `count` (how many it holds, unless given). A section with no
  * title has no header, and its items are always shown. */
case class RichSection[T](key: String, title: String, items: Vector[T], count: Option[Int] = None)
