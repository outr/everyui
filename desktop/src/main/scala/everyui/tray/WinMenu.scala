package everyui.tray

import everyui.menu.MenuItem

/** A tray icon's menu as Windows is to show it, taken from the app's entries on the app's thread (their ticks read
  * there), to be built on the window's (`WinTrayIcon`): each entry's words (an ampersand doubled, where Windows would
  * take it for a mnemonic's mark), its `AppendMenuW` flags (a separator; greyed where it cannot be chosen; checked
  * where it is ticked) and its submenu's entries, numbered from 1 in order as `chosen` knows them. */
private[tray] case class WinMenu(entries: List[WinMenu.Entry], items: Vector[MenuItem]) {
  /** What choosing the entry numbered `id` does, where it does anything. */
  def chosen(id: Int): Option[() => Unit] =
    items.lift(id - 1).filter(m => m.choosable && m.submenu.isEmpty).map(_.action)
}

private[tray] object WinMenu {
  case class Entry(id: Int, label: String, flags: Int, submenu: List[Entry])

  def of(items: List[MenuItem]): WinMenu = {
    var flat = Vector.empty[MenuItem]
    def entries(list: List[MenuItem]): List[Entry] = list.map { m =>
      flat = flat :+ m
      val id = flat.size
      if (m.separator) Entry(id, "", Win32.MF_SEPARATOR, Nil)
      else {
        val flags = Win32.MF_STRING |
          (if (m.submenu.nonEmpty) Win32.MF_POPUP else 0) |
          (if (!m.choosable) Win32.MF_GRAYED else 0) |
          (if ((m.checked.nonEmpty || m.on.nonEmpty) && m.ticked) Win32.MF_CHECKED else 0)
        Entry(id, m.label.replace("&", "&&"), flags, entries(m.submenu))
      }
    }
    val made = entries(items)
    WinMenu(made, flat)
  }
}
