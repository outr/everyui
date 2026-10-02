package everyui.list

import everyui.font.MaterialIcon

/** A level above a `RichList`'s sections ("Workspaces", "Personal"): the sections next to each other that name the same
  * group (`RichSection.group`, by `key`) go under one header, `title` with `icon` before it, which opens and closes them
  * all and says how many they hold between them. */
case class RichGroup(key: String, title: String, icon: Option[MaterialIcon] = None)
