package everyui.list

import everyui.font.MaterialIcon

/** A group of a `RichList`'s items under a header that opens and closes them ("Unread", "Recent", a persona's name):
  * `key` names it, `title` heads it, and the header says `count` (how many it holds, unless given), with `icon` before
  * the title where it is a kind of group of its own (a workspace, a folder, a team). A section with no title has no
  * header, and its items are always shown. */
case class RichSection[T](key: String, title: String, items: Vector[T], count: Option[Int] = None,
                          icon: Option[MaterialIcon] = None)
