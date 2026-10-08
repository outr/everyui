package everyui.list

import everyui.font.MaterialIcon

/** A group of a `RichList`'s items under a header that opens and closes them ("Unread", "Recent", a persona's name):
  * `key` names it, `title` heads it, and the header says `count` (how many it holds, unless given), with `icon` before
  * the title where it is a kind of group of its own (a workspace, a folder, a team). A section with no title has no
  * header, and its items are always shown. Sections next to each other in the same `group` go under its header, a level
  * above theirs; one in none is drawn as it always was. `action` puts a small button in its header that does a thing
  * of its own ("New conversation"), and `empty` is the line it shows, muted, while it is open and holds nothing ("No
  * conversations yet"); without it, an empty section is its header alone. */
case class RichSection[T](key: String, title: String, items: Vector[T], count: Option[Int] = None,
                          icon: Option[MaterialIcon] = None, group: Option[RichGroup] = None,
                          action: Option[RichAction] = None, empty: Option[String] = None)
